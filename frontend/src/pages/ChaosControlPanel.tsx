import { useState, useEffect } from 'react';
import { Zap, Play, StopCircle, RefreshCw, AlertOctagon } from 'lucide-react';
import type { ChaosExperiment, FaultType } from '../types';

const FAULT_LABELS: Record<FaultType, string> = {
    CONNECTION_LOSS: 'Connection Loss',
    LATENCY_INJECTION: 'Latency Injection',
    PARTIAL_FAILURE: 'Partial Failure',
    CIRCUIT_BREAKER_FORCE_OPEN: 'Force Circuit Open',
    TIMEOUT: 'Timeout',
    NETWORK_PARTITION: 'Network Partition',
};

export function ChaosControlPanel() {
    const [experiments, setExperiments] = useState<ChaosExperiment[]>([]);
    const [loading, setLoading] = useState(false);

    // Quick inject form
    const [systemType, setSystemType] = useState<string>('mysql');
    const [faultType, setFaultType] = useState<FaultType>('CONNECTION_LOSS');
    const [duration, setDuration] = useState<number>(60);

    useEffect(() => {
        fetchExperiments();
        const interval = setInterval(fetchExperiments, 5000);
        return () => clearInterval(interval);
    }, []);

    const fetchExperiments = async () => {
        try {
            const response = await fetch('/api/chaos/experiments?activeOnly=true');
            if (response.ok) {
                const data = await response.json();
                setExperiments(data);
            }
        } catch (error) {
            console.error('Failed to fetch experiments:', error);
        }
    };

    const handleQuickInject = async () => {
        if (loading) return;

        setLoading(true);
        try {
            const response = await fetch('/api/chaos/inject', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ systemType, faultType, durationSeconds: duration }),
            });

            if (response.ok) {
                await fetchExperiments();
            }
        } catch (error) {
            console.error('Failed to inject fault:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleStopExperiment = async (id: string) => {
        try {
            await fetch(`/api/chaos/experiments/${id}`, {
                method: 'DELETE',
            });
            await fetchExperiments();
        } catch (error) {
            console.error('Failed to stop experiment:', error);
        }
    };

    return (
        <div className="card">
            <div style={{ marginBottom: '1.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
                    <Zap size={24} style={{ color: 'var(--warning)' }} />
                    <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>Chaos Control Panel</h2>
                </div>
                <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    Inject faults to test system resilience
                </p>
            </div>

            {/* Quick Inject Form */}
            <div style={{
                padding: '1.5rem',
                background: 'var(--bg-secondary)',
                borderRadius: 'var(--radius-lg)',
                marginBottom: '1.5rem',
            }}>
                <h3 style={{ margin: '0 0 1rem 0', fontSize: '1rem', fontWeight: 600 }}>Quick Inject</h3>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '1rem', marginBottom: '1rem' }}>
                    <div>
                        <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                            System
                        </label>
                        <select
                            value={systemType}
                            onChange={(e) => setSystemType(e.target.value)}
                            style={{
                                width: '100%',
                                padding: '0.75rem',
                                borderRadius: 'var(--radius-md)',
                                border: '1px solid var(--border)',
                                background: 'var(--bg-primary)',
                                color: 'var(--text-primary)',
                            }}
                        >
                            <option value="mysql">MySQL</option>
                            <option value="redis">Redis</option>
                            <option value="kafka">Kafka</option>
                        </select>
                    </div>

                    <div>
                        <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                            Fault Type
                        </label>
                        <select
                            value={faultType}
                            onChange={(e) => setFaultType(e.target.value as FaultType)}
                            style={{
                                width: '100%',
                                padding: '0.75rem',
                                borderRadius: 'var(--radius-md)',
                                border: '1px solid var(--border)',
                                background: 'var(--bg-primary)',
                                color: 'var(--text-primary)',
                            }}
                        >
                            {Object.entries(FAULT_LABELS).map(([value, label]) => (
                                <option key={value} value={value}>{label}</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                            Duration (seconds)
                        </label>
                        <input
                            type="number"
                            value={duration}
                            onChange={(e) => setDuration(Number(e.target.value))}
                            min="10"
                            max="600"
                            style={{
                                width: '100%',
                                padding: '0.75rem',
                                borderRadius: 'var(--radius-md)',
                                border: '1px solid var(--border)',
                                background: 'var(--bg-primary)',
                                color: 'var(--text-primary)',
                            }}
                        />
                    </div>
                </div>

                <button
                    onClick={handleQuickInject}
                    disabled={loading}
                    className="btn btn-primary"
                    style={{
                        width: '100%',
                        background: 'var(--warning)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '0.5rem',
                    }}
                >
                    {loading ? (
                        <>
                            <RefreshCw size={16} className="spin" />
                            Injecting...
                        </>
                    ) : (
                        <>
                            <Play size={16} />
                            Inject Fault
                        </>
                    )}
                </button>
            </div>

            {/* Active Experiments */}
            <div>
                <h3 style={{ margin: '0 0 1rem 0', fontSize: '1rem', fontWeight: 600 }}>Active Experiments</h3>

                {experiments.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)' }}>
                        No active experiments
                    </div>
                ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                        {experiments.map((experiment) => (
                            <div
                                key={experiment.id}
                                style={{
                                    padding: '1rem',
                                    borderRadius: 'var(--radius-md)',
                                    background: 'var(--warning-bg)',
                                    border: '1px solid var(--warning)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '1rem',
                                }}
                            >
                                <AlertOctagon size={20} style={{ color: 'var(--warning)', flexShrink: 0 }} />

                                <div style={{ flex: 1 }}>
                                    <div style={{ fontWeight: 600, marginBottom: '0.25rem' }}>
                                        {FAULT_LABELS[experiment.faultType]} on {experiment.systemType.toUpperCase()}
                                    </div>
                                    <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                                        Started: {new Date(experiment.startedAt || '').toLocaleTimeString()} •
                                        Duration: {experiment.durationSeconds}s
                                    </div>
                                </div>

                                <button
                                    onClick={() => handleStopExperiment(experiment.id)}
                                    className="btn btn-secondary"
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '0.5rem',
                                        padding: '0.5rem 1rem',
                                    }}
                                >
                                    <StopCircle size={16} />
                                    Stop
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
