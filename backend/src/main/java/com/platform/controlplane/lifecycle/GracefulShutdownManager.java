package com.platform.controlplane.lifecycle;

import com.platform.controlplane.connectors.kafka.EventDispatcherService;
import com.platform.controlplane.connectors.kafka.KafkaEventProducer;
import com.platform.controlplane.observability.MetricsRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Graceful shutdown manager.
 * 
 * Ensures clean shutdown of:
 * - Schedulers (wait for running tasks)
 * - Kafka producers (flush pending messages)
 * - WebSocket sessions (notify and close)
 * - Thread pools (drain and terminate)
 * 
 * Order:
 * 1. Stop accepting new work
 * 2. Notify WebSocket clients
 * 3. Wait for in-flight tasks
 * 4. Flush Kafka
 * 5. Close connections
 */
@Slf4j
@Component
public class GracefulShutdownManager implements ApplicationListener<ContextClosedEvent> {
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private final KafkaTemplate<?, ?> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final EventDispatcherService eventDispatcher;
    private final MetricsRegistry metricsRegistry;
    
    @Value("${controlplane.shutdown.timeout-seconds:30}")
    private int shutdownTimeoutSeconds;
    
    @Value("${controlplane.shutdown.kafka-flush-timeout-seconds:10}")
    private int kafkaFlushTimeoutSeconds;
    
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private Instant shutdownStartTime;
    
    public GracefulShutdownManager(
            ThreadPoolTaskScheduler taskScheduler,
            KafkaTemplate<?, ?> kafkaTemplate,
            SimpMessagingTemplate messagingTemplate,
            SimpUserRegistry userRegistry,
            EventDispatcherService eventDispatcher,
            MetricsRegistry metricsRegistry) {
        this.taskScheduler = taskScheduler;
        this.kafkaTemplate = kafkaTemplate;
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
        this.eventDispatcher = eventDispatcher;
        this.metricsRegistry = metricsRegistry;
        
        // Register JVM shutdown hook as backup
        Runtime.getRuntime().addShutdownHook(new Thread(this::emergencyShutdown, "shutdown-hook"));
        
        log.info("Graceful shutdown manager initialized (timeout={}s)", shutdownTimeoutSeconds);
    }
    
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        performGracefulShutdown();
    }
    
    @PreDestroy
    public void onPreDestroy() {
        performGracefulShutdown();
    }
    
    /**
     * Check if shutdown is in progress.
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }
    
    /**
     * Perform graceful shutdown.
     */
    public synchronized void performGracefulShutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            log.debug("Shutdown already in progress");
            return;
        }
        
        shutdownStartTime = Instant.now();
        log.info("========== GRACEFUL SHUTDOWN INITIATED ==========");
        
        try {
            // Phase 1: Stop accepting new work
            log.info("[1/5] Stopping schedulers...");
            stopSchedulers();
            
            // Phase 2: Notify WebSocket clients
            log.info("[2/5] Notifying WebSocket clients...");
            notifyWebSocketClients();
            
            // Phase 3: Wait for in-flight tasks
            log.info("[3/5] Waiting for in-flight tasks...");
            waitForInFlightTasks();
            
            // Phase 4: Flush Kafka
            log.info("[4/5] Flushing Kafka producer...");
            flushKafka();
            
            // Phase 5: Close connections
            log.info("[5/5] Closing connections...");
            closeConnections();
            
            Duration duration = Duration.between(shutdownStartTime, Instant.now());
            log.info("========== GRACEFUL SHUTDOWN COMPLETE ({} ms) ==========", duration.toMillis());
            
        } catch (Exception e) {
            log.error("Error during graceful shutdown", e);
        }
    }
    
    /**
     * Emergency shutdown (JVM hook).
     */
    private void emergencyShutdown() {
        if (!shuttingDown.get()) {
            log.warn("Emergency shutdown hook triggered");
            performGracefulShutdown();
        }
    }
    
    /**
     * Stop all schedulers.
     */
    private void stopSchedulers() {
        try {
            if (taskScheduler != null) {
                taskScheduler.shutdown();
                log.info("Task scheduler shutdown initiated");
            }
        } catch (Exception e) {
            log.error("Error stopping schedulers", e);
        }
    }
    
    /**
     * Notify WebSocket clients of impending shutdown.
     */
    private void notifyWebSocketClients() {
        try {
            int sessionCount = userRegistry.getUserCount();
            if (sessionCount > 0) {
                log.info("Notifying {} WebSocket sessions of shutdown", sessionCount);
                
                // Send shutdown notification
                ShutdownNotification notification = new ShutdownNotification(
                    "SERVER_SHUTDOWN",
                    "Server is shutting down for maintenance",
                    shutdownTimeoutSeconds
                );
                
                messagingTemplate.convertAndSend("/topic/system", notification);
                
                // Give clients time to receive
                Thread.sleep(500);
            }
        } catch (Exception e) {
            log.warn("Error notifying WebSocket clients: {}", e.getMessage());
        }
    }
    
    /**
     * Wait for in-flight tasks to complete.
     */
    private void waitForInFlightTasks() {
        try {
            if (taskScheduler != null) {
                // Wait for scheduler to complete current tasks
                boolean terminated = taskScheduler.getScheduledThreadPoolExecutor()
                    .awaitTermination(shutdownTimeoutSeconds / 2, TimeUnit.SECONDS);
                
                if (terminated) {
                    log.info("All scheduled tasks completed");
                } else {
                    log.warn("Timeout waiting for scheduled tasks, forcing shutdown");
                    taskScheduler.getScheduledThreadPoolExecutor().shutdownNow();
                }
            }
            
            // Check event dispatcher backlog
            EventDispatcherService.OutboxStats stats = eventDispatcher.getStats();
            if (stats.pending() > 0 || stats.processing() > 0) {
                log.warn("Pending events in outbox: {} pending, {} processing", 
                    stats.pending(), stats.processing());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for tasks");
        } catch (Exception e) {
            log.error("Error waiting for in-flight tasks", e);
        }
    }
    
    /**
     * Flush Kafka producer.
     */
    private void flushKafka() {
        try {
            if (kafkaTemplate != null && kafkaTemplate.getProducerFactory() != null) {
                // Flush any pending messages
                kafkaTemplate.flush();
                log.info("Kafka producer flushed");
                
                // Destroy producer
                kafkaTemplate.getProducerFactory().reset();
                log.info("Kafka producer factory reset");
            }
        } catch (Exception e) {
            log.error("Error flushing Kafka", e);
        }
    }
    
    /**
     * Close connections.
     */
    private void closeConnections() {
        try {
            // WebSocket sessions will be closed by Spring
            int remainingSessions = userRegistry.getUserCount();
            if (remainingSessions > 0) {
                log.info("Closing {} remaining WebSocket sessions", remainingSessions);
            }
            
            // Record shutdown metric
            metricsRegistry.incrementCounter("lifecycle.shutdown", "status", "complete");
            
        } catch (Exception e) {
            log.error("Error closing connections", e);
        }
    }
    
    /**
     * Shutdown notification for WebSocket clients.
     */
    public record ShutdownNotification(
        String type,
        String message,
        int gracePeriodSeconds
    ) {}
}
