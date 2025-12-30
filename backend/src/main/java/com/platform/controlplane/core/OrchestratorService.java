package com.platform.controlplane.core;

import com.platform.controlplane.connectors.kafka.KafkaEventProducer;
import com.platform.controlplane.connectors.mysql.MySQLConnector;
import com.platform.controlplane.connectors.redis.RedisConnector;
import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.FailureEvent;
import com.platform.controlplane.model.SystemHealth;
import com.platform.controlplane.model.TopologyInfo;
import com.platform.controlplane.observability.MetricsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central orchestrator service that coordinates all connectors,
 * manages health checks, and pushes updates to the frontend.
 */
@Slf4j
@Service
public class OrchestratorService {
    
    private final MySQLConnector mysqlConnector;
    private final RedisConnector redisConnector;
    private final KafkaEventProducer kafkaProducer;
    private final TopologyDetector topologyDetector;
    private final MetricsRegistry metricsRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    
    private final AtomicReference<SystemHealth> currentHealth;
    private final AtomicReference<ConnectionStatus> previousMysqlStatus;
    private final AtomicReference<ConnectionStatus> previousRedisStatus;
    
    @Value("${controlplane.mysql.health-check-interval:10s}")
    private String mysqlHealthCheckInterval;
    
    @Value("${controlplane.redis.health-check-interval:10s}")
    private String redisHealthCheckInterval;
    
    public OrchestratorService(
            MySQLConnector mysqlConnector,
            RedisConnector redisConnector,
            KafkaEventProducer kafkaProducer,
            TopologyDetector topologyDetector,
            MetricsRegistry metricsRegistry,
            SimpMessagingTemplate messagingTemplate) {
        this.mysqlConnector = mysqlConnector;
        this.redisConnector = redisConnector;
        this.kafkaProducer = kafkaProducer;
        this.topologyDetector = topologyDetector;
        this.metricsRegistry = metricsRegistry;
        this.messagingTemplate = messagingTemplate;
        this.currentHealth = new AtomicReference<>();
        this.previousMysqlStatus = new AtomicReference<>();
        this.previousRedisStatus = new AtomicReference<>();
    }
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing OrchestratorService");
        
        // Connect to all systems
        initializeConnections();
        
        // Initial topology detection
        topologyDetector.detectAllTopologies();
        
        // Initial health check
        performHealthCheck();
        
