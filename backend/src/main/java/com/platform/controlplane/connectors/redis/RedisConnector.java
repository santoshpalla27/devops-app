package com.platform.controlplane.connectors.redis;

import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.TopologyInfo;

/**
 * Interface for Redis connectors supporting various topologies.
 * Implementations handle Standalone, Sentinel, and Cluster setups.
 */
public interface RedisConnector {
    
    /**
     * Establish connection to Redis.
     * @return true if connection successful
     */
    boolean connect();
    
    /**
     * Perform health check on the connection.
     * @return current connection status
     */
    ConnectionStatus healthCheck();
    
    /**
     * Detect the role of the Redis node (master/slave).
     * Uses the ROLE command.
     * @return detected topology information
     */
    TopologyInfo detectRole();
    
    /**
     * Attempt to reconnect after connection loss or failover.
     * @return true if reconnection successful
     */
    boolean reconnect();
    
    /**
     * Get current connection status without performing a check.
     * @return cached connection status
     */
    ConnectionStatus getConnectionStatus();
    
    /**
     * Get the topology type this connector is designed for.
     * @return topology type
     */
    TopologyInfo.TopologyType getSupportedTopology();
    
    /**
     * Close all connections and release resources.
     */
    void disconnect();
    
    /**
     * Check if connector is currently connected.
     * @return true if connected
     */
    boolean isConnected();
    
    /**
     * Execute PING command to validate connection.
     * @return true if PONG received
     */
    boolean ping();
    
    /**
     * Get string value for a key (for testing).
     */
    String get(String key);
    
    /**
     * Set string value for a key (for testing).
     */
    void set(String key, String value);
}
