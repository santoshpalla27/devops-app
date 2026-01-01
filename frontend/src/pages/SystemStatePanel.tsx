import { useEffect, useState } from 'react';
import { Shield, Clock, TrendingUp, AlertCircle } from 'lucide-react';
import type { SystemStateContext, SystemState } from '../types';

interface SystemStatePanelProps {
    systemType: string;
}

const STATE_COLORS: Record<SystemState, string> = {
    INIT: '#6B7280',
    CONNECTING: '#3B82F6',
    CONNECTED: '#10B981',
    DEGRADED: '#F59E0B',
    RETRYING: '#EF4444',
    CIRCUIT_OPEN: '#DC2626',
    RECOVERING: '#8B5CF6',
    DISCONNECTED: '#6B7280',
};

const STATE_LABELS: Record<SystemState, string> = {
    INIT: 'Initializing',
    CONNECTING: 'Connecting',
    CONNECTED: 'Connected',
    DEGRADED: 'Degraded',
    RETRYING: 'Retrying',
    CIRCUIT_OPEN: 'Circuit Open',
    RECOVERING: 'Recovering',
    DISCONNECTED: 'Disconnected',
};

export function SystemStatePanel({ systemType }: SystemStatePanelProps) {
    const [state, setState] = useState<SystemStateContext | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchState();
        const interval = setInterval(fetchState, 5000);
        return () => clearInterval(interval);
    }, [systemType]);

    const fetchState = async () => {
        try {
            const response = await fetch(`/api/policies/debug/state/${systemType}`);
            if (response.ok) {
                const data = await response.json();
                setState(data);
            }
        } catch (error) {
            console.error('Failed to fetch state:', error);
        } finally {
            setLoading(false);
        }
    };

    if (loading) {
        return <div className="card">Loading...</div>;
    }

    if (!state) {
        return <div className="card">No state data available</div>;
    }

    const stateColor = STATE_COLORS[state.currentState];
    const stateLabel = STATE_LABELS[state.currentState];

    return (
        <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <Shield size={24} style={{ color: stateColor }} />
                    <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>
                        {systemType.toUpperCase()} State
                    </h2>
                </div>
                <div
                    style={{
                        padding: '0.5rem 1rem',
                        borderRadius: 'var(--radius-md)',
                        background: `${stateColor}20`,
                        color: stateColor,
                        fontWeight: 600,
                        fontSize: '0.875rem',
                    }}
                >
                    {stateLabel}
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
                <div className="metric-card">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                        <Clock size={16} style={{ color: 'var(--text-secondary)' }} />
                        <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Latency</span>
                    </div>
                    <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                        {state.latencyMs}ms
                    </div>
                </div>

                <div className="metric-card">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                        <TrendingUp size={16} style={{ color: 'var(--text-secondary)' }} />
                        <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Retries</span>
                    </div>
                    <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                        {state.retryCount}
                    </div>
                </div>

                <div className="metric-card">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                        <AlertCircle size={16} style={{ color: 'var(--text-secondary)' }} />
                        <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Consecutive Failures</span>
                    </div>
                    <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                        {state.consecutiveFailures}
                    </div>
                </div>
            </div>

            {state.failureReason && (
                <div style={{
                    marginTop: '1rem',
                    padding: '0.75rem',
                    background: 'var(--error-bg)',
                    borderRadius: 'var(--radius-md)',
                    fontSize: '0.875rem',
                    color: 'var(--error)',
                }}>
                    <strong>Reason:</strong> {state.failureReason}
                </div>
            )}

            <div style={{ marginTop: '1rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                Last transition: {new Date(state.lastTransitionTime).toLocaleString()}
            </div>
        </div>
    );
}
