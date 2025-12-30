package com.platform.controlplane.connectors.mysql;

import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.TopologyInfo;
import com.platform.controlplane.model.TopologyInfo.NodeInfo;
import com.platform.controlplane.observability.MetricsRegistry;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * MySQL connector for Primary-Replica (Replication) setup.
 * Supports automatic failover detection and read/write splitting.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "controlplane.mysql.topology", havingValue = "replication")
public class ReplicationMySQLConnector implements MySQLConnector {
    
    private final MetricsRegistry metricsRegistry;
    private final AtomicReference<ConnectionStatus> currentStatus;
    private final AtomicReference<TopologyInfo> currentTopology;
    private final AtomicReference<HikariDataSource> primaryDataSource;
    private final AtomicReference<HikariDataSource> replicaDataSource;
    
    @Value("${controlplane.mysql.primary.url:jdbc:mysql://localhost:3306/controlplane}")
    private String primaryUrl;
    
    @Value("${controlplane.mysql.replica.url:jdbc:mysql://localhost:3307/controlplane}")
    private String replicaUrl;
    
    @Value("${spring.datasource.username:root}")
    private String username;
    
    @Value("${spring.datasource.password:password}")
    private String password;
    
    public ReplicationMySQLConnector(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
        this.currentStatus = new AtomicReference<>(ConnectionStatus.unknown("mysql"));
        this.currentTopology = new AtomicReference<>(TopologyInfo.unknown("mysql"));
        this.primaryDataSource = new AtomicReference<>();
        this.replicaDataSource = new AtomicReference<>();
    }
    
    @Override
    @CircuitBreaker(name = "mysql", fallbackMethod = "connectFallback")
    @Retry(name = "mysql")
    public boolean connect() {
        log.info("Attempting to connect to MySQL (replication mode)");
        long startTime = System.currentTimeMillis();
        
        try {
            // Connect to primary
            HikariDataSource primary = createDataSource("primary", primaryUrl);
            primaryDataSource.set(primary);
            
            // Connect to replica
            HikariDataSource replica = createDataSource("replica", replicaUrl);
            replicaDataSource.set(replica);
            
            // Validate connections
            try (Connection conn = primary.getConnection()) {
                if (!conn.isValid(5)) {
                    throw new RuntimeException("Primary connection invalid");
                }
            }
            
            long latency = System.currentTimeMillis() - startTime;
            currentStatus.set(ConnectionStatus.up("mysql", latency, 2, 40));
            metricsRegistry.recordConnectionSuccess("mysql");
            metricsRegistry.recordLatency("mysql", "connect", latency);
            
            log.info("Successfully connected to MySQL primary and replica in {}ms", latency);
            detectTopology();
            return true;
            
        } catch (Exception e) {
            log.error("Failed to connect to MySQL: {}", e.getMessage());
            currentStatus.set(ConnectionStatus.down("mysql", e.getMessage()));
            metricsRegistry.recordConnectionFailure("mysql");
            return false;
        }
    }
    
