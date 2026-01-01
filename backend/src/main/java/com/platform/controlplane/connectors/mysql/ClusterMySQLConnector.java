package com.platform.controlplane.connectors.mysql;

import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.TopologyInfo;
import com.platform.controlplane.model.TopologyInfo.NodeInfo;
import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.state.SystemState;
import com.platform.controlplane.state.SystemStateContext;
import com.platform.controlplane.state.SystemStateMachine;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MySQL connector for Cluster / Group Replication setup.
 * Supports multi-node clusters with automatic node discovery and failover.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "controlplane.mysql.topology", havingValue = "cluster")
public class ClusterMySQLConnector implements MySQLConnector {
    
    private final MetricsRegistry metricsRegistry;
    private final SystemStateMachine stateMachine;
    private final AtomicReference<ConnectionStatus> currentStatus;
    private final AtomicReference<TopologyInfo> currentTopology;
    private final Map<String, HikariDataSource> nodeDataSources;
    private final AtomicReference<String> currentPrimary;
    
    @Value("${controlplane.mysql.cluster.nodes:localhost:3306,localhost:3307,localhost:3308}")
    private String clusterNodes;
    
    @Value("${spring.datasource.username:root}")
    private String username;
    
    @Value("${spring.datasource.password:password}")
    private String password;
    
    @Value("${controlplane.mysql.cluster.database:controlplane}")
    private String database;
    
    public ClusterMySQLConnector(MetricsRegistry metricsRegistry, SystemStateMachine stateMachine) {
        this.metricsRegistry = metricsRegistry;
        this.stateMachine = stateMachine;
        this.currentStatus = new AtomicReference<>(ConnectionStatus.unknown("mysql"));
        this.currentTopology = new AtomicReference<>(TopologyInfo.unknown("mysql"));
        this.nodeDataSources = new ConcurrentHashMap<>();
        this.currentPrimary = new AtomicReference<>();
        stateMachine.initialize("mysql");
    }
    
