import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import {
    Activity,
    Network,
    AlertTriangle,
    BarChart3,
    RefreshCw,
    Settings,
} from 'lucide-react';
import { useWebSocket } from './hooks/useWebSocket';
import { SystemHealthDashboard } from './pages/SystemHealthDashboard';
import { TopologyView } from './pages/TopologyView';
import { FailureTimeline } from './pages/FailureTimeline';
import { LiveMetrics } from './pages/LiveMetrics';

function App() {
    const { isConnected, health, events, requestRefresh } = useWebSocket();

    return (
        <BrowserRouter>
            <div className="app-container">
                {/* Sidebar */}
                <aside className="sidebar">
                    <div style={{ marginBottom: '2rem' }}>
                        <h1 style={{
                            fontSize: '1.25rem',
                            fontWeight: 700,
                            background: 'var(--accent-gradient)',
                            WebkitBackgroundClip: 'text',
                            WebkitTextFillColor: 'transparent',
                            marginBottom: '0.5rem'
                        }}>
                            DevOps Control Plane
                        </h1>
                        <div className="connection-indicator">
                            <span className={`connection-dot ${isConnected ? 'connected' : ''}`} />
                            {isConnected ? 'Connected' : 'Disconnected'}
                        </div>
                    </div>

                    <nav>
                        <NavLink
                            to="/"
                            className={({ isActive }) =>
                                `nav-item ${isActive ? 'active' : ''}`
                            }
                        >
                            <Activity size={20} />
                            System Health
                        </NavLink>
                        <NavLink
                            to="/topology"
                            className={({ isActive }) =>
                                `nav-item ${isActive ? 'active' : ''}`
                            }
                        >
                            <Network size={20} />
                            Topology
                        </NavLink>
                        <NavLink
                            to="/events"
                            className={({ isActive }) =>
                                `nav-item ${isActive ? 'active' : ''}`
                            }
                        >
                            <AlertTriangle size={20} />
                            Events
                            {events.length > 0 && (
                                <span style={{
                                    marginLeft: 'auto',
                                    background: 'var(--error)',
                                    color: 'white',
                                    padding: '0.125rem 0.5rem',
                                    borderRadius: 'var(--radius-full)',
                                    fontSize: '0.75rem',
                                    fontWeight: 600,
                                }}>
                                    {events.length}
                                </span>
                            )}
                        </NavLink>
                        <NavLink
                            to="/metrics"
                            className={({ isActive }) =>
                                `nav-item ${isActive ? 'active' : ''}`
                            }
                        >
                            <BarChart3 size={20} />
                            Live Metrics
                        </NavLink>
                    </nav>

                    <div style={{ marginTop: 'auto', paddingTop: '2rem' }}>
                        <button
                            className="btn btn-secondary"
                            onClick={requestRefresh}
                            style={{ width: '100%' }}
                            disabled={!isConnected}
                        >
                            <RefreshCw size={16} />
                            Refresh All
                        </button>
                    </div>
                </aside>

                {/* Main Content */}
                <main className="main-content">
                    <Routes>
                        <Route
                            path="/"
                            element={<SystemHealthDashboard health={health} isConnected={isConnected} />}
                        />
                        <Route
                            path="/topology"
                            element={<TopologyView health={health} />}
                        />
                        <Route
                            path="/events"
                            element={<FailureTimeline events={events} />}
                        />
                        <Route path="/metrics" element={<LiveMetrics health={health} />} />
                    </Routes>
                </main>
            </div>
        </BrowserRouter>
    );
}

export default App;
