import { useEffect, useState } from 'react';
import { Database, Server, Radio, RefreshCw, Zap } from 'lucide-react';
import { SystemHealth, ConnectionStatus, CircuitBreakerStatus } from '../types';
import { useApi } from '../hooks/useApi';

interface Props {
    health: SystemHealth | null;
    isConnected: boolean;
}

export function SystemHealthDashboard({ health, isConnected }: Props) {
    const { getCircuitBreakers, forceReconnect, resetCircuitBreaker, loading } = useApi();
    const [circuitBreakers, setCircuitBreakers] = useState<Record<string, CircuitBreakerStatus>>({});

    useEffect(() => {
        loadCircuitBreakers();
        const interval = setInterval(loadCircuitBreakers, 10000);
        return () => clearInterval(interval);
    }, []);

    async function loadCircuitBreakers() {
        try {
            const data = await getCircuitBreakers();
            setCircuitBreakers(data);
        } catch {
            // Ignore errors
        }
    }

    async function handleReconnect(system: string) {
        const result = await forceReconnect(system);
        if (result.success) {
            loadCircuitBreakers();
        }
    }

    async function handleResetCircuitBreaker(system: string) {
        const result = await resetCircuitBreaker(system);
        if (result.success) {
            loadCircuitBreakers();
        }
    }

    const overallStatusColor = health?.overallStatus === 'HEALTHY'
        ? 'var(--success)'
        : health?.overallStatus === 'DEGRADED'
            ? 'var(--warning)'
            : 'var(--error)';

    return (
        <div>
            {/* Header */}
            <div className="header">
                <div>
                    <h1>System Health</h1>
                    <p style={{ color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                        Real-time monitoring of all connected systems
                    </p>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.5rem',
                        padding: '0.5rem 1rem',
                        background: 'var(--bg-card)',
                        borderRadius: 'var(--radius-md)',
                        border: '1px solid var(--border)',
                    }}>
                        <Zap size={16} style={{ color: overallStatusColor }} />
                        <span style={{ fontWeight: 600, color: overallStatusColor }}>
                            {health?.overallStatus || 'UNKNOWN'}
                        </span>
                    </div>
                </div>
            </div>

            {/* System Cards */}
            <div className="grid grid-3" style={{ marginBottom: '2rem' }}>
                <SystemCard
                    name="MySQL"
                    icon={<Database size={24} />}
                    status={health?.connectionStatuses?.mysql}
                    circuitBreaker={circuitBreakers.mysql}
                    onReconnect={() => handleReconnect('mysql')}
                    onResetCB={() => handleResetCircuitBreaker('mysql')}
                    loading={loading}
                />
                <SystemCard
                    name="Redis"
                    icon={<Server size={24} />}
                    status={health?.connectionStatuses?.redis}
                    circuitBreaker={circuitBreakers.redis}
                    onReconnect={() => handleReconnect('redis')}
                    onResetCB={() => handleResetCircuitBreaker('redis')}
                    loading={loading}
                />
                <SystemCard
                    name="Kafka"
                    icon={<Radio size={24} />}
                    status={health?.connectionStatuses?.kafka}
                    circuitBreaker={circuitBreakers.kafka}
                    onReconnect={() => handleReconnect('kafka')}
                    onResetCB={() => handleResetCircuitBreaker('kafka')}
                    loading={loading}
                />
            </div>

            {/* Connection Status */}
            {!isConnected && (
                <div className="card" style={{
                    background: 'var(--warning-bg)',
                    borderColor: 'var(--warning)',
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                        <RefreshCw size={20} style={{ color: 'var(--warning)' }} />
                        <span style={{ color: 'var(--warning)', fontWeight: 500 }}>
                            WebSocket disconnected. Attempting to reconnect...
                        </span>
                    </div>
                </div>
            )}

            {/* Quick Stats */}
            <div className="grid grid-3">
                <QuickStat
                    label="MySQL Latency"
                    value={health?.connectionStatuses?.mysql?.latencyMs ?? -1}
                    unit="ms"
                />
                <QuickStat
                    label="Redis Latency"
                    value={health?.connectionStatuses?.redis?.latencyMs ?? -1}
                    unit="ms"
                />
                <QuickStat
                    label="Last Updated"
                    value={health?.lastUpdated ? formatTime(health.lastUpdated) : '-'}
                    unit=""
                />
            </div>
        </div>
    );
}

interface SystemCardProps {
    name: string;
    icon: React.ReactNode;
    status?: ConnectionStatus;
    circuitBreaker?: CircuitBreakerStatus;
    onReconnect: () => void;
    onResetCB: () => void;
    loading: boolean;
}

function SystemCard({ name, icon, status, circuitBreaker, onReconnect, onResetCB, loading }: SystemCardProps) {
    const systemClass = name.toLowerCase();
    const statusClass = status?.status?.toLowerCase() || 'unknown';

    return (
        <div className={`card system-card ${systemClass}`}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div className={`system-icon ${systemClass}`}>
                    {icon}
                </div>
                <span className={`status-badge status-${statusClass}`}>
                    <span className="pulse"></span>
                    {status?.status || 'UNKNOWN'}
                </span>
            </div>

            <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginTop: '1rem' }}>
                {name}
            </h3>

            {status?.errorMessage && (
                <p style={{
                    color: 'var(--error)',
                    fontSize: '0.8rem',
                    marginTop: '0.5rem',
                    wordBreak: 'break-word'
                }}>
                    {status.errorMessage}
                </p>
            )}

            <div style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '1rem',
                marginTop: '1rem',
                paddingTop: '1rem',
                borderTop: '1px solid var(--border)'
            }}>
                <div>
                    <div className="metric-label">Latency</div>
                    <div className="metric-value" style={{ fontSize: '1.25rem' }}>
                        {status?.latencyMs && status.latencyMs >= 0 ? `${status.latencyMs}ms` : '-'}
                    </div>
                </div>
                <div>
                    <div className="metric-label">Circuit Breaker</div>
                    <div style={{
                        fontSize: '0.9rem',
                        fontWeight: 600,
                        color: circuitBreaker?.state === 'CLOSED'
                            ? 'var(--success)'
                            : circuitBreaker?.state === 'OPEN'
                                ? 'var(--error)'
                                : 'var(--warning)'
                    }}>
                        {circuitBreaker?.state || 'UNKNOWN'}
                    </div>
                </div>
            </div>

            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
                <button
                    className="btn btn-secondary"
                    onClick={onReconnect}
                    disabled={loading}
                    style={{ flex: 1, fontSize: '0.8rem', padding: '0.5rem' }}
                >
                    <RefreshCw size={14} />
                    Reconnect
                </button>
                {circuitBreaker?.state === 'OPEN' && (
                    <button
                        className="btn btn-secondary"
                        onClick={onResetCB}
                        disabled={loading}
                        style={{ flex: 1, fontSize: '0.8rem', padding: '0.5rem' }}
                    >
                        Reset CB
                    </button>
                )}
            </div>
        </div>
    );
}

interface QuickStatProps {
    label: string;
    value: number | string;
    unit: string;
}

function QuickStat({ label, value, unit }: QuickStatProps) {
    return (
        <div className="card">
            <div className="metric-label">{label}</div>
            <div className="metric-value">
                {typeof value === 'number' && value < 0 ? '-' : value}
                {unit && <span style={{ fontSize: '1rem', color: 'var(--text-muted)' }}>{unit}</span>}
            </div>
        </div>
    );
}

function formatTime(isoString: string): string {
    try {
        const date = new Date(isoString);
        return date.toLocaleTimeString();
    } catch {
        return '-';
    }
}
