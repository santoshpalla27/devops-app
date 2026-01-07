# Kibana Query Documentation

## Overview

This document provides common queries for incident investigation. Each query is designed to answer a specific operational question.

---

## Chaos Impact Investigation

### Q: What experiments ran in the last hour?

```kql
event_type: CHAOS_INJECTION_STARTED
```

**Timeframe:** Last 1 hour

---

### Q: Did experiment X cause errors?

```kql
chaos_experiment_id: "exp-abc123"
```

Then correlate with application errors using `trace_id`.

---

### Q: What's the average recovery time by system?

```kql
event_type: CHAOS_RECOVERY_COMPLETED
```

**Aggregation:** Average of `duration_ms` grouped by `system_type`

---

### Q: Which fault types have the highest failure rate?

```kql
event_type: (CHAOS_INJECTION_COMPLETED OR CHAOS_INJECTION_FAILED)
```

**Aggregation:** Count by `fault_type` split by `success`

---

## Policy Investigation

### Q: Why did policy X trigger?

```kql
event_type: POLICY_TRIGGERED AND policy_id: "policy-123"
```

---

### Q: Which policies are failing?

```kql
event_type: POLICY_ACTION_EXECUTED AND success: false
```

---

### Q: What actions were taken for system Y?

```kql
event_type: POLICY_ACTION_EXECUTED AND system_type: "mysql"
```

---

### Q: Policy evaluation throughput?

```kql
event_type: POLICY_EVALUATION_COMPLETED
```

**Aggregation:** Sum of `policies_evaluated` over time

---

## Security Investigation

### Q: Who triggered chaos in the last 24h?

```kql
event_type: CHAOS_INJECTION_STARTED
```

**Group by:** `actor`

---

### Q: Which IPs are being rate limited?

```kql
event_type: SECURITY_RATE_LIMITED
```

**Group by:** `client_ip`

---

### Q: What policy changes were made?

```kql
event_type: (POLICY_CREATED OR POLICY_UPDATED OR POLICY_DELETED)
```

---

### Q: Trace a suspicious request

```kql
trace_id: "abc123def456"
```

This will show all logs from all indices with this trace.

---

## Incident Response Playbook

### Step 1: Identify the incident scope

```kql
event_type: CHAOS_* AND success: false
```

### Step 2: Check if policies responded

```kql
event_type: POLICY_TRIGGERED AND @timestamp > "2026-01-07T12:00:00"
```

### Step 3: Verify recovery

```kql
event_type: CHAOS_RECOVERY_COMPLETED AND chaos_experiment_id: "exp-123"
```

### Step 4: Check for security anomalies

```kql
event_type: SECURITY_RATE_LIMITED OR event_type: SECURITY_VALIDATION_FAILED
```

---

## Performance Queries

### Slow recoveries (>5 seconds)

```kql
event_type: CHAOS_RECOVERY_COMPLETED AND duration_ms > 5000
```

### Slow policy actions (>1 second)

```kql
event_type: POLICY_ACTION_EXECUTED AND duration_ms > 1000
```

---

## Tips

1. **Use trace_id** to correlate events across indices
2. **Filter by system_type** to isolate MySQL vs Redis issues
3. **Check actor** to identify who triggered changes
4. **Sort by @timestamp DESC** to see most recent first
