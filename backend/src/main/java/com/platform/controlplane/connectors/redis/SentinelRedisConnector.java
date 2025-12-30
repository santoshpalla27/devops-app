package com.platform.controlplane.connectors.redis;

import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.TopologyInfo;
import com.platform.controlplane.model.TopologyInfo.NodeInfo;
import com.platform.controlplane.observability.MetricsRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.sentinel.api.StatefulRedisSentinelConnection;
import io.lettuce.core.sentinel.api.sync.RedisSentinelCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis connector for Sentinel setup with automatic failover detection.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "controlplane.redis.topology", havingValue = "sentinel")
public class SentinelRedisConnector implements RedisConnector {
    
    private final MetricsRegistry metricsRegistry;
    private final AtomicReference<ConnectionStatus> currentStatus;
    private final AtomicReference<TopologyInfo> currentTopology;
    private final AtomicReference<RedisClient> redisClient;
    private final AtomicReference<StatefulRedisConnection<String, String>> masterConnection;
    private final AtomicReference<String> currentMaster;
    
    @Value("${controlplane.redis.sentinel.nodes:localhost:26379}")
    private String sentinelNodes;
    
    @Value("${controlplane.redis.sentinel.master-name:mymaster}")
    private String masterName;
    
    @Value("${spring.data.redis.password:}")
    private String password;
    
    @Value("${spring.data.redis.timeout:5000ms}")
    private Duration timeout;
    