    @Override
    @CircuitBreaker(name = "mysql", fallbackMethod = "connectFallback")
    @Retry(name = "mysql")
    public boolean connect() {
        log.info("Attempting to connect to MySQL Cluster");
        long startTime = System.currentTimeMillis();
        stateMachine.transition("mysql", SystemState.CONNECTING, "Initiating cluster connection");
        
        try {
            String[] nodes = clusterNodes.split(",");
            int connectedNodes = 0;
            
            for (String node : nodes) {
                String[] parts = node.trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 3306;
                
                try {
                    HikariDataSource ds = createDataSource(host, port);
                    nodeDataSources.put(node.trim(), ds);
                    
                    // Verify connection
                    try (Connection conn = ds.getConnection()) {
                        if (conn.isValid(5)) {
                            connectedNodes++;
                            
                            // Check if this is primary
                            if (isPrimaryNode(conn)) {
                                currentPrimary.set(node.trim());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to connect to cluster node {}: {}", node, e.getMessage());
                }
            }
            
            if (connectedNodes == 0) {
                throw new RuntimeException("Failed to connect to any cluster node");
            }
            
            long latency = System.currentTimeMillis() - startTime;
            stateMachine.transition("mysql", SystemState.CONNECTED, "Connected to cluster");
            stateMachine.updateLatency("mysql", latency);
            
            currentStatus.set(ConnectionStatus.up("mysql", latency, connectedNodes, nodes.length * 20));
            metricsRegistry.recordConnectionSuccess("mysql");
            metricsRegistry.recordLatency("mysql", "connect", latency);
            
            log.info("Connected to MySQL Cluster: {}/{} nodes in {}ms", 
                connectedNodes, nodes.length, latency);
            detectTopology();
            return true;
            
        } catch (Exception e) {
            log.error("Failed to connect to MySQL Cluster: {}", e.getMessage());
            stateMachine.transition("mysql", SystemState.RETRYING, "Connection failed: " + e.getMessage());
            currentStatus.set(ConnectionStatus.down("mysql", e.getMessage()));
            metricsRegistry.recordConnectionFailure("mysql");
            return false;
        }
    }
    
    private HikariDataSource createDataSource(String host, int port) {
        String url = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
        
        HikariConfig config = new HikariConfig();
        config.setPoolName("MySQL-Cluster-" + host + "-" + port);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);
        config.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(config);
    }
    
    private boolean isPrimaryNode(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check for Group Replication primary
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT MEMBER_ROLE FROM performance_schema.replication_group_members " +
                    "WHERE MEMBER_ID = @@server_uuid")) {
                if (rs.next()) {
                    return "PRIMARY".equals(rs.getString(1));
                }
            }
            
            // Fallback to read_only check
            try (ResultSet rs = stmt.executeQuery("SELECT @@read_only, @@super_read_only")) {
                if (rs.next()) {
                    return rs.getInt(1) == 0 && rs.getInt(2) == 0;
                }
            }
        } catch (Exception e) {
            log.debug("Error detecting primary status: {}", e.getMessage());
        }
        return false;
    }
    
    @SuppressWarnings("unused")
    private boolean connectFallback(Exception e) {
        log.warn("MySQL Cluster connection circuit breaker triggered: {}", e.getMessage());
        stateMachine.transition("mysql", SystemState.CIRCUIT_OPEN, "Circuit breaker opened");
        currentStatus.set(ConnectionStatus.down("mysql", "Circuit breaker open: " + e.getMessage()));
        return false;
    }
    
    @Override
    @CircuitBreaker(name = "mysql", fallbackMethod = "healthCheckFallback")
    public ConnectionStatus healthCheck() {
        long startTime = System.currentTimeMillis();
        int healthyNodes = 0;
        int totalNodes = nodeDataSources.size();
        List<String> errors = new ArrayList<>();
        
        for (Map.Entry<String, HikariDataSource> entry : nodeDataSources.entrySet()) {
            try (Connection conn = entry.getValue().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    healthyNodes++;
                }
            } catch (Exception e) {
                errors.add(entry.getKey() + ": " + e.getMessage());
            }
        }
        
        long latency = System.currentTimeMillis() - startTime;
        ConnectionStatus status;
        
        if (healthyNodes == totalNodes) {
            status = ConnectionStatus.up("mysql", latency, healthyNodes, totalNodes * 10);
        } else if (healthyNodes > 0) {
            status = ConnectionStatus.degraded("mysql", latency, 
                String.format("%d/%d nodes healthy. Errors: %s", 
                    healthyNodes, totalNodes, String.join("; ", errors)));
        } else {
            status = ConnectionStatus.down("mysql", "All nodes down: " + String.join("; ", errors));
        }
        
        currentStatus.set(status);
        metricsRegistry.recordLatency("mysql", "health_check", latency);
        return status;
    }
    
    @SuppressWarnings("unused")
    private ConnectionStatus healthCheckFallback(Exception e) {
        return ConnectionStatus.down("mysql", "Circuit breaker open");
    }
    
    @Override
    @CircuitBreaker(name = "mysql", fallbackMethod = "detectTopologyFallback")
    public TopologyInfo detectTopology() {
        log.debug("Detecting MySQL Cluster topology");
        List<NodeInfo> nodes = new ArrayList<>();
        String primaryNode = null;
        
        for (Map.Entry<String, HikariDataSource> entry : nodeDataSources.entrySet()) {
            String nodeAddress = entry.getKey();
            String[] parts = nodeAddress.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 3306;
            
            try (Connection conn = entry.getValue().getConnection();
                 Statement stmt = conn.createStatement()) {
                
                NodeInfo.NodeRole role = NodeInfo.NodeRole.UNKNOWN;
                boolean isHealthy = true;
                long latencyMs = 0;
                
                // Get group replication member info
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT MEMBER_ROLE, MEMBER_STATE FROM performance_schema.replication_group_members " +
                        "WHERE MEMBER_ID = @@server_uuid")) {
                    if (rs.next()) {
                        String memberRole = rs.getString(1);
                        String memberState = rs.getString(2);
                        
                        role = "PRIMARY".equals(memberRole) 
                            ? NodeInfo.NodeRole.PRIMARY 
                            : NodeInfo.NodeRole.REPLICA;
                        isHealthy = "ONLINE".equals(memberState);
                        
                        if (role == NodeInfo.NodeRole.PRIMARY) {
                            primaryNode = nodeAddress;
                        }
                    }
                } catch (Exception ignored) {
                    // Not group replication, use read_only check
                    if (isPrimaryNode(conn)) {
                        role = NodeInfo.NodeRole.PRIMARY;
                        primaryNode = nodeAddress;
                    } else {
                        role = NodeInfo.NodeRole.REPLICA;
                    }
                }
                
                nodes.add(new NodeInfo(nodeAddress, host, port, role, isHealthy, latencyMs));
                
            } catch (Exception e) {
                log.warn("Failed to get topology for node {}: {}", nodeAddress, e.getMessage());
                nodes.add(new NodeInfo(nodeAddress, host, port, 
                    NodeInfo.NodeRole.UNKNOWN, false, -1));
            }
        }
        
        currentPrimary.set(primaryNode);
        
        TopologyInfo topology = new TopologyInfo(
            "mysql",
            TopologyInfo.TopologyType.MYSQL_CLUSTER,
            nodes,
            primaryNode,
            Instant.now(),
            String.format("MySQL Cluster: %d nodes, %d healthy", 
                nodes.size(), (int) nodes.stream().filter(NodeInfo::isHealthy).count())
        );
        
        currentTopology.set(topology);
        log.info("Detected MySQL Cluster topology: {} nodes, primary: {}", nodes.size(), primaryNode);
        return topology;
    }
    
    @SuppressWarnings("unused")
    private TopologyInfo detectTopologyFallback(Exception e) {
        return currentTopology.get();
    }
    
    @Override
    @Retry(name = "mysql")
    public boolean reconnect() {
        log.info("Attempting MySQL Cluster reconnection");
        disconnect();
        return connect();
    }
    
    @Override
    public ConnectionStatus getConnectionStatus() {
        return currentStatus.get();
    }
    
    @Override
    public TopologyInfo.TopologyType getSupportedTopology() {
        return TopologyInfo.TopologyType.MYSQL_CLUSTER;
    }
    
    @Override
    public void disconnect() {
        log.info("Disconnecting from MySQL Cluster");
        stateMachine.transition("mysql", SystemState.DISCONNECTED, "Manual disconnect");
        
        for (HikariDataSource ds : nodeDataSources.values()) {
            try {
                ds.close();
            } catch (Exception ignored) {}
        }
        nodeDataSources.clear();
        currentPrimary.set(null);
        currentStatus.set(ConnectionStatus.unknown("mysql"));
    }
    
    @Override
    public boolean isConnected() {
        return currentStatus.get().isHealthy() || 
               currentStatus.get().status() == ConnectionStatus.Status.DEGRADED;
    }
    
    @Override
    public boolean validateConnection() {
        return nodeDataSources.values().stream()
            .anyMatch(ds -> {
                try (Connection conn = ds.getConnection()) {
                    return conn.isValid(5);
                } catch (Exception e) {
                    return false;
                }
            });
    }
    
    /**
     * Get connection to primary node for write operations.
     */
    public Connection getPrimaryConnection() throws Exception {
        String primary = currentPrimary.get();
        if (primary != null) {
            HikariDataSource ds = nodeDataSources.get(primary);
            if (ds != null) {
                return ds.getConnection();
            }
        }
        
        // Refresh topology and try again
        detectTopology();
        primary = currentPrimary.get();
        if (primary != null) {
            HikariDataSource ds = nodeDataSources.get(primary);
            if (ds != null) {
                return ds.getConnection();
            }
        }
        
        throw new RuntimeException("No primary node available");
    }
    
    /**
     * Get connection to any healthy node for read operations.
     */
    public Connection getReadConnection() throws Exception {
        for (HikariDataSource ds : nodeDataSources.values()) {
            try {
                Connection conn = ds.getConnection();
                if (conn.isValid(2)) {
                    return conn;
                }
            } catch (Exception ignored) {}
        }
        throw new RuntimeException("No healthy nodes available");
    }
}
