# Control Plane - API Documentation

> **Accurate documentation of all REST endpoints.**

---

## Base URL

```
http://localhost:8080
```

---

## Health Endpoints

### GET /api/health

Get overall system health.

**Response:**
```json
{
  "status": "HEALTHY",
  "systems": {
    "mysql": { "status": "CONNECTED", "latencyMs": 5 },
    "redis": { "status": "CONNECTED", "latencyMs": 2 },
    "kafka": { "status": "CONNECTED" }
  },
  "timestamp": "2026-01-06T16:20:00Z"
}
```

### GET /api/health/{system}

Get health for a specific system (mysql, redis, kafka).

---

## Chaos Engineering Endpoints

> ⚠️ **Rate Limited**: 10 requests/minute for mutations

### POST /api/chaos/inject

Inject a fault into a system.

**Request:**
```json
{
  "systemType": "mysql",       // Required: mysql, redis, kafka
  "faultType": "LATENCY_INJECTION",
  "durationSeconds": 30,       // 1-3600
  "intensity": 50              // 0-100
}
```

**Response (201):**
```json
{
  "experimentId": "exp-abc123",
  "status": "ACTIVE",
  "faultType": "LATENCY_INJECTION",
  "startedAt": "2026-01-06T16:20:00Z",
  "scheduledEndAt": "2026-01-06T16:20:30Z"
}
```

**Error Responses:**
- `400` - Invalid request (validation failed)
- `429` - Rate limit exceeded
- `500` - Injection failed (Toxiproxy unavailable)

### POST /api/chaos/recover/{experimentId}

Stop a running chaos experiment.

**Response (200):**
```json
{
  "experimentId": "exp-abc123",
  "status": "RECOVERED",
  "recoveredAt": "2026-01-06T16:20:15Z"
}
```

### GET /api/chaos/experiments

List all chaos experiments.

**Query Parameters:**
- `status` - Filter by status (ACTIVE, COMPLETED, RECOVERED, FAILED)
- `system` - Filter by system type
- `limit` - Max results (default: 100)

---

## Policy Endpoints

> ⚠️ **Rate Limited**: 30 requests/minute for mutations

### GET /api/policies

List all policies.

### POST /api/policies

Create a new policy.

**Request:**
```json
{
  "name": "Auto-reconnect MySQL",
  "systemType": "mysql",
  "condition": {
    "type": "STATE_EQUALS",
    "state": "DISCONNECTED"
  },
  "action": "FORCE_RECONNECT",
  "cooldownSeconds": 60,
  "enabled": true
}
```

### GET /api/policies/{id}

Get policy by ID.

### PUT /api/policies/{id}

Update policy.

### DELETE /api/policies/{id}

Delete policy.

### PATCH /api/policies/{id}/enabled

Enable/disable policy.

**Query Parameters:**
- `enabled` - true or false

### GET /api/policies/executions

Get policy execution history (audit log).

**Query Parameters:**
- `systemType` - Filter by system
- `policyId` - Filter by policy
- `limit` - Max results (default: 100)

### GET /api/policies/scheduler/stats

Get scheduler statistics.

**Response:**
```json
{
  "enabled": true,
  "evaluationCycles": 1420,
  "policiesTriggered": 35,
  "failures": 0,
  "lastEvaluation": "2026-01-06T16:20:00Z",
  "totalPolicies": 5
}
```

### POST /api/policies/scheduler/trigger

Manually trigger policy evaluation.

---

## Lifecycle Endpoints

### GET /api/lifecycle/ready

Kubernetes readiness probe.

**Response (200):**
```json
{ "status": "ready", "phase": "READY" }
```

**Response (503):**
```json
{ "status": "not_ready", "phase": "DRAINING" }
```

### GET /api/lifecycle/live

Kubernetes liveness probe.

### GET /api/lifecycle/status

Full lifecycle status.

**Response:**
```json
{
  "phase": "READY",
  "phaseStartTime": "2026-01-06T16:00:00Z",
  "durationMs": 1200000
}
```

---

## Error Response Format

All errors return this structure:

```json
{
  "code": "CP-300",
  "message": "Resource not found",
  "detail": "Policy not found: abc-123",
  "fatal": false,
  "status": 404,
  "timestamp": "2026-01-06T16:20:00Z",
  "path": "/api/policies/abc-123",
  "traceId": "a1b2c3d4",
  "fieldErrors": null,
  "metadata": null
}
```

### Error Codes

| Code | Description | Fatal |
|------|-------------|-------|
| CP-100 | Validation error | No |
| CP-300 | Resource not found | No |
| CP-312 | Concurrent modification | No |
| CP-400 | Database error | Yes |
| CP-429 | Rate limit exceeded | No |
| CP-500 | Chaos operation failed | No |
| CP-901 | Unexpected error | Yes |

---

## WebSocket

### Endpoint

```
ws://localhost:8080/ws
```

### Topics

| Topic | Description |
|-------|-------------|
| /topic/health | Real-time health updates |
| /topic/system | System notifications (shutdown, etc.) |

### Messages

**Health Update:**
```json
{
  "type": "HEALTH_UPDATE",
  "systems": { ... },
  "timestamp": "2026-01-06T16:20:00Z"
}
```

**Shutdown Notification:**
```json
{
  "type": "SERVER_SHUTDOWN",
  "message": "Server is shutting down for maintenance",
  "gracePeriodSeconds": 30
}
```

---

## Authentication

> ⚠️ **NOT IMPLEMENTED**
> 
> This control plane does not include authentication.
> Secure access using:
> - Network isolation (VPC)
> - API Gateway
> - Service mesh