    private HikariDataSource createDataSource(String poolName, String url) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("MySQL-" + poolName);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(20);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);
        config.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(config);
    }
    
    @SuppressWarnings("unused")
    private boolean connectFallback(Exception e) {
        log.warn("MySQL connection circuit breaker triggered: {}", e.getMessage());
        currentStatus.set(ConnectionStatus.down("mysql", "Circuit breaker open: " + e.getMessage()));
        return false;
    }
    
    @Override
    @CircuitBreaker(name = "mysql", fallbackMethod = "healthCheckFallback")
    public ConnectionStatus healthCheck() {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        int healthyNodes = 0;
        
        // Check primary
        HikariDataSource primary = primaryDataSource.get();
        if (primary != null) {
            try (Connection conn = primary.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    healthyNodes++;
                }
            } catch (Exception e) {
                errors.add("Primary: " + e.getMessage());
            }
        }
        
        // Check replica
        HikariDataSource replica = replicaDataSource.get();
        if (replica != null) {
            try (Connection conn = replica.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    healthyNodes++;
                }
            } catch (Exception e) {
                errors.add("Replica: " + e.getMessage());
            }
        }
        
        long latency = System.currentTimeMillis() - startTime;
        ConnectionStatus status;
        
        if (healthyNodes == 2) {
            status = ConnectionStatus.up("mysql", latency, healthyNodes, 40);
        } else if (healthyNodes == 1) {
            status = ConnectionStatus.degraded("mysql", latency, 
                "Partial availability: " + String.join(", ", errors));
        } else {
            status = ConnectionStatus.down("mysql", String.join(", ", errors));
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
        log.debug("Detecting MySQL replication topology");
        List<NodeInfo> nodes = new ArrayList<>();
        String primaryHost = null;
        
        // Detect primary
        HikariDataSource primary = primaryDataSource.get();
        if (primary != null) {
            try (Connection conn = primary.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                String host = "primary";
                int port = 3306;
                boolean isReadOnly = false;
                
                try (ResultSet rs = stmt.executeQuery("SELECT @@hostname, @@port, @@read_only")) {
                    if (rs.next()) {
                        host = rs.getString(1);
                        port = rs.getInt(2);
                        isReadOnly = rs.getInt(3) == 1;
                    }
                }
                
                NodeInfo.NodeRole role = isReadOnly ? NodeInfo.NodeRole.REPLICA : NodeInfo.NodeRole.PRIMARY;
                nodes.add(new NodeInfo("primary", host, port, role, true, 0));
                
                if (!isReadOnly) {
                    primaryHost = host + ":" + port;
                }
                
            } catch (Exception e) {
                log.warn("Failed to get primary topology: {}", e.getMessage());
                nodes.add(new NodeInfo("primary", "unknown", 3306, 
                    NodeInfo.NodeRole.UNKNOWN, false, -1));
            }
        }
        
        // Detect replica
        HikariDataSource replica = replicaDataSource.get();
        if (replica != null) {
            try (Connection conn = replica.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                String host = "replica";
                int port = 3307;
                
                try (ResultSet rs = stmt.executeQuery("SELECT @@hostname, @@port")) {
                    if (rs.next()) {
                        host = rs.getString(1);
                        port = rs.getInt(2);
                    }
                }
                
                // Check replication status
                long secondsBehind = 0;
                try (ResultSet rs = stmt.executeQuery("SHOW SLAVE STATUS")) {
                    if (rs.next()) {
                        secondsBehind = rs.getLong("Seconds_Behind_Master");
                    }
                }
                
                nodes.add(new NodeInfo("replica", host, port, 
                    NodeInfo.NodeRole.REPLICA, true, secondsBehind));
                
            } catch (Exception e) {
                log.warn("Failed to get replica topology: {}", e.getMessage());
                nodes.add(new NodeInfo("replica", "unknown", 3307, 
                    NodeInfo.NodeRole.REPLICA, false, -1));
            }
        }
        
        TopologyInfo topology = new TopologyInfo(
            "mysql",
            TopologyInfo.TopologyType.MYSQL_REPLICATION,
            nodes,
            primaryHost,
            Instant.now(),
            String.format("Primary-Replica setup: %d nodes", nodes.size())
        );
        
        currentTopology.set(topology);
        log.info("Detected MySQL replication topology with {} nodes", nodes.size());
        return topology;
    }
    
    @SuppressWarnings("unused")
    private TopologyInfo detectTopologyFallback(Exception e) {
        return currentTopology.get();
    }
    
    @Override
    @Retry(name = "mysql")
    public boolean reconnect() {
        log.info("Attempting MySQL reconnection");
        disconnect();
        return connect();
    }
    
    @Override
    public ConnectionStatus getConnectionStatus() {
        return currentStatus.get();
    }
    
    @Override
    public TopologyInfo.TopologyType getSupportedTopology() {
        return TopologyInfo.TopologyType.MYSQL_REPLICATION;
    }
    
    @Override
    public void disconnect() {
        log.info("Disconnecting from MySQL");
        
        HikariDataSource primary = primaryDataSource.getAndSet(null);
        if (primary != null) {
            primary.close();
        }
        
        HikariDataSource replica = replicaDataSource.getAndSet(null);
        if (replica != null) {
            replica.close();
        }
        
        currentStatus.set(ConnectionStatus.unknown("mysql"));
    }
    
    @Override
    public boolean isConnected() {
        return currentStatus.get().isHealthy();
    }
    
    @Override
    public boolean validateConnection() {
        HikariDataSource primary = primaryDataSource.get();
        if (primary == null) return false;
        
        try (Connection conn = primary.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.debug("Connection validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get connection for read operations (uses replica if available).
     */
    public Connection getReadConnection() throws Exception {
        HikariDataSource replica = replicaDataSource.get();
        if (replica != null) {
            return replica.getConnection();
        }
        
        HikariDataSource primary = primaryDataSource.get();
        if (primary != null) {
            return primary.getConnection();
        }
        
        throw new RuntimeException("No available MySQL connections");
    }
    
    /**
     * Get connection for write operations (always uses primary).
     */
    public Connection getWriteConnection() throws Exception {
        HikariDataSource primary = primaryDataSource.get();
        if (primary != null) {
            return primary.getConnection();
        }
        throw new RuntimeException("Primary MySQL connection not available");
    }
}
