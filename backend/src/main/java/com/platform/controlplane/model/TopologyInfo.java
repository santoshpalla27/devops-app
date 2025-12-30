package com.platform.controlplane.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents the detected topology of an external system.
 */
public record TopologyInfo(
    String system,
    TopologyType topologyType,
    List<NodeInfo> nodes,
    String primaryNode,
    Instant lastDetected,
    String additionalInfo
) {
    public enum TopologyType {
        // MySQL
        MYSQL_STANDALONE,
        MYSQL_REPLICATION,
        MYSQL_CLUSTER,
        MYSQL_GROUP_REPLICATION,
        
        // Redis
        REDIS_STANDALONE,
        REDIS_SENTINEL,
        REDIS_CLUSTER,
        
        // Kafka
        KAFKA_SINGLE_BROKER,
        KAFKA_MULTI_BROKER,
        
        UNKNOWN
    }
    
    public record NodeInfo(
        String nodeId,
        String host,
        int port,
        NodeRole role,
        boolean isHealthy,
        long latencyMs
    ) {
        public enum NodeRole {
            PRIMARY,
            REPLICA,
            MASTER,
            SLAVE,
            SENTINEL,
            BROKER,
            UNKNOWN
        }
    }
    
    public static TopologyInfo standalone(String system, TopologyType type, String host, int port) {
        NodeInfo node = new NodeInfo("main", host, port, NodeInfo.NodeRole.PRIMARY, true, 0);
        return new TopologyInfo(system, type, List.of(node), host + ":" + port, Instant.now(), null);
    }
    
    public static TopologyInfo unknown(String system) {
        return new TopologyInfo(system, TopologyType.UNKNOWN, List.of(), null, Instant.now(), "Topology not yet detected");
    }
    
    public int getHealthyNodeCount() {
        return (int) nodes.stream().filter(NodeInfo::isHealthy).count();
    }
    
    public int getTotalNodeCount() {
        return nodes.size();
    }
}
