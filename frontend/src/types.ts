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

// Phase 5: State Machine Types
export type SystemState = 'INIT' | 'CONNECTING' | 'CONNECTED' | 'DEGRADED' | 'RETRYING' | 'CIRCUIT_OPEN' | 'RECOVERING' | 'DISCONNECTED';

export interface SystemStateContext {
    systemType: string;
    currentState: SystemState;
    previousState: SystemState | null;
    lastTransitionTime: string;
    failureReason: string | null;
    retryCount: number;
    latencyMs: number;
    consecutiveFailures: number;
}

export interface StateTransition {
    id: string;
    systemType: string;
    fromState: SystemState;
    toState: SystemState;
    reason: string;
    timestamp: string;
}

// Policy Types
export type PolicyAction = 'FORCE_RECONNECT' | 'OPEN_CIRCUIT' | 'CLOSE_CIRCUIT' | 'EMIT_ALERT' | 'MARK_DEGRADED' | 'NO_ACTION';
export type PolicySeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface PolicyExecutionRecord {
    id: string;
    policyId: string;
    policyName: string;
    systemType: string;
    action: PolicyAction;
    success: boolean;
    message: string;
    executedAt: string;
    durationMs: number;
}

export interface Policy {
    id: string;
    name: string;
    systemType: string;
    conditionDescription: string;
    action: PolicyAction;
    severity: PolicySeverity;
    enabled: boolean;
    cooldownSeconds: number;
    description: string;
}

// Chaos Engineering Types
export type FaultType = 'CONNECTION_LOSS' | 'LATENCY_INJECTION' | 'PARTIAL_FAILURE' | 'CIRCUIT_BREAKER_FORCE_OPEN' | 'TIMEOUT' | 'NETWORK_PARTITION';
export type ExperimentStatus = 'CREATED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface ChaosExperiment {
    id: string;
    name: string;
    systemType: string;
    faultType: FaultType;
    durationSeconds: number;
    status: ExperimentStatus;
    createdAt: string;
    startedAt: string | null;
    endedAt: string | null;
    result: string | null;
}

