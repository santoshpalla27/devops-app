# Service Level Objectives (SLOs)

> Alerts that are rare, actionable, and trusted.

---

## SLO 1: API Availability

| Attribute | Value |
|-----------|-------|
| **Definition** | Proportion of HTTP requests that succeed |
| **Metric** | `controlplane_http_requests_total{status="success"}` |
| **Target** | 99.9% over 30 days |
| **Error Budget** | 43.2 minutes/month |
| **Window** | Rolling 30 days |

### Burn Rate Alerts

| Alert | Burn Rate | Window | Page? |
|-------|-----------|--------|-------|
| AvailabilityBurnCritical | 14.4x | 1h | Yes |
| AvailabilityBurnHigh | 6x | 6h | Yes |
| AvailabilityBurnModerate | 1x | 3d | No (ticket) |

---

## SLO 2: Chaos Recovery Time

| Attribute | Value |
|-----------|-------|
| **Definition** | Proportion of chaos recoveries completing within 60s |
| **Metric** | `controlplane_chaos_recovery_duration_seconds` |
| **Target** | 99% within 60 seconds |
| **Window** | Rolling 7 days |

### Burn Rate Alerts

| Alert | Burn Rate | Window | Page? |
|-------|-----------|--------|-------|
| RecoveryBurnCritical | 14.4x | 1h | Yes |
| RecoveryBurnHigh | 6x | 6h | Yes |

---

## SLO 3: Policy Evaluation Latency

| Attribute | Value |
|-----------|-------|
| **Definition** | P95 policy evaluation latency |
| **Metric** | `controlplane_policy_evaluation_duration_seconds` |
| **Target** | < 100ms (P95) |
| **Window** | Rolling 7 days |

### Burn Rate Alerts

| Alert | Condition | Page? |
|-------|-----------|-------|
| PolicyLatencyBurn | P95 > 100ms for 5m | No (ticket) |

---

## Alerting Philosophy

### ✅ DO Alert On
- SLO burn rate (error budget consumption)
- Service unavailability
- Recovery failures

### ❌ DO NOT Alert On
- CPU/Memory (use dashboards instead)
- Every single error
- Metrics during known maintenance

---

## Error Budget Policy

| Budget Remaining | Action |
|------------------|--------|
| > 50% | Normal operations |
| 25-50% | Increase review of changes |
| 10-25% | Freeze non-critical changes |
| < 10% | Freeze all changes, focus on reliability |

---

## Alert Requirements

Every alert MUST include:
1. **Runbook link** - Immediate remediation steps
2. **Dashboard link** - Visualization of the issue
3. **SLO context** - How much budget is being burned
