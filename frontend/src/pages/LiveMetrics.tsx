import { useEffect, useState } from 'react';
import {
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    AreaChart,
    Area,
} from 'recharts';
import { Activity, Clock, TrendingUp } from 'lucide-react';
import { SystemHealth } from '../types';
import { useApi } from '../hooks/useApi';

interface Props {
    health: SystemHealth | null;
}

interface LatencyDataPoint {
    time: string;
    mysql: number;
    redis: number;
    kafka: number;
}

export function LiveMetrics({ health }: Props) {
    const [latencyHistory, setLatencyHistory] = useState<LatencyDataPoint[]>([]);
    const { getLatencies } = useApi();

    // Update latency history when health changes
    useEffect(() => {
        if (!health) return;

        const newPoint: LatencyDataPoint = {
            time: new Date().toLocaleTimeString(),
            mysql: health.connectionStatuses?.mysql?.latencyMs ?? 0,
            redis: health.connectionStatuses?.redis?.latencyMs ?? 0,
            kafka: 0,
        };

        setLatencyHistory((prev) => {
            const updated = [...prev, newPoint];
            return updated.slice(-30);
        });
    }, [health?.lastUpdated]);

    // Also poll for metrics
    useEffect(() => {
        const interval = setInterval(async () => {
            try {
                const latencies = await getLatencies();
                const newPoint: LatencyDataPoint = {
                    time: new Date().toLocaleTimeString(),
                    mysql: latencies.mysql ?? 0,
                    redis: latencies.redis ?? 0,
                    kafka: latencies.kafka ?? 0,
                };

                setLatencyHistory((prev) => {
                    const updated = [...prev, newPoint];
                    return updated.slice(-30);
                });
            } catch {
                // Ignore errors
            }
        }, 5000);

        return () => clearInterval(interval);
    }, [getLatencies]);

    const mysqlStatus = health?.connectionStatuses?.mysql;
    const redisStatus = health?.connectionStatuses?.redis;
    const kafkaStatus = health?.connectionStatuses?.kafka;

    return (
        <div>
            <div className="header">
                <div>
                    <h1>Live Metrics</h1>
                    <p style={{ color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                        Real-time performance metrics and latency trends
                    </p>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Activity size={16} style={{ color: 'var(--success)' }} />
                    <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                        Updating every 10s
                    </span>
                </div>
            </div>

            {/* Current Latencies */}
            <div className="grid grid-3" style={{ marginBottom: '2rem' }}>
                <MetricCard
                    label="MySQL Latency"
                    value={mysqlStatus?.latencyMs ?? -1}
                    unit="ms"
                    trend={calculateTrend(latencyHistory, 'mysql')}
                    color="#00758f"
                />
                <MetricCard
                    label="Redis Latency"
                    value={redisStatus?.latencyMs ?? -1}
                    unit="ms"
                    trend={calculateTrend(latencyHistory, 'redis')}
                    color="#dc382d"
                />
                <MetricCard
                    label="Kafka Latency"
                    value={kafkaStatus?.latencyMs ?? -1}
                    unit="ms"
                    trend={calculateTrend(latencyHistory, 'kafka')}
                    color="#4a4a4a"
                />
            </div>

            {/* Latency Chart */}
            <div className="card" style={{ marginBottom: '2rem' }}>
                <div className="card-header">
                    <h3 className="card-title">Latency Over Time</h3>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.8rem' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                            <span style={{ width: 12, height: 3, background: '#00758f', borderRadius: 2 }} />
                            MySQL
                        </span>
                        <span style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                            <span style={{ width: 12, height: 3, background: '#dc382d', borderRadius: 2 }} />
                            Redis
                        </span>
                    </div>
                </div>
                <div className="chart-container">
                    {latencyHistory.length > 0 ? (
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={latencyHistory}>
                                <defs>
                                    <linearGradient id="mysqlGradient" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#00758f" stopOpacity={0.3} />
                                        <stop offset="95%" stopColor="#00758f" stopOpacity={0} />
                                    </linearGradient>
                                    <linearGradient id="redisGradient" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#dc382d" stopOpacity={0.3} />
                                        <stop offset="95%" stopColor="#dc382d" stopOpacity={0} />
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                <XAxis
                                    dataKey="time"
                                    stroke="var(--text-muted)"
                                    fontSize={12}
                                    tickLine={false}
                                />
                                <YAxis
                                    stroke="var(--text-muted)"
                                    fontSize={12}
                                    tickLine={false}
                                    axisLine={false}
                                    tickFormatter={(value) => `${value}ms`}
                                />
                                <Tooltip
                                    contentStyle={{
                                        background: 'var(--bg-card)',
                                        border: '1px solid var(--border)',
                                        borderRadius: 'var(--radius-md)',
                                    }}
                                    labelStyle={{ color: 'var(--text-primary)' }}
                                />
                                <Area
                                    type="monotone"
                                    dataKey="mysql"
                                    stroke="#00758f"
                                    strokeWidth={2}
                                    fill="url(#mysqlGradient)"
                                    dot={false}
                                />
                                <Area
                                    type="monotone"
                                    dataKey="redis"
                                    stroke="#dc382d"
                                    strokeWidth={2}
                                    fill="url(#redisGradient)"
                                    dot={false}
                                />
                            </AreaChart>
                        </ResponsiveContainer>
                    ) : (
                        <div className="empty-state">
                            <Clock size={32} />
                            <p>Collecting latency data...</p>
                        </div>
                    )}
                </div>
            </div>

            {/* Connection Stats */}
            <div className="grid grid-2">
                <div className="card">
                    <h3 className="card-title" style={{ marginBottom: '1rem' }}>MySQL Connections</h3>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <div>
                            <div className="metric-label">Active</div>
                            <div className="metric-value" style={{ fontSize: '1.5rem' }}>
                                {mysqlStatus?.activeConnections ?? 0}
                            </div>
                        </div>
                        <div>
                            <div className="metric-label">Max</div>
                            <div className="metric-value" style={{ fontSize: '1.5rem' }}>
                                {mysqlStatus?.maxConnections ?? 0}
                            </div>
                        </div>
                        <div>
                            <div className="metric-label">Utilization</div>
                            <div className="metric-value" style={{ fontSize: '1.5rem' }}>
                                {mysqlStatus?.maxConnections
                                    ? Math.round((mysqlStatus.activeConnections / mysqlStatus.maxConnections) * 100)
                                    : 0}%
                            </div>
                        </div>
                    </div>
                </div>

                <div className="card">
                    <h3 className="card-title" style={{ marginBottom: '1rem' }}>Redis Connections</h3>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <div>
                            <div className="metric-label">Active</div>
                            <div className="metric-value" style={{ fontSize: '1.5rem' }}>
                                {redisStatus?.activeConnections ?? 0}
                            </div>
                        </div>
                        <div>
                            <div className="metric-label">Max</div>
                            <div className="metric-value" style={{ fontSize: '1.5rem' }}>
                                {redisStatus?.maxConnections ?? 0}
                            </div>
                        </div>
                        <div>
                            <div className="metric-label">Status</div>
                            <div className="metric-value" style={{
                                fontSize: '1rem',
                                color: redisStatus?.status === 'UP' ? 'var(--success)' : 'var(--error)'
                            }}>
                                {redisStatus?.status ?? 'UNKNOWN'}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

interface MetricCardProps {
    label: string;
    value: number;
    unit: string;
    trend: 'up' | 'down' | 'stable';
    color: string;
}

function MetricCard({ label, value, unit, trend, color }: MetricCardProps) {
    const trendColor = trend === 'up' ? 'var(--warning)' : trend === 'down' ? 'var(--success)' : 'var(--text-muted)';

    return (
        <div className="card" style={{ position: 'relative', overflow: 'hidden' }}>
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '3px',
                background: color
            }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginTop: '0.5rem' }}>
                <div className="metric-label">{label}</div>
                {trend !== 'stable' && (
                    <TrendingUp
                        size={16}
                        style={{
                            color: trendColor,
                            transform: trend === 'down' ? 'rotate(180deg)' : 'none'
                        }}
                    />
                )}
            </div>
            <div className="metric-value" style={{ marginTop: '0.5rem' }}>
                {value >= 0 ? value : '-'}
                {value >= 0 && <span style={{ fontSize: '1rem', color: 'var(--text-muted)' }}>{unit}</span>}
            </div>
        </div>
    );
}

function calculateTrend(history: LatencyDataPoint[], system: keyof Omit<LatencyDataPoint, 'time'>): 'up' | 'down' | 'stable' {
    if (history.length < 3) return 'stable';

    const recent = history.slice(-3);
    const first = recent[0][system];
    const last = recent[recent.length - 1][system];

    const diff = last - first;
    const threshold = first * 0.1;

    if (diff > threshold) return 'up';
    if (diff < -threshold) return 'down';
    return 'stable';
}
