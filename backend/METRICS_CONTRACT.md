# Control Plane Metrics Contract

> **Every metric has an owner and purpose. No ad-hoc metrics allowed.**

---

## Naming Convention

```
controlplane_{subsystem}_{metric}_{unit}
```

Examples:
- `controlplane_http_request_duration_seconds`
- `controlplane_chaos_injection_total`
- `controlplane_policy_evaluation_duration_seconds`

### Rules

| Rule | Example |
|------|---------|
| Lowercase with underscores | `request_duration` ✓ |
| Units as suffix | `_seconds`, `_bytes`, `_total` |
| Counters end with `_total` | `requests_total` |
| Use base units | seconds (not ms), bytes (not KB) |

---

## Banned Labels (High Cardinality)

> ❌ **NEVER use these as metric labels**

| Banned Label | Reason | Alternative |
|--------------|--------|-------------|
| `user_id` | Unbounded | Use logs with trace_id |
| `request_id` | Unique per request | Use trace_id in logs |
| `trace_id` | Unique per request | Logs only |
| `experiment_id` | Too many values | Use `system_type` |
| `session_id` | Unbounded | None |
| `ip_address` | Unbounded | Aggregate by CIDR block |
| `error_message` | High variance | Use `error_code` |

---

## Allowed Labels

| Label | Cardinality | Values |
|-------|-------------|--------|
| `system_type` | 3 | mysql, redis, kafka |
| `fault_type` | 5 | CONNECTION_LOSS, LATENCY_INJECTION, ... |
| `status` | 3 | success, failure, timeout |
| `method` | 5 | GET, POST, PUT, DELETE, PATCH |
| `endpoint` | ~15 | Normalized paths only |
| `policy_action` | 5 | FORCE_RECONNECT, OPEN_CIRCUIT, ... |
| `environment` | 3 | dev, staging, prod |

---

## Metrics Catalog

### HTTP (RED Metrics)

| Metric | Type | Labels | Owner | Purpose |
|--------|------|--------|-------|---------|
| `controlplane_http_requests_total` | Counter | method, endpoint, status | Platform | Request **Rate** |
| `controlplane_http_request_duration_seconds` | Histogram | method, endpoint | Platform | **Duration** (latency) |
| `controlplane_http_request_errors_total` | Counter | method, endpoint, error_code | Platform | **Error** rate |

### Chaos Engineering (RED)

| Metric | Type | Labels | Owner | Purpose |
|--------|------|--------|-------|---------|
| `controlplane_chaos_injection_total` | Counter | system_type, fault_type, status | Chaos | Injection **Rate** |
| `controlplane_chaos_injection_duration_seconds` | Histogram | system_type, fault_type | Chaos | Injection **Duration** |
| `controlplane_chaos_recovery_total` | Counter | system_type, status | Chaos | Recovery attempts |
| `controlplane_chaos_recovery_duration_seconds` | Histogram | system_type | Chaos | Recovery **Duration** |
| `controlplane_chaos_active_experiments` | Gauge | system_type | Chaos | Currently running |

### Policy Engine (RED)

| Metric | Type | Labels | Owner | Purpose |
|--------|------|--------|-------|---------|
| `controlplane_policy_evaluations_total` | Counter | system_type | Policy | Evaluation **Rate** |
| `controlplane_policy_evaluation_duration_seconds` | Histogram | system_type | Policy | **Duration** |
| `controlplane_policy_triggers_total` | Counter | system_type, policy_action | Policy | Trigger **Rate** |
| `controlplane_policy_action_errors_total` | Counter | system_type, policy_action | Policy | **Errors** |
| `controlplane_policy_active_count` | Gauge | system_type | Policy | Enabled policies |

### Connectors (USE Metrics)

