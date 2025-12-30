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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis connector for standalone (single-node) setup.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "controlplane.redis.topology", havingValue = "standalone", matchIfMissing = true)
public class StandaloneRedisConnector implements RedisConnector {
    
    private final MetricsRegistry metricsRegistry;
    private final AtomicReference<ConnectionStatus> currentStatus;
    private final AtomicReference<TopologyInfo> currentTopology;
    private final AtomicReference<RedisClient> redisClient;
    private final AtomicReference<StatefulRedisConnection<String, String>> connection;
    
    @Value("${spring.data.redis.host:localhost}")
    private String host;
    
    @Value("${spring.data.redis.port:6379}")
    private int port;
    
    @Value("${spring.data.redis.password:}")
    private String password;
    
    @Value("${spring.data.redis.timeout:5000ms}")
    private Duration timeout;
    
    public StandaloneRedisConnector(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
        this.currentStatus = new AtomicReference<>(ConnectionStatus.unknown("redis"));
        this.currentTopology = new AtomicReference<>(TopologyInfo.unknown("redis"));
        this.redisClient = new AtomicReference<>();
        this.connection = new AtomicReference<>();
    }
    
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "connectFallback")
    @Retry(name = "redis")
    public boolean connect() {
        log.info("Attempting to connect to Redis (standalone mode) at {}:{}", host, port);
        long startTime = System.currentTimeMillis();
        
        try {
            RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(timeout);
            
            if (password != null && !password.isEmpty()) {
                uriBuilder.withPassword(password.toCharArray());
            }
            
            RedisClient client = RedisClient.create(uriBuilder.build());
            redisClient.set(client);
            
            StatefulRedisConnection<String, String> conn = client.connect();
            connection.set(conn);
            
            // Verify with PING
            String response = conn.sync().ping();
            if (!"PONG".equals(response)) {
                throw new RuntimeException("Unexpected PING response: " + response);
            }
            
            long latency = System.currentTimeMillis() - startTime;
            currentStatus.set(ConnectionStatus.up("redis", latency, 1, 1000));
            metricsRegistry.recordConnectionSuccess("redis");
            metricsRegistry.recordLatency("redis", "connect", latency);
            
            log.info("Successfully connected to Redis in {}ms", latency);
            detectRole();
            return true;
            
        } catch (Exception e) {
            log.error("Failed to connect to Redis: {}", e.getMessage());
            currentStatus.set(ConnectionStatus.down("redis", e.getMessage()));
            metricsRegistry.recordConnectionFailure("redis");
            return false;
        }
    }
    
    @SuppressWarnings("unused")
    private boolean connectFallback(Exception e) {
        log.warn("Redis connection circuit breaker triggered: {}", e.getMessage());
        currentStatus.set(ConnectionStatus.down("redis", "Circuit breaker open: " + e.getMessage()));
        return false;
    }
    
    @Override
    @CircuitBreaker(name = "redis", fallbackMethod = "healthCheckFallback")
    public ConnectionStatus healthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            StatefulRedisConnection<String, String> conn = connection.get();
            if (conn == null || !conn.isOpen()) {
                return ConnectionStatus.down("redis", "Connection not established");
            }
            
            String response = conn.sync().ping();
            if (!"PONG".equals(response)) {
                return ConnectionStatus.down("redis", "Unexpected PING response");
            }
            
            long latency = System.currentTimeMillis() - startTime;
            
            // Get memory info for additional metrics
            String info = conn.sync().info("memory");
            
            ConnectionStatus status = ConnectionStatus.up("redis", latency, 1, 1000);
            currentStatus.set(status);
            metricsRegistry.recordLatency("redis", "health_check", latency);
            return status;
            
        } catch (Exception e) {
            log.error("Redis health check failed: {}", e.getMessage());
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
        log.debug("Detecting Redis role");
        
        try {
            StatefulRedisConnection<String, String> conn = connection.get();
            if (conn == null || !conn.isOpen()) {
                return TopologyInfo.unknown("redis");
            }
            
            RedisCommands<String, String> commands = conn.sync();
            
            // Execute ROLE command
            List<Object> roleInfo = commands.role();
            String role = roleInfo.isEmpty() ? "unknown" : roleInfo.get(0).toString();
            
            NodeInfo.NodeRole nodeRole = "master".equalsIgnoreCase(role) 
                ? NodeInfo.NodeRole.MASTER 
                : NodeInfo.NodeRole.SLAVE;
            
            NodeInfo node = new NodeInfo(
                "main",
                host,
                port,
                nodeRole,
                true,
                currentStatus.get().latencyMs()
            );
            
            TopologyInfo topology = new TopologyInfo(
                "redis",
                TopologyInfo.TopologyType.REDIS_STANDALONE,
                List.of(node),
                host + ":" + port,
                Instant.now(),
                "Redis " + role + " mode"
            );
            
            currentTopology.set(topology);
            log.info("Detected Redis role: {}", role);
            return topology;
            
        } catch (Exception e) {
            log.error("Failed to detect Redis role: {}", e.getMessage());
            return TopologyInfo.unknown("redis");
        }
    }
    
    @SuppressWarnings("unused")
    private TopologyInfo detectRoleFallback(Exception e) {
        return currentTopology.get();
    }
    
    @Override
    @Retry(name = "redis")
    public boolean reconnect() {
        log.info("Attempting Redis reconnection");
        disconnect();
        return connect();
    }
    
    @Override
    public ConnectionStatus getConnectionStatus() {
        return currentStatus.get();
    }
    
    @Override
    public TopologyInfo.TopologyType getSupportedTopology() {
        return TopologyInfo.TopologyType.REDIS_STANDALONE;
    }
    
    @Override
    @PreDestroy
    public void disconnect() {
        log.info("Disconnecting from Redis");
        
        StatefulRedisConnection<String, String> conn = connection.getAndSet(null);
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
        
        currentStatus.set(ConnectionStatus.unknown("redis"));
    }
    
    @Override
    public boolean isConnected() {
        StatefulRedisConnection<String, String> conn = connection.get();
        return conn != null && conn.isOpen() && currentStatus.get().isHealthy();
    }
    
    @Override
    public boolean ping() {
        try {
            StatefulRedisConnection<String, String> conn = connection.get();
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
        StatefulRedisConnection<String, String> conn = connection.get();
        if (conn == null) {
            throw new IllegalStateException("Not connected to Redis");
        }
        return conn.sync().get(key);
    }
    
    @Override
    public void set(String key, String value) {
        StatefulRedisConnection<String, String> conn = connection.get();
        if (conn == null) {
            throw new IllegalStateException("Not connected to Redis");
        }
        conn.sync().set(key, value);
    }
}
