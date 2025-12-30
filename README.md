# DevOps Control Plane

A production-ready monolithic DevOps control-plane application featuring a React dashboard, Spring Boot backend, and comprehensive connectivity to MySQL, Redis, and Kafka with resilience patterns and observability.

## 🚀 Quick Start

```bash
# Start the full stack
docker-compose up -d

# Wait for services to be healthy (about 60 seconds)
docker-compose ps

# Access the dashboard
open http://localhost:3000
```

## 🏗️ Architecture

```
┌──────────────────────────────────────────────┐
│                React Frontend                │
│  - System Health Dashboard                   │
│  - Topology View                             │
│  - Event Timeline                            │
│  - Live Metrics (WebSocket)                  │
└──────────────────▲───────────────────────────┘
                   │ REST + WebSocket
┌──────────────────┴───────────────────────────┐
│          Java Spring Boot Backend             │
│                                              │
│  API Layer         │  Control Plane Core     │
│  ├── Health        │  ├── Orchestrator       │
│  ├── Metrics       │  ├── TopologyDetector   │
│  └── Actions       │  └── RetryEngine        │
│                                              │
│  Connectors        │  Observability          │
│  ├── MySQL         │  ├── OpenTelemetry      │
│  ├── Redis         │  ├── Prometheus         │
│  └── Kafka         │  └── Structured Logs    │
└──────────────────────────────────────────────┘
```

## 📋 Features

### Connectivity
- **MySQL**: Standalone, Replication, Cluster/Group Replication
- **Redis**: Standalone, Sentinel, Cluster
- **Kafka**: Single and Multi-Broker

### Resilience
- Circuit breakers (Resilience4j)
- Exponential backoff with jitter
- Automatic reconnection
- Graceful degradation

### Observability
- Prometheus metrics via Micrometer
- OpenTelemetry distributed tracing
- Structured JSON logging with correlation IDs
- Grafana dashboards

### Real-Time Dashboard
- System health status (Green/Amber/Red)
- Topology visualization
- Failure event timeline
- Live latency charts

## 🛠️ Development

### Prerequisites
- Java 17+
- Node.js 18+
- Docker & Docker Compose

### Backend (Spring Boot)

```bash
cd backend
./mvnw spring-boot:run
```

Backend runs on http://localhost:8080

### Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on http://localhost:3000

## 🔌 Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/health` | Aggregated system health |
| `GET /api/health/{system}` | System-specific health |
| `GET /api/health/circuit-breakers` | Circuit breaker states |
| `GET /api/metrics/summary` | Key metrics snapshot |
| `GET /api/actions/topology` | All system topologies |
| `POST /api/actions/reconnect/{system}` | Force reconnection |
| `POST /api/actions/refresh-topology` | Refresh topology detection |
| `WS /ws` | WebSocket for real-time updates |

## 🧪 Testing Resilience

### Kill MySQL Primary
```bash
docker-compose stop mysql-primary
# Dashboard shows MySQL status amber/red
# Kafka emits MYSQL_UNAVAILABLE event

docker-compose start mysql-primary
# Dashboard recovers to green
```

### Redis Failover
```bash
docker-compose stop redis-master
# Sentinel promotes replica
# Dashboard shows failover event
```

### Kafka Down
```bash
docker-compose stop kafka
# Events queued locally
# Application continues serving
```

## 📊 Observability URLs

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Actuator | http://localhost:8080/actuator |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 (admin/admin) |

## 📁 Project Structure

```
capstone/
├── backend/
│   ├── src/main/java/com/platform/controlplane/
│   │   ├── api/           # REST controllers
│   │   ├── core/          # Orchestrator, TopologyDetector
│   │   ├── connectors/    # MySQL, Redis, Kafka
│   │   ├── observability/ # Metrics, Tracing, Logging
│   │   └── model/         # DTOs and records
│   └── Dockerfile
├── frontend/
│   ├── src/
│   │   ├── pages/         # Dashboard pages
│   │   ├── hooks/         # useWebSocket, useApi
│   │   └── types.ts       # TypeScript definitions
│   └── Dockerfile
├── config/
│   ├── mysql/             # Primary/replica configs
│   ├── redis/             # Sentinel config
│   ├── prometheus/        # Scrape config
│   └── grafana/           # Provisioning
└── docker-compose.yml
```

## 📝 License

MIT
