package com.platform.controlplane.connectors.mysql;

import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.TopologyInfo;

/**
 * Interface for MySQL connectors supporting various topologies.
 * Implementations handle Standalone, Replication, and Cluster setups.
 */
public interface MySQLConnector {
    
    /**
     * Establish connection to MySQL.
     * @return true if connection successful
     */
    boolean connect();
    
    /**
     * Perform health check on the connection.
     * @return current connection status
     */
    ConnectionStatus healthCheck();
    
    /**
     * Detect the current topology of MySQL setup.
     * Uses information_schema, performance_schema, and read-only flags.
     * @return detected topology information
     */
    TopologyInfo detectTopology();
    
    /**
     * Attempt to reconnect after connection loss.
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
     * Execute a simple query to validate connection.
     * @return true if query succeeds
     */
    boolean validateConnection();
}