| Metric | Type | Labels | Owner | Purpose |
|--------|------|--------|-------|---------|
| `controlplane_connector_pool_utilization` | Gauge | system_type | Infra | **Utilization** |
| `controlplane_connector_pool_saturation` | Gauge | system_type | Infra | **Saturation** (queue depth) |
| `controlplane_connector_errors_total` | Counter | system_type, error_code | Infra | **Errors** |
| `controlplane_connector_latency_seconds` | Histogram | system_type | Infra | Health check latency |
| `controlplane_connector_reconnects_total` | Counter | system_type | Infra | Reconnection attempts |

### Kafka Outbox (USE)

| Metric | Type | Labels | Owner | Purpose |
|--------|------|--------|-------|---------|
| `controlplane_kafka_outbox_pending` | Gauge | - | Messaging | **Saturation** |
| `controlplane_kafka_outbox_dlq` | Counter | - | Messaging | **Errors** (dead letters) |
| `controlplane_kafka_dispatch_duration_seconds` | Histogram | - | Messaging | Dispatch latency |
| `controlplane_kafka_dispatch_total` | Counter | status | Messaging | **Rate** |

### Security (RED)

| Metric | Type | Labels | Owner | Purpose |
|--------|------|--------|-------|---------|
| `controlplane_ratelimit_requests_total` | Counter | bucket_type, blocked | Security | **Rate** |
| `controlplane_ratelimit_blocked_total` | Counter | bucket_type | Security | **Errors** (blocks) |
| `controlplane_validation_failures_total` | Counter | field | Security | Input rejections |

---

## SLI Definitions

### SLI 1: API Availability

**Definition:** Proportion of successful HTTP requests

```promql
sum(rate(controlplane_http_requests_total{status="success"}[5m]))
/
sum(rate(controlplane_http_requests_total[5m]))
```

**Target:** 99.9% over 30-day window

**Owner:** Platform Team

---

### SLI 2: Chaos Recovery Success Rate

**Definition:** Proportion of recoveries that succeed

```promql
sum(rate(controlplane_chaos_recovery_total{status="success"}[1h]))
/
sum(rate(controlplane_chaos_recovery_total[1h]))
```

**Target:** 99% over 7-day window

**Owner:** Chaos Team

---

### SLI 3: Policy Execution Latency

**Definition:** 95th percentile of policy evaluation duration

```promql
histogram_quantile(0.95, 
  rate(controlplane_policy_evaluation_duration_seconds_bucket[5m])
)
```

**Target:** < 100ms (p95)

**Owner:** Policy Team

---

### SLI 4: Connector Health

**Definition:** Proportion of healthy connector checks

```promql
sum(rate(controlplane_connector_errors_total[5m])) 
/ 
sum(rate(controlplane_connector_latency_seconds_count[5m]))
```

**Target:** < 1% error rate

**Owner:** Infra Team

---

## Metric Ownership

| Owner | Subsystems | Responsibility |
|-------|------------|----------------|
| **Platform** | HTTP, lifecycle | API health, latency |
| **Chaos** | Chaos injection/recovery | Experiment reliability |
| **Policy** | Policy engine | Evaluation performance |
| **Infra** | Connectors, Kafka | Resource health |
| **Security** | Rate limiting, validation | Threat detection |

---

## Anti-Patterns (Forbidden)

| ❌ Bad | ✓ Good | Reason |
|--------|--------|--------|
| `requests{user_id="123"}` | `requests{endpoint="/api/chaos"}` | User ID is unbounded |
| `errors{message="..."}` | `errors{error_code="CP-500"}` | Error messages have high cardinality |
| `chaos_duration_ms` | `chaos_duration_seconds` | Use base units |
| `my_custom_metric` | `controlplane_subsystem_metric_unit` | Follow naming convention |
| Logging to metrics | Use structured logs | Metrics are aggregates |

---

## Adding New Metrics

1. Check if existing metric covers the use case
2. Follow naming convention: `controlplane_{subsystem}_{metric}_{unit}`
3. Use only allowed labels
4. Document owner and purpose
5. Add to this catalog
6. Review with Platform team