    public SentinelRedisConnector(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
        this.currentStatus = new AtomicReference<>(ConnectionStatus.unknown("redis"));
        this.currentTopology = new AtomicReference<>(TopologyInfo.unknown("redis"));
        this.redisClient = new AtomicReference<>();
        this.masterConnection = new AtomicReference<>();
        this.currentMaster = new AtomicReference<>();
    }
    
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "connectFallback")
    @Retry(name = "redis")
    public boolean connect() {
        log.info("Attempting to connect to Redis via Sentinel");
        long startTime = System.currentTimeMillis();
        
        try {
            RedisURI.Builder uriBuilder = RedisURI.builder()
                .withSentinelMasterId(masterName)
                .withTimeout(timeout);
            
            // Add sentinel nodes
            String[] nodes = sentinelNodes.split(",");
            for (String node : nodes) {
                String[] parts = node.trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 26379;
                uriBuilder.withSentinel(host, port);
            }
            
            if (password != null && !password.isEmpty()) {
                uriBuilder.withPassword(password.toCharArray());
            }
            
            RedisClient client = RedisClient.create(uriBuilder.build());
            redisClient.set(client);
            
            StatefulRedisConnection<String, String> conn = client.connect();
            masterConnection.set(conn);
            
            // Verify with PING
            String response = conn.sync().ping();
            if (!"PONG".equals(response)) {
                throw new RuntimeException("Unexpected PING response: " + response);
            }
            
            long latency = System.currentTimeMillis() - startTime;
            currentStatus.set(ConnectionStatus.up("redis", latency, 1, 1000));
            metricsRegistry.recordConnectionSuccess("redis");
            metricsRegistry.recordLatency("redis", "connect", latency);
            
            log.info("Successfully connected to Redis via Sentinel in {}ms", latency);
            detectRole();
            return true;
            
        } catch (Exception e) {
            log.error("Failed to connect to Redis via Sentinel: {}", e.getMessage());
            currentStatus.set(ConnectionStatus.down("redis", e.getMessage()));
            metricsRegistry.recordConnectionFailure("redis");
            return false;
        }
    }
    
    @SuppressWarnings("unused")
    private boolean connectFallback(Exception e) {
        log.warn("Redis Sentinel connection circuit breaker triggered: {}", e.getMessage());
        currentStatus.set(ConnectionStatus.down("redis", "Circuit breaker open: " + e.getMessage()));
        return false;
    }
    
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "healthCheckFallback")
    public ConnectionStatus healthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            StatefulRedisConnection<String, String> conn = masterConnection.get();
            if (conn == null || !conn.isOpen()) {
                // Try to reconnect
                if (!reconnect()) {
                    return ConnectionStatus.down("redis", "Connection lost and reconnection failed");
                }
                conn = masterConnection.get();
            }
            
            String response = conn.sync().ping();
            if (!"PONG".equals(response)) {
                return ConnectionStatus.down("redis", "Unexpected PING response");
            }
            
            long latency = System.currentTimeMillis() - startTime;
            
            // Check if there was a failover
            TopologyInfo topology = detectRole();
            int healthyNodes = (int) topology.nodes().stream()
                .filter(NodeInfo::isHealthy)
                .count();
            
            ConnectionStatus status;
            if (healthyNodes == topology.getTotalNodeCount()) {
                status = ConnectionStatus.up("redis", latency, healthyNodes, 1000);
            } else {
                status = ConnectionStatus.degraded("redis", latency, 
                    String.format("%d/%d nodes healthy", healthyNodes, topology.getTotalNodeCount()));
            }
            
            currentStatus.set(status);
            metricsRegistry.recordLatency("redis", "health_check", latency);
            return status;
            
        } catch (Exception e) {
            log.error("Redis Sentinel health check failed: {}", e.getMessage());
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
        log.debug("Detecting Redis Sentinel topology");
        List<NodeInfo> nodes = new ArrayList<>();
        String masterAddress = null;
        
        try {
            // Connect to sentinel to get master info
            String[] sentinelHostPort = sentinelNodes.split(",")[0].trim().split(":");
            String sentinelHost = sentinelHostPort[0];
            int sentinelPort = sentinelHostPort.length > 1 ? Integer.parseInt(sentinelHostPort[1]) : 26379;
            
            RedisURI sentinelUri = RedisURI.builder()
                .withHost(sentinelHost)
                .withPort(sentinelPort)
                .withTimeout(timeout)
                .build();
            
            try (RedisClient sentinelClient = RedisClient.create(sentinelUri);
                 StatefulRedisSentinelConnection<String, String> sentinelConn = sentinelClient.connectSentinel()) {
                
                RedisSentinelCommands<String, String> sentinel = sentinelConn.sync();
                
                // Get master info
                Map<String, String> masterInfo = sentinel.master(masterName);
                if (masterInfo != null) {
                    String masterHost = masterInfo.get("ip");
                    int masterPort = Integer.parseInt(masterInfo.getOrDefault("port", "6379"));
                    masterAddress = masterHost + ":" + masterPort;
                    
                    nodes.add(new NodeInfo(
                        "master",
                        masterHost,
                        masterPort,
                        NodeInfo.NodeRole.MASTER,
                        "ok".equals(masterInfo.get("flags")) || 
                            (masterInfo.get("flags") != null && masterInfo.get("flags").contains("master")),
                        0
                    ));
                    
                    currentMaster.set(masterAddress);
                }
                
                // Get replicas
                List<Map<String, String>> replicas = sentinel.slaves(masterName);
                for (Map<String, String> replica : replicas) {
                    String replicaHost = replica.get("ip");
                    int replicaPort = Integer.parseInt(replica.getOrDefault("port", "6379"));
                    String flags = replica.getOrDefault("flags", "");
                    
                    nodes.add(new NodeInfo(
                        "replica-" + replicaHost,
                        replicaHost,
                        replicaPort,
                        NodeInfo.NodeRole.SLAVE,
                        !flags.contains("s_down") && !flags.contains("o_down"),
                        0
                    ));
                }
                
                // Add sentinel nodes
                for (String node : sentinelNodes.split(",")) {
                    String[] parts = node.trim().split(":");
                    nodes.add(new NodeInfo(
                        "sentinel-" + parts[0],
                        parts[0],
                        parts.length > 1 ? Integer.parseInt(parts[1]) : 26379,
                        NodeInfo.NodeRole.SENTINEL,
                        true,
                        0
                    ));
                }
            }
            
            TopologyInfo topology = new TopologyInfo(
                "redis",
                TopologyInfo.TopologyType.REDIS_SENTINEL,
                nodes,
                masterAddress,
                Instant.now(),
                String.format("Sentinel setup with %d nodes, master: %s", nodes.size(), masterAddress)
            );
            
            currentTopology.set(topology);
            log.info("Detected Redis Sentinel topology: {} nodes, master: {}", nodes.size(), masterAddress);
            return topology;
            
        } catch (Exception e) {
            log.error("Failed to detect Redis Sentinel topology: {}", e.getMessage());
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
        log.info("Attempting Redis Sentinel reconnection");
        disconnect();
        return connect();
    }
    
    @Override
    public ConnectionStatus getConnectionStatus() {
        return currentStatus.get();
    }
    
    @Override
    public TopologyInfo.TopologyType getSupportedTopology() {
        return TopologyInfo.TopologyType.REDIS_SENTINEL;
    }
    
    @Override
    @PreDestroy
    public void disconnect() {
        log.info("Disconnecting from Redis Sentinel");
        
        StatefulRedisConnection<String, String> conn = masterConnection.getAndSet(null);
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {}
        }
        
        RedisClient client = redisClient.getAndSet(null);
        if (client != null) {
            try {
                client.shutdown();
            } catch (Exception ignored) {}
        }
        
        currentMaster.set(null);
        currentStatus.set(ConnectionStatus.unknown("redis"));
    }
    
    @Override
    public boolean isConnected() {
        StatefulRedisConnection<String, String> conn = masterConnection.get();
        return conn != null && conn.isOpen() && currentStatus.get().isHealthy();
    }
    
    @Override
    public boolean ping() {
        try {
            StatefulRedisConnection<String, String> conn = masterConnection.get();
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
        StatefulRedisConnection<String, String> conn = masterConnection.get();
        if (conn == null) {
            throw new IllegalStateException("Not connected to Redis");
        }
        return conn.sync().get(key);
    }
    
    @Override
    public void set(String key, String value) {
        StatefulRedisConnection<String, String> conn = masterConnection.get();
        if (conn == null) {
            throw new IllegalStateException("Not connected to Redis");
        }
        conn.sync().set(key, value);
    }
    
    /**
     * Get current master address.
     */
    public String getCurrentMaster() {
        return currentMaster.get();
    }
}
