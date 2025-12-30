package com.platform.controlplane.model;

import java.time.Instant;
import java.util.Map;

/**
 * Aggregated health status of all systems.
 */
public record SystemHealth(
    OverallStatus overallStatus,
    Map<String, ConnectionStatus> connectionStatuses,
    Map<String, TopologyInfo> topologies,
    Instant lastUpdated
) {
    public enum OverallStatus {
        HEALTHY,      // All systems UP
        DEGRADED,     // Some systems DOWN or DEGRADED
        UNHEALTHY     // All systems DOWN
    }
    
    public static SystemHealth compute(
        ConnectionStatus mysqlStatus,
        ConnectionStatus redisStatus,
        ConnectionStatus kafkaStatus,
        TopologyInfo mysqlTopology,
        TopologyInfo redisTopology,
        TopologyInfo kafkaTopology
    ) {
        Map<String, ConnectionStatus> statuses = Map.of(
            "mysql", mysqlStatus,
            "redis", redisStatus,
            "kafka", kafkaStatus
        );
        
        Map<String, TopologyInfo> topologies = Map.of(
            "mysql", mysqlTopology,
            "redis", redisTopology,
            "kafka", kafkaTopology
        );
        
        long healthyCount = statuses.values().stream()
            .filter(ConnectionStatus::isHealthy)
            .count();
        
        OverallStatus overall;
        if (healthyCount == statuses.size()) {
            overall = OverallStatus.HEALTHY;
        } else if (healthyCount == 0) {
            overall = OverallStatus.UNHEALTHY;
        } else {
            overall = OverallStatus.DEGRADED;
        }
        
        return new SystemHealth(overall, statuses, topologies, Instant.now());
    }
}