        log.info("OrchestratorService initialized successfully");
    }
    
    private void initializeConnections() {
        log.info("Initializing connections to external systems");
        
        try {
            if (mysqlConnector.connect()) {
                log.info("MySQL connection established");
            } else {
                log.warn("Failed to establish MySQL connection");
            }
        } catch (Exception e) {
            log.error("Error connecting to MySQL: {}", e.getMessage());
        }
        
        try {
            if (redisConnector.connect()) {
                log.info("Redis connection established");
            } else {
                log.warn("Failed to establish Redis connection");
            }
        } catch (Exception e) {
            log.error("Error connecting to Redis: {}", e.getMessage());
        }
    }
    
    /**
     * Perform health check on all systems (runs every 10 seconds).
     */
    @Scheduled(fixedRateString = "${controlplane.health-check-interval:10000}")
    public void performHealthCheck() {
        log.debug("Performing scheduled health check");
        
        // Check MySQL
        ConnectionStatus mysqlStatus = mysqlConnector.healthCheck();
        checkStatusChange("mysql", previousMysqlStatus.getAndSet(mysqlStatus), mysqlStatus);
        
        // Check Redis
        ConnectionStatus redisStatus = redisConnector.healthCheck();
        checkStatusChange("redis", previousRedisStatus.getAndSet(redisStatus), redisStatus);
        
        // Check Kafka
        ConnectionStatus kafkaStatus = kafkaProducer.isKafkaAvailable() 
            ? ConnectionStatus.up("kafka", 0, 1, 100)
            : ConnectionStatus.down("kafka", "Kafka producer unavailable");
        
        // Get topologies
        TopologyInfo mysqlTopology = mysqlConnector.isConnected() 
            ? topologyDetector.getMySQLTopology() 
            : TopologyInfo.unknown("mysql");
        TopologyInfo redisTopology = redisConnector.isConnected() 
            ? topologyDetector.getRedisTopology() 
            : TopologyInfo.unknown("redis");
        TopologyInfo kafkaTopology = topologyDetector.getKafkaTopology();
        
        // Compute overall health
        SystemHealth health = SystemHealth.compute(
            mysqlStatus, redisStatus, kafkaStatus,
            mysqlTopology, redisTopology, kafkaTopology
        );
        
        currentHealth.set(health);
        
        // Update metrics
        metricsRegistry.setHealthStatus("mysql", mysqlStatus.isHealthy());
        metricsRegistry.setHealthStatus("redis", redisStatus.isHealthy());
        metricsRegistry.setHealthStatus("kafka", kafkaProducer.isKafkaAvailable());
        
        // Push to WebSocket clients
        pushHealthUpdate(health);
        
        // Process any queued Kafka events
        kafkaProducer.processQueuedEvents();
    }
    
    private void checkStatusChange(String system, ConnectionStatus previous, ConnectionStatus current) {
        if (previous == null) return;
        
        boolean wasHealthy = previous.isHealthy();
        boolean isHealthy = current.isHealthy();
        
        if (wasHealthy && !isHealthy) {
            log.warn("{} became unavailable: {}", system, current.errorMessage());
            if ("mysql".equals(system)) {
                kafkaProducer.emitMySQLUnavailable(current.errorMessage());
            } else if ("redis".equals(system)) {
                kafkaProducer.emitRedisUnavailable(current.errorMessage());
            }
        } else if (!wasHealthy && isHealthy) {
            log.info("{} recovered", system);
            if ("mysql".equals(system)) {
                kafkaProducer.emitMySQLRecovered();
            } else if ("redis".equals(system)) {
                kafkaProducer.emitRedisRecovered();
            }
        }
    }
    
    /**
     * Refresh topology detection for all systems (runs every 30 seconds).
     */
    @Scheduled(fixedRateString = "${controlplane.topology-check-interval:30000}")
    public void refreshTopologies() {
        log.debug("Refreshing topologies");
        topologyDetector.detectAllTopologies();
    }
    
    /**
     * Get current system health.
     */
    public SystemHealth getSystemHealth() {
        return currentHealth.get();
    }
    
    /**
     * Get health status for a specific system.
     */
    public ConnectionStatus getConnectionStatus(String system) {
        return switch (system.toLowerCase()) {
            case "mysql" -> mysqlConnector.getConnectionStatus();
            case "redis" -> redisConnector.getConnectionStatus();
            case "kafka" -> kafkaProducer.isKafkaAvailable() 
                ? ConnectionStatus.up("kafka", 0, 1, 100)
                : ConnectionStatus.down("kafka", "Kafka producer unavailable");
            default -> ConnectionStatus.unknown(system);
        };
    }
    
    /**
     * Force reconnection to a specific system.
     */
    public boolean forceReconnect(String system) {
        log.info("Forcing reconnection to {}", system);
        
        return switch (system.toLowerCase()) {
            case "mysql" -> {
                boolean result = mysqlConnector.reconnect();
                if (result) {
                    topologyDetector.detectMySQLTopology();
                }
                yield result;
            }
            case "redis" -> {
                boolean result = redisConnector.reconnect();
                if (result) {
                    topologyDetector.detectRedisTopology();
                }
                yield result;
            }
            default -> false;
        };
    }
    
    /**
     * Force topology refresh.
     */
    public void forceTopologyRefresh() {
        log.info("Forcing topology refresh");
        topologyDetector.detectAllTopologies();
        performHealthCheck();
    }
    
    private void pushHealthUpdate(SystemHealth health) {
        try {
            messagingTemplate.convertAndSend("/topic/health", health);
            log.debug("Pushed health update to WebSocket clients");
        } catch (Exception e) {
            log.warn("Failed to push health update: {}", e.getMessage());
        }
    }
    
    /**
     * Push a failure event to WebSocket clients.
     */
    public void pushFailureEvent(FailureEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/events", event);
            log.debug("Pushed failure event to WebSocket clients: {}", event.eventType());
        } catch (Exception e) {
            log.warn("Failed to push event: {}", e.getMessage());
        }
    }
}
