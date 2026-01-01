import { useEffect, useState } from 'react';
import { CheckCircle, XCircle, Clock, Search } from 'lucide-react';
import type { PolicyExecutionRecord } from '../types';

export function PolicyActivityLog() {
    const [executions, setExecutions] = useState<PolicyExecutionRecord[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');

    useEffect(() => {
        fetchExecutions();
        const interval = setInterval(fetchExecutions, 10000);
        return () => clearInterval(interval);
    }, []);

    const fetchExecutions = async () => {
        try {
            const response = await fetch('http://localhost:8080/api/policies/executions?limit=50');
            if (response.ok) {
                const data = await response.json();
                setExecutions(data);
            }
        } catch (error) {
            console.error('Failed to fetch policy executions:', error);
        } finally {
            setLoading(false);
        }
    };

    const filteredExecutions = searchTerm
        ? executions.filter(ex =>
            ex.policyName.toLowerCase().includes(searchTerm.toLowerCase()) ||
            ex.systemType.toLowerCase().includes(searchTerm.toLowerCase())
        )
        : executions;

    return (
        <div className="card">
            <div style={{ marginBottom: '1.5rem' }}>
                <h2 style={{ margin: 0, marginBottom: '1rem', fontSize: '1.25rem', fontWeight: 600 }}>
                    Policy Activity Log
                </h2>

                <div style={{ position: 'relative' }}>
                    <Search
                        size={16}
                        style={{
                            position: 'absolute',
                            left: '0.75rem',
                            top: '50%',
                            transform: 'translateY(-50%)',
                            color: 'var(--text-secondary)',
                        }}
                    />
                    <input
                        type="text"
                        placeholder="Search by policy or system..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        style={{
                            width: '100%',
                            padding: '0.75rem 0.75rem 0.75rem 2.5rem',
                            borderRadius: 'var(--radius-md)',
                            border: '1px solid var(--border)',
                            background: 'var(--bg-secondary)',
                            color: 'var(--text-primary)',
                        }}
                    />
                </div>
            </div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)' }}>
                    Loading...
                </div>
            ) : filteredExecutions.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)' }}>
                    No policy executions found
                </div>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    {filteredExecutions.map((execution) => (
                        <div
                            key={execution.id}
                            style={{
                                padding: '1rem',
                                borderRadius: 'var(--radius-md)',
                                background: 'var(--bg-secondary)',
                                border: '1px solid var(--border)',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '1rem',
                            }}
                        >
                            {execution.success ? (
                                <CheckCircle size={20} style={{ color: 'var(--success)', flexShrink: 0 }} />
                            ) : (
                                <XCircle size={20} style={{ color: 'var(--error)', flexShrink: 0 }} />
                            )}

                            <div style={{ flex: 1 }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.25rem' }}>
                                    <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                                        {execution.policyName}
                                    </span>
                                    <span
                                        style={{
                                            padding: '0.125rem 0.5rem',
                                            background: 'var(--accent-bg)',
                                            color: 'var(--accent)',
                                            borderRadius: 'var(--radius-md)',
                                            fontSize: '0.75rem',
                                            fontWeight: 600,
                                        }}
                                    >
                                        {execution.systemType}
                                    </span>
                                    <span
                                        style={{
                                            padding: '0.125rem 0.5rem',
                                            background: 'var(--bg-tertiary)',
                                            color: 'var(--text-secondary)',
                                            borderRadius: 'var(--radius-md)',
                                            fontSize: '0.75rem',
                                        }}
                                    >
                                        {execution.action}
                                    </span>
                                </div>

                                <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                                    {execution.message}
                                </div>
                            </div>

                            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.25rem' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                                    <Clock size={12} style={{ color: 'var(--text-tertiary)' }} />
                                    <span style={{ fontSize: '0.75rem', color: 'var(--text-tertiary)' }}>
                                        {execution.durationMs}ms
                                    </span>
                                </div>
                                <span style={{ fontSize: '0.75rem', color: 'var(--text-tertiary)' }}>
                                    {new Date(execution.executedAt).toLocaleTimeString()}
                                </span>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
