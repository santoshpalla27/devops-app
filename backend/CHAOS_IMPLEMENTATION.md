# Chaos Engineering - Fault Injection Implementation Guide

## Overview

This document describes the real fault injection implementations for the DevOps Control Plane chaos engineering system.

**Hard Rule**: If a fault cannot be injected realistically, the feature is not available.

---

## Supported Fault Types

### 1. CONNECTION_LOSS ✅

**Implementation**: Connector disconnect + Toxiproxy proxy disable

**Observable Effect**:
- All queries immediately fail with connection errors
- State transitions to `DISCONNECTED`
- Health checks fail

**Recovery**: Re-enable proxy, reconnect connector

**Metrics**:
```promql
controlplane_system_state{system="mysql", state="DISCONNECTED"} == 1
```

---

### 2. LATENCY_INJECTION ✅

**Implementation**: 
- **Primary**: Toxiproxy `latency` toxic (network-level delay)
- **Fallback**: Thread.sleep before query execution (JVM-only)

**Observable Effect**:
- All queries delayed by configured milliseconds
- Query duration metrics spike
- May trigger circuit breaker if latency exceeds thresholds

**Configuration**:
```json
{
  "faultType": "LATENCY_INJECTION",
  "latencyMs": 2000,
  "durationSeconds": 300
}
```

**Metrics**:
```promql
controlplane_connection_latency{system="mysql"} > 2000
```

**Limitations**:
- Fallback (Thread.sleep) only affects JVM, not external clients
- Toxiproxy required for realistic network-level latency

---

### 3. PARTIAL_FAILURE ✅

**Implementation**: Application-level interceptor with random failure

**Observable Effect**:
- Configured percentage of requests throw `ChaosInducedFailureException`
- Error rate metrics increase proportionally

**Configuration**:
```json
{
  "faultType": "PARTIAL_FAILURE",
  "failureRatePercent": 50,
  "durationSeconds": 300
}
```

**Metrics**:
```promql
rate(controlplane_connection_failures_total{system="mysql"}[1m]) > 0
```

**Limitations**:
- Only affects requests through control plane, not direct DB connections
- Requires integration with query interceptor

---

### 4. TIMEOUT ✅

**Implementation**: Toxiproxy `timeout` toxic

**Observable Effect**:
- All connections hang and eventually timeout
- Queries never complete
- Connection pool exhaustion may occur

**Configuration**:
```json
{
  "faultType": "TIMEOUT",
  "durationSeconds": 60
}
```

**Metrics**:
```promql
controlplane_connection_timeouts_total{system="mysql"} > 0
```

**Limitations**:
- **Requires Toxiproxy** - will fail if Toxiproxy unavailable
- No fallback implementation

---

### 5. NETWORK_PARTITION ✅

**Implementation**: Toxiproxy `reset_peer` toxic

**Observable Effect**:
- All connections receive TCP RST immediately
- State transitions to `DISCONNECTED`
- Simulates network split between application and database

**Configuration**:
```json
{
  "faultType": "NETWORK_PARTITION",
  "durationSeconds": 120
}
```

**Metrics**:
```promql
controlplane_system_state{system="mysql", state="DISCONNECTED"} == 1
```

**Limitations**:
- **Requires Toxiproxy** - will fail if Toxiproxy unavailable
- No fallback implementation

---

## Removed Fault Types

### CIRCUIT_BREAKER_FORCE_OPEN ❌ REMOVED

**Reason**: Not a real fault. Circuit breakers should open in response to actual failures, not artificial state changes.

**Alternative**: Use `LATENCY_INJECTION` or `PARTIAL_FAILURE` to trigger circuit breaker naturally.

---

## Infrastructure Requirements

### Toxiproxy Setup

Toxiproxy must be running and configured for network-level faults:

```yaml
# docker-compose.yml
toxiproxy:
  image: ghcr.io/shopify/toxiproxy:2.7.0
  ports:
    - "8474:8474"   # API
    - "13306:13306" # MySQL proxy
    - "16379:16379" # Redis proxy
```

### Proxy Configuration

Proxies are auto-created on startup:
- `mysql-proxy`: `0.0.0.0:13306` → `mysql-primary:3306`
- `redis-proxy`: `0.0.0.0:16379` → `redis-master:6379`

### Connecting Through Proxies

For full chaos support, configure applications to connect through Toxiproxy:

```yaml
spring:
  datasource:
    url: jdbc:mysql://toxiproxy:13306/controlplane
  data:
    redis:
      host: toxiproxy
      port: 16379
```

---

## Observability

### Metrics

Each fault injection produces metrics:

```promql
# Faults injected
chaos_fault_injected{system="mysql", type="LATENCY_INJECTION"}

# Faults recovered
chaos_fault_recovered{system="mysql", type="LATENCY_INJECTION"}

# Toxiproxy toxics
chaos_toxic_added{proxy="mysql-proxy", type="latency"}
chaos_toxic_removed{proxy="mysql-proxy"}
```

### Logging

All fault operations are logged with:
- Experiment ID
- System type
- Fault type
- Duration

MDC fields for correlation:
- `experimentId`
- `systemType`

---

## Cleanup & Recovery

### Deterministic Cleanup

Every fault injection has a corresponding recovery method:

| Fault Type | Cleanup Actions |
|------------|-----------------|
| CONNECTION_LOSS | Enable proxy, reconnect |
| LATENCY_INJECTION | Remove toxic, clear app-level state |
| PARTIAL_FAILURE | Reset failure rate to 0% |
| TIMEOUT | Remove toxic, reconnect |
| NETWORK_PARTITION | Remove toxic, reconnect |

### Auto-Recovery

Experiments with `durationSeconds > 0` auto-terminate:
1. Scheduled termination stored in database (`scheduled_end_at`)
2. Recovery service reschedules on application restart
3. Fault cleanup guaranteed even after restart

### Manual Cleanup

```bash
# Stop experiment
POST /api/chaos/stop/{experimentId}

# Force cleanup all Toxiproxy toxics for an experiment
DELETE /api/chaos/experiments/{experimentId}/toxics
```

---

## Error Handling

### Toxiproxy Unavailable

| Fault Type | Behavior |
|------------|----------|
| LATENCY_INJECTION | Falls back to Thread.sleep |
| PARTIAL_FAILURE | Works (app-level only) |
| TIMEOUT | **Fails with error** |
| NETWORK_PARTITION | **Fails with error** |

### Clear Error Messages

When a fault cannot be injected, clear error messages are returned:

```json
{
  "status": "FAILED",
  "result": "Cannot inject TIMEOUT: Toxiproxy is required but not available"
}
```
