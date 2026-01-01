import { useEffect, useState } from 'react';
import { Shield, AlertTriangle } from 'lucide-react';

interface ReliabilityContract {
    subsystem: string;
    deliveryGuarantee: string;
    persistence: string;
    lossBehavior: string;
    recoveryExpectation: string;
    description: string;
}

interface ContractViolation {
    subsystem: string;
    violationType: string;
    description: string;
    timestamp: string;
}

export function ContractsPanel() {
    const [contracts, setContracts] = useState<Record<string, ReliabilityContract>>({});
    const [violations, setViolations] = useState<ContractViolation[]>([]);

    useEffect(() => {
        fetchContracts();
        fetchViolations();
        const interval = setInterval(() => {
            fetchContracts();
            fetchViolations();
        }, 10000);
        return () => clearInterval(interval);
    }, []);

    const fetchContracts = async () => {
        try {
            const response = await fetch('/api/system/contracts');
            if (response.ok) {
                const data = await response.json();
                setContracts(data);
            }
        } catch (error) {
            console.error('Failed to fetch contracts:', error);
        }
    };

    const fetchViolations = async () => {
        try {
            const response = await fetch('/api/system/contracts/violations');
            if (response.ok) {
                const data = await response.json();
                setViolations(data.slice(-10)); // Last 10
            }
        } catch (error) {
            console.error('Failed to fetch violations:', error);
        }
    };

    return (
        <div className="card">
            <div style={{ marginBottom: '1.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
                    <Shield size={24} style={{ color: 'var(--accent)' }} />
                    <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>Reliability Contracts</h2>
                </div>
                <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    Explicit guarantees for each subsystem
                </p>
            </div>

            {/* Contracts Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1rem', marginBottom: '2rem' }}>
                {Object.values(contracts).map((contract) => (
                    <div
                        key={contract.subsystem}
                        style={{
                            padding: '1rem',
                            borderRadius: 'var(--radius-lg)',
                            background: 'var(--bg-secondary)',
                            border: '1px solid var(--border)',
                        }}
                    >
                        <div style={{ fontWeight: 600, marginBottom: '0.75rem', textTransform: 'capitalize' }}>
                            {contract.subsystem.replace('-', ' ')}
                        </div>

                        <div style={{ fontSize: '0.875rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                            <div>
                                <span style={{ color: 'var(--text-secondary)' }}>Delivery:</span>{' '}
                                <span style={{ fontWeight: 500 }}>{contract.deliveryGuarantee.replace('_', ' ')}</span>
                            </div>
                            <div>
                                <span style={{ color: 'var(--text-secondary)' }}>Persistence:</span>{' '}
                                <span style={{ fontWeight: 500 }}>{contract.persistence.replace('_', ' ')}</span>
                            </div>
                            <div>
                                <span style={{ color: 'var(--text-secondary)' }}>Loss Behavior:</span>{' '}
                                <span style={{ fontWeight: 500 }}>{contract.lossBehavior}</span>
                            </div>
                            <div>
                                <span style={{ color: 'var(--text-secondary)' }}>Recovery:</span>{' '}
                                <span style={{ fontWeight: 500 }}>{contract.recoveryExpectation}</span>
                            </div>
                        </div>

                        <div style={{ marginTop: '0.75rem', fontSize: '0.75rem', color: 'var(--text-tertiary)', fontStyle: 'italic' }}>
                            {contract.description}
                        </div>
                    </div>
                ))}
            </div>

            {/* Violations */}
            {violations.length > 0 && (
                <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                        <AlertTriangle size={20} style={{ color: 'var(--warning)' }} />
                        <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 600 }}>Recent Violations</h3>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                        {violations.map((violation, idx) => (
                            <div
                                key={idx}
                                style={{
                                    padding: '0.75rem',
                                    borderRadius: 'var(--radius-md)',
                                    background: 'var(--warning-bg)',
                                    border: '1px solid var(--warning)',
                                    fontSize: '0.875rem',
                                }}
                            >
                                <div style={{ fontWeight: 600, marginBottom: '0.25rem' }}>
                                    {violation.subsystem} - {violation.violationType}
                                </div>
                                <div style={{ color: 'var(--text-secondary)' }}>{violation.description}</div>
                                <div style={{ fontSize: '0.75rem', color: 'var(--text-tertiary)', marginTop: '0.25rem' }}>
                                    {new Date(violation.timestamp).toLocaleString()}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
