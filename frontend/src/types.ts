// Type definitions for the control plane API

export type SystemStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN';
export type OverallStatus = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY';

export interface ConnectionStatus {
    system: string;
    status: SystemStatus;
    latencyMs: number;
    lastChecked: string;
    errorMessage: string | null;
    activeConnections: number;
    maxConnections: number;
}

export interface NodeInfo {
    nodeId: string;
    host: string;
    port: number;
    role: 'PRIMARY' | 'REPLICA' | 'MASTER' | 'SLAVE' | 'SENTINEL' | 'BROKER' | 'UNKNOWN';
    isHealthy: boolean;
    latencyMs: number;
}

export interface TopologyInfo {
    system: string;
    topologyType: string;
    nodes: NodeInfo[];
    primaryNode: string | null;
    lastDetected: string;
    additionalInfo: string | null;
}

export interface SystemHealth {
    overallStatus: OverallStatus;
    connectionStatuses: Record<string, ConnectionStatus>;
    topologies: Record<string, TopologyInfo>;
    lastUpdated: string;
}

export interface CircuitBreakerStatus {
    state: 'OPEN' | 'CLOSED' | 'HALF_OPEN' | 'UNKNOWN';
    successfulCalls: number;
    failedCalls: number;
    failureRate: number;
    slowCallRate: number;
}

export interface FailureEvent {
    eventId: string;
    eventType: string;
    system: string;
    timestamp: string;
    message: string;
    retryCount: number;
    metadata: Record<string, unknown>;
}

export interface MetricsSummary {
    mysqlLatencyMs: number;
    redisLatencyMs: number;
    kafkaLatencyMs: number;
    mysqlHealthy: boolean;
    redisHealthy: boolean;
    kafkaHealthy: boolean;
    retryCounts: Record<string, number>;
}

export interface ActionResult {
    success: boolean;
    message: string;
}
