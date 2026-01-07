# Control Plane - Feature Matrix

> **This document reflects the actual, implemented capabilities.**  
> Features are marked as ✅ IMPLEMENTED, ⚠️ PARTIAL, or ❌ NOT IMPLEMENTED.

---

## Core Systems

### MySQL Monitoring

| Feature | Status | Notes |
|---------|--------|-------|
| Health check (PING/SELECT 1) | ✅ IMPLEMENTED | Periodic health monitoring |
| Latency tracking | ✅ IMPLEMENTED | P95/P99 metrics |
| Topology detection (standalone) | ✅ IMPLEMENTED | Single node detection |
| Topology detection (replication) | ⚠️ PARTIAL | Basic master/replica detection |
| Topology detection (Group Replication) | ❌ NOT IMPLEMENTED | Requires MySQL GR cluster |
| Automatic failover | ❌ NOT IMPLEMENTED | Read-only detection, no promotion |

### Redis Monitoring

| Feature | Status | Notes |
|---------|--------|-------|
| Health check (PING) | ✅ IMPLEMENTED | Periodic health monitoring |
| Latency tracking | ✅ IMPLEMENTED | P95/P99 metrics |
| Standalone mode | ✅ IMPLEMENTED | Full support |
| Sentinel mode | ⚠️ PARTIAL | Detection only, no failover trigger |
| Cluster mode | ⚠️ PARTIAL | Node discovery, no slot balancing |
| Automatic failover | ❌ NOT IMPLEMENTED | Sentinel/Cluster handles this |

### Kafka Integration

| Feature | Status | Notes |
|---------|--------|-------|
| Event emission | ✅ IMPLEMENTED | Transactional outbox pattern |
| Reliable delivery | ✅ IMPLEMENTED | Retry + DLQ |
| Consumer groups | ❌ NOT IMPLEMENTED | Producer only |
| Partition management | ❌ NOT IMPLEMENTED | Not applicable |

---

## Chaos Engineering

### Fault Injection

| Fault Type | MySQL | Redis | Notes |
|------------|-------|-------|-------|
| CONNECTION_LOSS | ✅ | ✅ | Via Toxiproxy reset_peer |
| LATENCY_INJECTION | ✅ | ✅ | Via Toxiproxy or fallback |
| TIMEOUT | ✅ | ✅ | Via Toxiproxy timeout |
| NETWORK_PARTITION | ✅ | ✅ | Via Toxiproxy (requires container) |
| PARTIAL_FAILURE | ⚠️ | ⚠️ | Application-level only |

### Requirements

| Requirement | Status | Notes |
|-------------|--------|-------|
| Toxiproxy container | **REQUIRED** | For network-level faults |
| Database proxy routing | **REQUIRED** | App must connect via Toxiproxy |
| Fallback mode | ✅ AVAILABLE | Latency injection without Toxiproxy |

---

## Policy Engine

| Feature | Status | Notes |
|---------|--------|-------|
| Policy creation | ✅ IMPLEMENTED | REST API |
| Policy conditions | ⚠️ PARTIAL | State-based only |
| Policy actions | ✅ IMPLEMENTED | 5 action types |
| Periodic evaluation | ✅ IMPLEMENTED | Every 10 seconds |
| Event-triggered evaluation | ✅ IMPLEMENTED | On state change |
| Custom condition expressions | ❌ NOT IMPLEMENTED | Fixed condition types |
| Policy versioning | ❌ NOT IMPLEMENTED | Single version |

### Policy Actions

| Action | Status | Notes |
|--------|--------|-------|
| FORCE_RECONNECT | ✅ IMPLEMENTED | Triggers connector reconnect |
| OPEN_CIRCUIT | ✅ IMPLEMENTED | State transition |
| CLOSE_CIRCUIT | ✅ IMPLEMENTED | State transition |
| EMIT_ALERT | ✅ IMPLEMENTED | Kafka event |
| MARK_DEGRADED | ✅ IMPLEMENTED | State transition |

---

## Observability

| Feature | Status | Notes |
|---------|--------|-------|
| Prometheus metrics | ✅ IMPLEMENTED | /actuator/prometheus |
| Health endpoints | ✅ IMPLEMENTED | /actuator/health |
| Structured logging | ✅ IMPLEMENTED | JSON with traceId |
| OpenTelemetry tracing | ⚠️ PARTIAL | Basic integration |
| Distributed tracing | ⚠️ PARTIAL | Single-service only |
| Custom dashboards | ❌ NOT IMPLEMENTED | Metrics available, no Grafana |

---

## Security

| Feature | Status | Notes |
|---------|--------|-------|
| Input validation | ✅ IMPLEMENTED | @SafeString, DTOs |
| Rate limiting | ✅ IMPLEMENTED | Bucket4j token bucket |
| CORS restriction | ✅ IMPLEMENTED | No wildcards |
| Audit logging | ✅ IMPLEMENTED | Chaos/policy events |
| Authentication | ❌ NOT IMPLEMENTED | No auth layer |
| Authorization (RBAC) | ❌ NOT IMPLEMENTED | No roles |
| API key support | ❌ NOT IMPLEMENTED | - |

---

## WebSocket

| Feature | Status | Notes |
|---------|--------|-------|
| Real-time health updates | ✅ IMPLEMENTED | /topic/health |
| Error handling | ✅ IMPLEMENTED | Safe session close |
| Client notifications | ✅ IMPLEMENTED | Shutdown notices |
| Authentication | ❌ NOT IMPLEMENTED | Open connections |

---

## Persistence

| Feature | Status | Notes |
|---------|--------|-------|
| Chaos experiments | ✅ IMPLEMENTED | MySQL + Flyway |
| Policies | ✅ IMPLEMENTED | MySQL + Flyway |
| State snapshots | ✅ IMPLEMENTED | DesiredSystemState |
| Event outbox | ✅ IMPLEMENTED | Transactional outbox |
| Startup recovery | ✅ IMPLEMENTED | Resume experiments |

---

## Known Limitations

### Toxiproxy Dependency
Network-level chaos (CONNECTION_LOSS, TIMEOUT, NETWORK_PARTITION) **requires** Toxiproxy. Without it:
- These fault types will fail with appropriate error messages
- Only LATENCY_INJECTION and PARTIAL_FAILURE work via fallback

### No Authentication
This is a **back-office control plane**, not a public API. Authentication should be provided by:
- Network isolation (VPC/firewall)
- API Gateway
- Service mesh (Istio, Linkerd)

### Single Instance
The control plane is designed for **single-instance deployment**. For HA:
- Use external load balancer health checks
- Rely on container orchestrator (Kubernetes) for restarts
- State is persisted to MySQL for recovery

---

## API Endpoints Summary

| Category | Endpoint | Method | Implemented |
|----------|----------|--------|-------------|
| Health | /api/health | GET | ✅ |
| Health | /api/health/{system} | GET | ✅ |
| Chaos | /api/chaos/inject | POST | ✅ |
| Chaos | /api/chaos/recover/{id} | POST | ✅ |
| Chaos | /api/chaos/experiments | GET | ✅ |
| Policy | /api/policies | GET, POST | ✅ |
| Policy | /api/policies/{id} | GET, PUT, DELETE | ✅ |
| Policy | /api/policies/executions | GET | ✅ |
| Policy | /api/policies/scheduler/stats | GET | ✅ |
| Lifecycle | /api/lifecycle/ready | GET | ✅ |
| Lifecycle | /api/lifecycle/live | GET | ✅ |
| Metrics | /actuator/prometheus | GET | ✅ |
