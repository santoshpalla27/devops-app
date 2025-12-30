package com.platform.controlplane.api;

import com.platform.controlplane.core.RetryEngine;
import com.platform.controlplane.observability.MetricsRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for metrics queries.
 */
@Slf4j
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@CrossOrigin(origins = "${controlplane.websocket.allowed-origins}")
public class MetricsController {
    
    private final MetricsRegistry metricsRegistry;
    private final MeterRegistry meterRegistry;
    private final RetryEngine retryEngine;
    
    /**
     * Get summary of key metrics.
     */
    @GetMapping("/summary")
    public ResponseEntity<MetricsSummary> getSummary() {
        MetricsSummary summary = new MetricsSummary(
            metricsRegistry.getLatency("mysql"),
            metricsRegistry.getLatency("redis"),
            metricsRegistry.getLatency("kafka"),
            metricsRegistry.getHealthStatus("mysql") == 1,
            metricsRegistry.getHealthStatus("redis") == 1,
            metricsRegistry.getHealthStatus("kafka") == 1,
            retryEngine.getAllRetryCounts()
        );
        
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Get connection pool statistics.
     */
    @GetMapping("/connections")
    public ResponseEntity<Map<String, ConnectionPoolStats>> getConnectionStats() {
        Map<String, ConnectionPoolStats> stats = new HashMap<>();
        
        // Get HikariCP metrics
        meterRegistry.find("hikaricp.connections.active").gauges()
            .forEach(gauge -> {
                String pool = gauge.getId().getTag("pool");
                if (pool != null) {
                    double active = gauge.value();
                    double idle = meterRegistry.find("hikaricp.connections.idle")
                        .tag("pool", pool).gauge().value();
                    double pending = meterRegistry.find("hikaricp.connections.pending")
                        .tag("pool", pool).gauge().value();
                    double max = meterRegistry.find("hikaricp.connections.max")
                        .tag("pool", pool).gauge().value();
                    
                    stats.put(pool, new ConnectionPoolStats(
                        (int) active, (int) idle, (int) pending, (int) max
                    ));
                }
            });
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get retry counts.
     */
    @GetMapping("/retries")
    public ResponseEntity<Map<String, Integer>> getRetryCounts() {
        return ResponseEntity.ok(retryEngine.getAllRetryCounts());
    }
    
    /**
     * Get latencies for all systems.
     */
    @GetMapping("/latencies")
    public ResponseEntity<Map<String, Long>> getLatencies() {
        Map<String, Long> latencies = new HashMap<>();
        latencies.put("mysql", metricsRegistry.getLatency("mysql"));
        latencies.put("redis", metricsRegistry.getLatency("redis"));
        latencies.put("kafka", metricsRegistry.getLatency("kafka"));
        return ResponseEntity.ok(latencies);
    }
    
    /**
     * Get all registered meters (for debugging).
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            metrics.put(name, meter.measure());
        }
        
        return ResponseEntity.ok(metrics);
    }
    
    public record MetricsSummary(
        long mysqlLatencyMs,
        long redisLatencyMs,
        long kafkaLatencyMs,
        boolean mysqlHealthy,
        boolean redisHealthy,
        boolean kafkaHealthy,
        Map<String, Integer> retryCounts
    ) {}
    
    public record ConnectionPoolStats(
        int activeConnections,
        int idleConnections,
        int pendingConnections,
        int maxConnections
    ) {}
}
