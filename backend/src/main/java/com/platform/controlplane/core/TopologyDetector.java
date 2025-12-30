package com.platform.controlplane.core;

import com.platform.controlplane.connectors.mysql.MySQLConnector;
import com.platform.controlplane.connectors.redis.RedisConnector;
import com.platform.controlplane.model.TopologyInfo;
import com.platform.controlplane.observability.MetricsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detects and tracks topology changes for MySQL, Redis, and Kafka.
 */
@Slf4j
@Component
public class TopologyDetector {
    
    private final MySQLConnector mysqlConnector;
    private final RedisConnector redisConnector;
    private final MetricsRegistry metricsRegistry;
    
    private final AtomicReference<TopologyInfo> mysqlTopology;
    private final AtomicReference<TopologyInfo> redisTopology;
    private final AtomicReference<TopologyInfo> kafkaTopology;
    
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;
    
    public TopologyDetector(
            MySQLConnector mysqlConnector,
            RedisConnector redisConnector,
            MetricsRegistry metricsRegistry) {
        this.mysqlConnector = mysqlConnector;
        this.redisConnector = redisConnector;
        this.metricsRegistry = metricsRegistry;
        this.mysqlTopology = new AtomicReference<>(TopologyInfo.unknown("mysql"));
        this.redisTopology = new AtomicReference<>(TopologyInfo.unknown("redis"));
        this.kafkaTopology = new AtomicReference<>(TopologyInfo.unknown("kafka"));
    }
    
    /**
     * Detect all topologies.
     */
    public void detectAllTopologies() {
        detectMySQLTopology();
        detectRedisTopology();
        detectKafkaTopology();
    }
    
    /**
     * Detect MySQL topology.
     */
    public TopologyInfo detectMySQLTopology() {
        try {
            if (!mysqlConnector.isConnected()) {
                log.debug("MySQL not connected, skipping topology detection");
                return mysqlTopology.get();
            }
            
            TopologyInfo newTopology = mysqlConnector.detectTopology();
            TopologyInfo previousTopology = mysqlTopology.getAndSet(newTopology);
            
            // Check for topology change
            if (hasTopologyChanged(previousTopology, newTopology)) {
                log.info("MySQL topology changed: {} -> {}", 
                    previousTopology.topologyType(), newTopology.topologyType());
                metricsRegistry.recordTopologyChange("mysql", newTopology.topologyType().name());
            }
            
            return newTopology;
            
        } catch (Exception e) {
            log.error("Failed to detect MySQL topology: {}", e.getMessage());
            return mysqlTopology.get();
        }
    }
    
    /**
     * Detect Redis topology.
     */
    public TopologyInfo detectRedisTopology() {
        try {
            if (!redisConnector.isConnected()) {
                log.debug("Redis not connected, skipping topology detection");
                return redisTopology.get();
            }
            
            TopologyInfo newTopology = redisConnector.detectRole();
            TopologyInfo previousTopology = redisTopology.getAndSet(newTopology);
            
            // Check for topology change (especially failover)
            if (hasTopologyChanged(previousTopology, newTopology)) {
                log.info("Redis topology changed: {} -> {}", 
                    previousTopology.topologyType(), newTopology.topologyType());
                metricsRegistry.recordTopologyChange("redis", newTopology.topologyType().name());
                
                // Check for master change (failover)
                if (hasPrimaryChanged(previousTopology, newTopology)) {
                    log.warn("Redis failover detected: {} -> {}", 
                        previousTopology.primaryNode(), newTopology.primaryNode());
                }
            }
            
            return newTopology;
            
        } catch (Exception e) {
            log.error("Failed to detect Redis topology: {}", e.getMessage());
            return redisTopology.get();
        }
    }
    
    /**
     * Detect Kafka topology (broker list).
     */
    public TopologyInfo detectKafkaTopology() {
        try {
            String[] brokers = kafkaBootstrapServers.split(",");
            List<TopologyInfo.NodeInfo> nodes = java.util.Arrays.stream(brokers)
                .map(broker -> {
                    String[] parts = broker.trim().split(":");
                    String host = parts[0];
                    int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9092;
                    return new TopologyInfo.NodeInfo(
                        broker,
                        host,
                        port,
                        TopologyInfo.NodeInfo.NodeRole.BROKER,
                        true, // Assume healthy; actual check requires admin client
                        0
                    );
                })
                .toList();
            
            TopologyInfo.TopologyType type = nodes.size() > 1 
                ? TopologyInfo.TopologyType.KAFKA_MULTI_BROKER 
                : TopologyInfo.TopologyType.KAFKA_SINGLE_BROKER;
            
            TopologyInfo topology = new TopologyInfo(
                "kafka",
                type,
                nodes,
                brokers[0],
                Instant.now(),
                String.format("Kafka with %d broker(s)", nodes.size())
            );
            
            kafkaTopology.set(topology);
            return topology;
            
        } catch (Exception e) {
            log.error("Failed to detect Kafka topology: {}", e.getMessage());
            return kafkaTopology.get();
        }
    }
    
    public TopologyInfo getMySQLTopology() {
        return mysqlTopology.get();
    }
    
    public TopologyInfo getRedisTopology() {
        return redisTopology.get();
    }
    
    public TopologyInfo getKafkaTopology() {
        return kafkaTopology.get();
    }
    
    private boolean hasTopologyChanged(TopologyInfo previous, TopologyInfo current) {
        if (previous == null || previous.topologyType() == TopologyInfo.TopologyType.UNKNOWN) {
            return false;
        }
        
        return previous.topologyType() != current.topologyType() ||
               previous.getTotalNodeCount() != current.getTotalNodeCount();
    }
    
    private boolean hasPrimaryChanged(TopologyInfo previous, TopologyInfo current) {
        if (previous == null || previous.primaryNode() == null) {
            return false;
        }
        
        return !previous.primaryNode().equals(current.primaryNode());
    }
}
