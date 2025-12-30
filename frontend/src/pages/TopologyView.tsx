import { Database, Server, Radio, CheckCircle, XCircle, Circle } from 'lucide-react';
import { SystemHealth, NodeInfo } from '../types';

interface Props {
    health: SystemHealth | null;
}

export function TopologyView({ health }: Props) {
    const mysqlTopology = health?.topologies?.mysql;
    const redisTopology = health?.topologies?.redis;
    const kafkaTopology = health?.topologies?.kafka;

    return (
        <div>
            <div className="header">
                <div>
                    <h1>System Topology</h1>
                    <p style={{ color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                        Visual representation of connected systems and their nodes
                    </p>
                </div>
            </div>

            <div className="grid grid-2" style={{ gap: '2rem' }}>
                {/* MySQL Topology */}
                <TopologyCard
                    title="MySQL"
                    icon={<Database size={24} />}
                    topologyType={mysqlTopology?.topologyType || 'UNKNOWN'}
                    nodes={mysqlTopology?.nodes || []}
                    primaryNode={mysqlTopology?.primaryNode}
                    additionalInfo={mysqlTopology?.additionalInfo}
                    color="#00758f"
                />

                {/* Redis Topology */}
                <TopologyCard
                    title="Redis"
                    icon={<Server size={24} />}
                    topologyType={redisTopology?.topologyType || 'UNKNOWN'}
                    nodes={redisTopology?.nodes || []}
                    primaryNode={redisTopology?.primaryNode}
                    additionalInfo={redisTopology?.additionalInfo}
                    color="#dc382d"
                />

                {/* Kafka Topology */}
                <TopologyCard
                    title="Kafka"
                    icon={<Radio size={24} />}
                    topologyType={kafkaTopology?.topologyType || 'UNKNOWN'}
                    nodes={kafkaTopology?.nodes || []}
                    primaryNode={kafkaTopology?.primaryNode}
                    additionalInfo={kafkaTopology?.additionalInfo}
                    color="#231f20"
                    fullWidth
                />
            </div>
        </div>
    );
}

interface TopologyCardProps {
    title: string;
    icon: React.ReactNode;
    topologyType: string;
    nodes: NodeInfo[];
    primaryNode?: string | null;
    additionalInfo?: string | null;
    color: string;
    fullWidth?: boolean;
}

function TopologyCard({
    title,
    icon,
    topologyType,
    nodes,
    primaryNode,
    additionalInfo,
    color,
    fullWidth
}: TopologyCardProps) {
    const formatTopologyType = (type: string) => {
        return type
            .replace(/_/g, ' ')
            .split(' ')
            .map(word => word.charAt(0) + word.slice(1).toLowerCase())
            .join(' ');
    };

    return (
        <div
            className="card"
            style={{
                gridColumn: fullWidth ? 'span 2' : undefined,
                position: 'relative',
                overflow: 'hidden'
            }}
        >
            {/* Color bar */}
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '3px',
                background: color
            }} />

            <div className="card-header" style={{ marginTop: '0.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <div style={{
                        width: 40,
                        height: 40,
                        borderRadius: 'var(--radius-md)',
                        background: `${color}20`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: color
                    }}>
                        {icon}
                    </div>
                    <div>
                        <h3 style={{ fontSize: '1.125rem', fontWeight: 600 }}>{title}</h3>
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            {formatTopologyType(topologyType)}
                        </span>
                    </div>
                </div>
                <span style={{
                    padding: '0.25rem 0.75rem',
                    background: 'var(--bg-tertiary)',
                    borderRadius: 'var(--radius-full)',
                    fontSize: '0.8rem',
                    fontWeight: 500
                }}>
                    {nodes.length} node{nodes.length !== 1 ? 's' : ''}
                </span>
            </div>

            {additionalInfo && (
                <p style={{
                    color: 'var(--text-muted)',
                    fontSize: '0.85rem',
                    marginBottom: '1rem'
                }}>
                    {additionalInfo}
                </p>
            )}

            {/* Nodes */}
            <div style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: '0.75rem',
                marginTop: '1rem'
            }}>
                {nodes.length === 0 ? (
                    <div className="empty-state" style={{ width: '100%', padding: '2rem' }}>
                        <Circle size={32} />
                        <p>No nodes detected</p>
                    </div>
                ) : (
                    nodes.map((node, index) => (
                        <NodeCard
                            key={node.nodeId || index}
                            node={node}
                            isPrimary={primaryNode === `${node.host}:${node.port}`}
                            color={color}
                        />
                    ))
                )}
            </div>
        </div>
    );
}

interface NodeCardProps {
    node: NodeInfo;
    isPrimary: boolean;
    color: string;
}

function NodeCard({ node, isPrimary, color }: NodeCardProps) {
    const roleColors: Record<string, { bg: string; text: string }> = {
        PRIMARY: { bg: 'var(--success-bg)', text: 'var(--success)' },
        MASTER: { bg: 'var(--success-bg)', text: 'var(--success)' },
        REPLICA: { bg: 'var(--warning-bg)', text: 'var(--warning)' },
        SLAVE: { bg: 'var(--warning-bg)', text: 'var(--warning)' },
        SENTINEL: { bg: 'rgba(139, 92, 246, 0.1)', text: '#8b5cf6' },
        BROKER: { bg: 'var(--bg-tertiary)', text: 'var(--text-secondary)' },
        UNKNOWN: { bg: 'var(--bg-tertiary)', text: 'var(--text-muted)' },
    };

    const roleStyle = roleColors[node.role] || roleColors.UNKNOWN;

    return (
        <div style={{
            background: 'var(--bg-tertiary)',
            border: isPrimary ? `2px solid ${color}` : '1px solid var(--border)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem',
            minWidth: '200px',
            flex: '1 1 200px',
            maxWidth: '300px'
        }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    {node.isHealthy ? (
                        <CheckCircle size={16} style={{ color: 'var(--success)' }} />
                    ) : (
                        <XCircle size={16} style={{ color: 'var(--error)' }} />
                    )}
                    <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                        {node.nodeId.substring(0, 12)}
                    </span>
                </div>
                <span style={{
                    padding: '0.125rem 0.5rem',
                    background: roleStyle.bg,
                    color: roleStyle.text,
                    borderRadius: 'var(--radius-full)',
                    fontSize: '0.7rem',
                    fontWeight: 600,
                    textTransform: 'uppercase'
                }}>
                    {node.role}
                </span>
            </div>

            <div style={{ marginTop: '0.75rem', color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                <div>{node.host}:{node.port}</div>
                {node.latencyMs > 0 && (
                    <div style={{ marginTop: '0.25rem' }}>
                        Latency: {node.latencyMs}ms
                    </div>
                )}
            </div>

            {isPrimary && (
                <div style={{
                    marginTop: '0.5rem',
                    padding: '0.25rem 0.5rem',
                    background: `${color}20`,
                    color: color,
                    borderRadius: 'var(--radius-sm)',
                    fontSize: '0.7rem',
                    fontWeight: 600,
                    textAlign: 'center'
                }}>
                    PRIMARY
                </div>
            )}
        </div>
    );
}
