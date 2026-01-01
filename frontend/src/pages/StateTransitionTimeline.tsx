import { useEffect, useState } from 'react';
import { ArrowRight, Filter } from 'lucide-react';
import type { StateTransition, SystemState } from '../types';

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

export function StateTransitionTimeline() {
    const [transitions, setTransitions] = useState<StateTransition[]>([]);
    const [filter, setFilter] = useState<string>('all');

    useEffect(() => {
        // Mock data - in production, subscribe to WebSocket events
        const mockTransitions: StateTransition[] = [
            {
                id: '1',
                systemType: 'mysql',
                fromState: 'CONNECTED',
                toState: 'DISCONNECTED',
                reason: 'Chaos experiment: connection loss test',
                timestamp: new Date(Date.now() - 60000).toISOString(),
            },
            {
                id: '2',
                systemType: 'mysql',
                fromState: 'DISCONNECTED',
                toState: 'CONNECTING',
                reason: 'Policy-triggered reconnection',
                timestamp: new Date(Date.now() - 50000).toISOString(),
            },
            {
                id: '3',
                systemType: 'mysql',
                fromState: 'CONNECTING',
                toState: 'CONNECTED',
                reason: 'Connection restored',
                timestamp: new Date(Date.now() - 45000).toISOString(),
            },
        ];
        setTransitions(mockTransitions);
    }, []);

    const filteredTransitions = filter === 'all'
        ? transitions
        : transitions.filter(t => t.systemType === filter);

    return (
        <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
                <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>State Transition Timeline</h2>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Filter size={16} style={{ color: 'var(--text-secondary)' }} />
                    <select
                        value={filter}
                        onChange={(e) => setFilter(e.target.value)}
                        style={{
                            padding: '0.5rem',
                            borderRadius: 'var(--radius-md)',
                            border: '1px solid var(--border)',
                            background: 'var(--bg-secondary)',
                            color: 'var(--text-primary)',
                        }}
                    >
                        <option value="all">All Systems</option>
                        <option value="mysql">MySQL</option>
                        <option value="redis">Redis</option>
                        <option value="kafka">Kafka</option>
                    </select>
                </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                {filteredTransitions.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)' }}>
                        No state transitions yet
                    </div>
                ) : (
                    filteredTransitions.map((transition) => (
                        <div
                            key={transition.id}
                            style={{
                                padding: '1rem',
                                borderRadius: 'var(--radius-md)',
                                background: 'var(--bg-secondary)',
                                border: '1px solid var(--border)',
                            }}
                        >
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                                    <span
                                        style={{
                                            padding: '0.25rem 0.75rem',
                                            borderRadius: 'var(--radius-md)',
                                            background: `${STATE_COLORS[transition.fromState]}20`,
                                            color: STATE_COLORS[transition.fromState],
                                            fontSize: '0.875rem',
                                            fontWeight: 600,
                                        }}
                                    >
                                        {transition.fromState}
                                    </span>
                                    <ArrowRight size={16} style={{ color: 'var(--text-secondary)' }} />
                                    <span
                                        style={{
                                            padding: '0.25rem 0.75rem',
                                            borderRadius: 'var(--radius-md)',
                                            background: `${STATE_COLORS[transition.toState]}20`,
                                            color: STATE_COLORS[transition.toState],
                                            fontSize: '0.875rem',
                                            fontWeight: 600,
                                        }}
                                    >
                                        {transition.toState}
                                    </span>
                                </div>

                                <span style={{
                                    padding: '0.25rem 0.75rem',
                                    background: 'var(--accent-bg)',
                                    color: 'var(--accent)',
                                    borderRadius: 'var(--radius-md)',
                                    fontSize: '0.75rem',
                                    fontWeight: 600,
                                    textTransform: 'uppercase',
                                }}>
                                    {transition.systemType}
                                </span>
                            </div>

                            <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>
                                {transition.reason}
                            </div>

                            <div style={{ fontSize: '0.75rem', color: 'var(--text-tertiary)' }}>
                                {new Date(transition.timestamp).toLocaleString()}
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}
