package com.platform.controlplane.connectors.redis;

import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.TopologyInfo;
import com.platform.controlplane.model.TopologyInfo.NodeInfo;
import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.state.SystemState;
import com.platform.controlplane.state.SystemStateContext;
import com.platform.controlplane.state.SystemStateMachine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.models.partitions.ClusterPartitionParser;
import io.lettuce.core.cluster.models.partitions.Partitions;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis connector for Cluster setup with slot-based sharding.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "controlplane.redis.topology", havingValue = "cluster")
public class ClusterRedisConnector implements RedisConnector {
    
    private final MetricsRegistry metricsRegistry;
    private final SystemStateMachine stateMachine;
    private final AtomicReference<ConnectionStatus> currentStatus;
    private final AtomicReference<TopologyInfo> currentTopology;
    private final AtomicReference<RedisClusterClient> clusterClient;
    private final AtomicReference<StatefulRedisClusterConnection<String, String>> connection;
    
    @Value("${controlplane.redis.cluster.nodes:localhost:7000,localhost:7001,localhost:7002}")
    private String clusterNodes;
    
    @Value("${spring.data.redis.password:}")
    private String password;
    
    @Value("${spring.data.redis.timeout:5000ms}")
    private Duration timeout;
    
    public ClusterRedisConnector(MetricsRegistry metricsRegistry, SystemStateMachine stateMachine) {
        this.metricsRegistry = metricsRegistry;
        this.stateMachine = stateMachine;
        this.currentStatus = new AtomicReference<>(ConnectionStatus.unknown("redis"));
        this.currentTopology = new AtomicReference<>(TopologyInfo.unknown("redis"));
        this.clusterClient = new AtomicReference<>();
        this.connection = new AtomicReference<>();
        stateMachine.initialize("redis");
    }
    
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "connectFallback")
    @Retry(name = "redis")
    public boolean connect() {
        log.info("Attempting to connect to Redis Cluster");
        long startTime = System.currentTimeMillis();
        stateMachine.transition("redis", SystemState.CONNECTING, "Initiating cluster connection");
        
        try {
            List<RedisURI> nodeUris = new ArrayList<>();
            
            for (String node : clusterNodes.split(",")) {
                String[] parts = node.trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
                
                RedisURI.Builder uriBuilder = RedisURI.builder()
                    .withHost(host)
                    .withPort(port)
                    .withTimeout(timeout);
                
                if (password != null && !password.isEmpty()) {
                    uriBuilder.withPassword(password.toCharArray());
                }
                
                nodeUris.add(uriBuilder.build());
            }
            
            RedisClusterClient client = RedisClusterClient.create(nodeUris);
            
            // Configure cluster options with topology refresh
            ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofSeconds(30))
                .enableAllAdaptiveRefreshTriggers()
                .build();
            
            ClusterClientOptions options = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .autoReconnect(true)
                .build();
            
            client.setOptions(options);
            clusterClient.set(client);
            
            StatefulRedisClusterConnection<String, String> conn = client.connect();
            connection.set(conn);
            
            // Verify with PING
            String response = conn.sync().ping();
            if (!"PONG".equals(response)) {
                throw new RuntimeException("Unexpected PING response: " + response);
            }
            
            long latency = System.currentTimeMillis() - startTime;
            stateMachine.transition("redis", SystemState.CONNECTED, "Connected to cluster");
            stateMachine.updateLatency("redis", latency);
            
            currentStatus.set(ConnectionStatus.up("redis", latency, nodeUris.size(), 1000));
            metricsRegistry.recordConnectionSuccess("redis");
            metricsRegistry.recordLatency("redis", "connect", latency);
            
            log.info("Successfully connected to Redis Cluster in {}ms", latency);
            detectRole();
            return true;
            
        } catch (Exception e) {
            log.error("Failed to connect to Redis Cluster: {}", e.getMessage());
            stateMachine.transition("redis", SystemState.RETRYING, "Connection failed: " + e.getMessage());
            currentStatus.set(ConnectionStatus.down("redis", e.getMessage()));
            metricsRegistry.recordConnectionFailure("redis");
            return false;
        }
    }
    
    @SuppressWarnings("unused")
    private boolean connectFallback(Exception e) {
        log.warn("Redis Cluster connection circuit breaker triggered: {}", e.getMessage());
        stateMachine.transition("redis", SystemState.CIRCUIT_OPEN, "Circuit breaker opened");
        currentStatus.set(ConnectionStatus.down("redis", "Circuit breaker open: " + e.getMessage()));
        return false;
    }
    
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "healthCheckFallback")
    public ConnectionStatus healthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            StatefulRedisClusterConnection<String, String> conn = connection.get();
            if (conn == null || !conn.isOpen()) {
                return ConnectionStatus.down("redis", "Connection not established");
            }
            
            String response = conn.sync().ping();
            if (!"PONG".equals(response)) {
                return ConnectionStatus.down("redis", "Unexpected PING response");
            }
            
            long latency = System.currentTimeMillis() - startTime;
            
            // Get cluster info
            TopologyInfo topology = detectRole();
            int healthyNodes = (int) topology.nodes().stream()
                .filter(NodeInfo::isHealthy)
                .count();
            int totalNodes = topology.getTotalNodeCount();
            
            ConnectionStatus status;
            if (healthyNodes == totalNodes) {
                status = ConnectionStatus.up("redis", latency, healthyNodes, 1000);
            } else if (healthyNodes > 0) {
                status = ConnectionStatus.degraded("redis", latency, 
                    String.format("%d/%d nodes healthy", healthyNodes, totalNodes));
            } else {
                status = ConnectionStatus.down("redis", "No healthy nodes");
            }
            
            currentStatus.set(status);
            metricsRegistry.recordLatency("redis", "health_check", latency);
            return status;
            
        } catch (Exception e) {
            log.error("Redis Cluster health check failed: {}", e.getMessage());
            SystemStateContext context = stateMachine.getContext("redis");
            if (context.currentState() == SystemState.CONNECTED) {
                stateMachine.transition("redis", SystemState.DEGRADED, "Health check failed");
            }
            ConnectionStatus status = ConnectionStatus.down("redis", e.getMessage());
            currentStatus.set(status);
            metricsRegistry.recordConnectionFailure("redis");
            return status;
        }
    }
    
    @SuppressWarnings("unused")
    private ConnectionStatus healthCheckFallback(Exception e) {
        return ConnectionStatus.down("redis", "Circuit breaker open");
    }
    
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "detectRoleFallback")
    public TopologyInfo detectRole() {
        log.debug("Detecting Redis Cluster topology");
        List<NodeInfo> nodes = new ArrayList<>();
        
        try {
            StatefulRedisClusterConnection<String, String> conn = connection.get();
            if (conn == null || !conn.isOpen()) {
                return TopologyInfo.unknown("redis");
            }
            
            RedisAdvancedClusterCommands<String, String> commands = conn.sync();
            
            // Get CLUSTER NODES info
            String clusterNodesInfo = commands.clusterNodes();
            Partitions partitions = ClusterPartitionParser.parse(clusterNodesInfo);
            
            for (RedisClusterNode node : partitions) {
                String nodeId = node.getNodeId();
                String host = node.getUri().getHost();
                int port = node.getUri().getPort();
                
                NodeInfo.NodeRole role;
                if (node.is(RedisClusterNode.NodeFlag.MASTER)) {
                    role = NodeInfo.NodeRole.MASTER;
                } else if (node.is(RedisClusterNode.NodeFlag.SLAVE)) {
                    role = NodeInfo.NodeRole.SLAVE;
                } else {
                    role = NodeInfo.NodeRole.UNKNOWN;
                }
                
                boolean isHealthy = !node.is(RedisClusterNode.NodeFlag.FAIL) && 
                                   !node.is(RedisClusterNode.NodeFlag.EVENTUAL_FAIL);
                
                nodes.add(new NodeInfo(
                    nodeId.substring(0, Math.min(8, nodeId.length())),
                    host,
                    port,
                    role,
                    isHealthy,
                    0
                ));
            }
            
            // Get primary master (first master found)
            String primaryNode = nodes.stream()
                .filter(n -> n.role() == NodeInfo.NodeRole.MASTER && n.isHealthy())
                .findFirst()
                .map(n -> n.host() + ":" + n.port())
                .orElse(null);
            
            int masterCount = (int) nodes.stream()
                .filter(n -> n.role() == NodeInfo.NodeRole.MASTER)
                .count();
            int slotsCovered = partitions.stream()
                .filter(n -> n.is(RedisClusterNode.NodeFlag.MASTER))
                .mapToInt(n -> n.getSlots().size())
                .sum();
            
            TopologyInfo topology = new TopologyInfo(
                "redis",
                TopologyInfo.TopologyType.REDIS_CLUSTER,
                nodes,
                primaryNode,
                Instant.now(),
                String.format("Cluster with %d masters, %d slots covered", masterCount, slotsCovered)
            );
            
            currentTopology.set(topology);
            log.info("Detected Redis Cluster topology: {} nodes, {} masters", nodes.size(), masterCount);
            return topology;
            
        } catch (Exception e) {
            log.error("Failed to detect Redis Cluster topology: {}", e.getMessage());
            return currentTopology.get();
        }
    }
    
    @SuppressWarnings("unused")
    private TopologyInfo detectRoleFallback(Exception e) {
        return currentTopology.get();
    }
    
    @Override
    @Retry(name = "redis")
    public boolean reconnect() {
        log.info("Attempting Redis Cluster reconnection");
        disconnect();
        return connect();
    }
    
    @Override
    public ConnectionStatus getConnectionStatus() {
        return currentStatus.get();
    }
    
    @Override
    public TopologyInfo.TopologyType getSupportedTopology() {
        return TopologyInfo.TopologyType.REDIS_CLUSTER;
    }
    
    @Override
    @PreDestroy
    public void disconnect() {
        log.info("Disconnecting from Redis Cluster");
        stateMachine.transition("redis", SystemState.DISCONNECTED, "Manual disconnect");
        
        StatefulRedisClusterConnection<String, String> conn = connection.getAndSet(null);
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {}
        }
        
        RedisClusterClient client = clusterClient.getAndSet(null);
        if (client != null) {
            try {
                client.shutdown();
            } catch (Exception ignored) {}
        }
        
        currentStatus.set(ConnectionStatus.unknown("redis"));
    }
    
    @Override
    public boolean isConnected() {
        StatefulRedisClusterConnection<String, String> conn = connection.get();
        return conn != null && conn.isOpen() && 
               (currentStatus.get().isHealthy() || 
                currentStatus.get().status() == ConnectionStatus.Status.DEGRADED);
    }
    
    @Override
    public boolean ping() {
        try {
            StatefulRedisClusterConnection<String, String> conn = connection.get();
            if (conn == null || !conn.isOpen()) {
                return false;
            }
            return "PONG".equals(conn.sync().ping());
        } catch (Exception e) {
            log.debug("PING failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String get(String key) {
        StatefulRedisClusterConnection<String, String> conn = connection.get();
        if (conn == null) {
            throw new IllegalStateException("Not connected to Redis");
        }
        return conn.sync().get(key);
    }
    
    @Override
    public void set(String key, String value) {
        StatefulRedisClusterConnection<String, String> conn = connection.get();
        if (conn == null) {
            throw new IllegalStateException("Not connected to Redis");
        }
        conn.sync().set(key, value);
    }
}
