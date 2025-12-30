import { AlertTriangle, CheckCircle, XCircle, RefreshCw, Info } from 'lucide-react';
import { FailureEvent } from '../types';
import { formatDistanceToNow } from 'date-fns';

interface Props {
    events: FailureEvent[];
}

export function FailureTimeline({ events }: Props) {
    const getEventCategory = (type: string): 'error' | 'success' | 'warning' | 'info' => {
        if (type.includes('UNAVAILABLE') || type.includes('DOWN') || type.includes('EXHAUSTED') || type.includes('FAILED')) {
            return 'error';
        }
        if (type.includes('RECOVERED') || type.includes('CLOSED') || type.includes('SUCCESSFUL')) {
            return 'success';
        }
        if (type.includes('HALF_OPEN') || type.includes('ATTEMPTED') || type.includes('CHANGED')) {
            return 'warning';
        }
        return 'info';
    };

    const getEventIcon = (category: string) => {
        switch (category) {
            case 'error':
                return <XCircle size={16} style={{ color: 'var(--error)' }} />;
            case 'success':
                return <CheckCircle size={16} style={{ color: 'var(--success)' }} />;
            case 'warning':
                return <RefreshCw size={16} style={{ color: 'var(--warning)' }} />;
            default:
                return <Info size={16} style={{ color: 'var(--accent-primary)' }} />;
        }
    };

    const formatEventType = (type: string): string => {
        return type
            .replace(/_/g, ' ')
            .split(' ')
            .map(word => word.charAt(0) + word.slice(1).toLowerCase())
            .join(' ');
    };

    return (
        <div>
            <div className="header">
                <div>
                    <h1>Event Timeline</h1>
                    <p style={{ color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                        Chronological view of system events, failures, and recoveries
                    </p>
                </div>
                <span style={{
                    padding: '0.5rem 1rem',
                    background: 'var(--bg-card)',
                    border: '1px solid var(--border)',
                    borderRadius: 'var(--radius-md)',
                    fontSize: '0.9rem'
                }}>
                    {events.length} event{events.length !== 1 ? 's' : ''}
                </span>
            </div>

            {events.length === 0 ? (
                <div className="card">
                    <div className="empty-state">
                        <AlertTriangle size={48} style={{ opacity: 0.3 }} />
                        <h3 style={{ marginTop: '1rem', fontWeight: 600 }}>No Events Yet</h3>
                        <p style={{ color: 'var(--text-muted)' }}>
                            Events will appear here when system changes occur
                        </p>
                    </div>
                </div>
            ) : (
                <div className="timeline">
                    {events.map((event, index) => {
                        const category = getEventCategory(event.eventType);
                        return (
                            <div key={event.eventId || index} className={`timeline-item ${category}`}>
                                <div className="card" style={{ marginBottom: 0 }}>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'flex-start'
                                    }}>
                                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
                                            {getEventIcon(category)}
                                            <div>
                                                <div style={{ fontWeight: 600, fontSize: '0.95rem' }}>
                                                    {formatEventType(event.eventType)}
                                                </div>
                                                <div style={{
                                                    color: 'var(--text-muted)',
                                                    fontSize: '0.8rem',
                                                    marginTop: '0.25rem'
                                                }}>
                                                    {event.message}
                                                </div>
                                            </div>
                                        </div>
                                        <div style={{ textAlign: 'right' }}>
                                            <span style={{
                                                padding: '0.125rem 0.5rem',
                                                background: 'var(--bg-tertiary)',
                                                borderRadius: 'var(--radius-full)',
                                                fontSize: '0.75rem',
                                                fontWeight: 500,
                                                textTransform: 'uppercase'
                                            }}>
                                                {event.system}
                                            </span>
                                            <div style={{
                                                color: 'var(--text-muted)',
                                                fontSize: '0.75rem',
                                                marginTop: '0.5rem'
                                            }}>
                                                {formatDistanceToNow(new Date(event.timestamp), { addSuffix: true })}
                                            </div>
                                        </div>
                                    </div>

                                    {event.retryCount > 0 && (
                                        <div style={{
                                            marginTop: '0.75rem',
                                            paddingTop: '0.75rem',
                                            borderTop: '1px solid var(--border)',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '0.5rem',
                                            fontSize: '0.8rem',
                                            color: 'var(--text-muted)'
                                        }}>
                                            <RefreshCw size={14} />
                                            Retry attempt #{event.retryCount}
                                        </div>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
