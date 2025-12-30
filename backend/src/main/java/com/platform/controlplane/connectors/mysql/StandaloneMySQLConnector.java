package com.platform.controlplane.connectors.mysql;

import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.TopologyInfo;
import com.platform.controlplane.model.TopologyInfo.NodeInfo;
import com.platform.controlplane.observability.MetricsRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MySQL connector for standalone (single-node) setup.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "controlplane.mysql.topology", havingValue = "standalone", matchIfMissing = true)
public class StandaloneMySQLConnector implements MySQLConnector {
    
    private final DataSource dataSource;
    private final MetricsRegistry metricsRegistry;
    private final AtomicReference<ConnectionStatus> currentStatus;
    private final AtomicReference<TopologyInfo> currentTopology;
    
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    
    public StandaloneMySQLConnector(DataSource dataSource, MetricsRegistry metricsRegistry) {
        this.dataSource = dataSource;
        this.metricsRegistry = metricsRegistry;
        this.currentStatus = new AtomicReference<>(ConnectionStatus.unknown("mysql"));
        this.currentTopology = new AtomicReference<>(TopologyInfo.unknown("mysql"));
    }
    
    @Override
    @CircuitBreaker(name = "mysql", fallbackMethod = "connectFallback")
    @Retry(name = "mysql")
    public boolean connect() {
        log.info("Attempting to connect to MySQL (standalone mode)");
        long startTime = System.currentTimeMillis();
        
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                long latency = System.currentTimeMillis() - startTime;
                currentStatus.set(ConnectionStatus.up("mysql", latency, 1, 20));
                metricsRegistry.recordConnectionSuccess("mysql");
                metricsRegistry.recordLatency("mysql", "connect", latency);
                log.info("Successfully connected to MySQL in {}ms", latency);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to connect to MySQL: {}", e.getMessage());
            currentStatus.set(ConnectionStatus.down("mysql", e.getMessage()));
            metricsRegistry.recordConnectionFailure("mysql");
        }
        return false;
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
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            
            if (rs.next()) {
                long latency = System.currentTimeMillis() - startTime;
                ConnectionStatus status = ConnectionStatus.up("mysql", latency, 
                    getActiveConnections(), getMaxConnections());
                currentStatus.set(status);
                metricsRegistry.recordLatency("mysql", "health_check", latency);
                return status;
            }
        } catch (Exception e) {
            log.error("MySQL health check failed: {}", e.getMessage());
            ConnectionStatus status = ConnectionStatus.down("mysql", e.getMessage());
            currentStatus.set(status);
            metricsRegistry.recordConnectionFailure("mysql");
            return status;
        }
        
        return ConnectionStatus.unknown("mysql");
    }
    
    @SuppressWarnings("unused")
    private ConnectionStatus healthCheckFallback(Exception e) {
        return ConnectionStatus.down("mysql", "Circuit breaker open");
    }
    
    @Override
    @CircuitBreaker(name = "mysql", fallbackMethod = "detectTopologyFallback")
    public TopologyInfo detectTopology() {
        log.debug("Detecting MySQL topology");
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Check if read-only (indicates replica)
            boolean isReadOnly = false;
            try (ResultSet rs = stmt.executeQuery("SELECT @@read_only")) {
                if (rs.next()) {
                    isReadOnly = rs.getInt(1) == 1;
                }
            }
            
            // Get server info
            String host = "localhost";
            int port = 3306;
            try (ResultSet rs = stmt.executeQuery("SELECT @@hostname, @@port")) {
                if (rs.next()) {
                    host = rs.getString(1);
                    port = rs.getInt(2);
                }
            }
            
            // Check for group replication
            boolean isGroupReplication = false;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM performance_schema.replication_group_members")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    isGroupReplication = true;
                }
            } catch (Exception ignored) {
                // Group replication not enabled
            }
            
            TopologyInfo.TopologyType type = isGroupReplication 
                ? TopologyInfo.TopologyType.MYSQL_GROUP_REPLICATION
                : TopologyInfo.TopologyType.MYSQL_STANDALONE;
            
            NodeInfo.NodeRole role = isReadOnly 
                ? NodeInfo.NodeRole.REPLICA 
                : NodeInfo.NodeRole.PRIMARY;
            
            NodeInfo node = new NodeInfo("main", host, port, role, true, 
                currentStatus.get().latencyMs());
            
            TopologyInfo topology = new TopologyInfo(
                "mysql",
                type,
                List.of(node),
                host + ":" + port,
                Instant.now(),
                "MySQL " + (isReadOnly ? "Read-Only" : "Read-Write") + " Mode"
            );
            
            currentTopology.set(topology);
            log.info("Detected MySQL topology: {} with role {}", type, role);
            return topology;
            
        } catch (Exception e) {
            log.error("Failed to detect MySQL topology: {}", e.getMessage());
            return TopologyInfo.unknown("mysql");
        }
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
        return TopologyInfo.TopologyType.MYSQL_STANDALONE;
    }
    
    @Override
    public void disconnect() {
        log.info("Disconnecting from MySQL");
        currentStatus.set(ConnectionStatus.unknown("mysql"));
    }
    
    @Override
    public boolean isConnected() {
        return currentStatus.get().isHealthy();
    }
    
    @Override
    public boolean validateConnection() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.debug("Connection validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    private int getActiveConnections() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW STATUS LIKE 'Threads_connected'")) {
            if (rs.next()) {
                return rs.getInt("Value");
            }
        } catch (Exception ignored) {}
        return 0;
    }
    
    private int getMaxConnections() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'max_connections'")) {
            if (rs.next()) {
                return rs.getInt("Value");
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
