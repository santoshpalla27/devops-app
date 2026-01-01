# Observability Guide

## Metrics Catalog

### State Transitions
- `controlplane.state.transition{system,from,to}` - All state transitions
- `controlplane.state.transition.invalid{system}` - Rejected transitions
- `controlplane.state.transitions.policy_triggered{system,from,to}` - Policy-driven transitions

### Chaos Engineering
- `controlplane.chaos.experiments.total{system,fault_type}` - Total experiments
- `controlplane.chaos.experiments.completed{system,fault_type,success}` - Completed experiments
- `controlplane.chaos.faults.injected{system,type}` - Faults injected
- `controlplane.chaos.faults.recovered{system,type}` - Faults recovered

### Policies
- `controlplane.policy.executions.total{policy,system,action,success}` - Policy executions
- `controlplane.state.transitions.policy_triggered{system,from,to}` - Policy-triggered transitions

### Connections
- `controlplane.connection.status{system}` - Connection status
- `controlplane.connection.latency{system}` - Connection latency
- `controlplane.connection.success{system}` - Successful connections
- `controlplane.connection.failure{system}` - Failed connections

---

## Structured Logging

### MDC Fields

All logs include contextual MDC fields for correlation:

| Field | Description | Example |
|-------|-------------|---------|
| `experimentId` | Chaos experiment ID | `abc-123-def` |
| `policyId` | Policy ID | `policy-456` |
| `policyName` | Human-readable policy name | `mysql-circuit-open-on-retries` |
| `systemType` | System identifier | `mysql`, `redis`, `kafka` |

### Log Format

```
2026-01-01 12:00:00 INFO [experimentId=abc-123, systemType=mysql] Chaos experiment mysql-connection-loss started
2026-01-01 12:00:05 INFO [systemType=mysql] State transition: CONNECTED -> DISCONNECTED
2026-01-01 12:00:06 INFO [policyId=policy-456, policyName=auto-reconnect, systemType=mysql] Policy triggered
2026-01-01 12:00:10 INFO [systemType=mysql] State transition: DISCONNECTED -> CONNECTING
```

---

## End-to-End Flow

### Example: Connection Loss → Auto-Healing

```
1. Inject Fault
   POST /api/chaos/inject
   → Metric: chaos.experiments.total++
   → Log: [experimentId=X, systemType=mysql] Experiment started
   → State: CONNECTED → DISCONNECTED

2. Policy Triggers
   → Metric: policy.executions.total++
   → Log: [policyId=Y, systemType=mysql] Policy 'auto-reconnect' triggered
   → Action: FORCE_RECONNECT

3. System Recovers
   → Metric: chaos.faults.recovered++
   → Log: [systemType=mysql] Connection restored
   → State: DISCONNECTED → CONNECTING → CONNECTED

4. Experiment Completes
   → Metric: chaos.experiments.completed++
   → Log: [experimentId=X] Experiment completed successfully
```

---

## Grafana Dashboard Queries

### Chaos Success Rate
```promql
sum(rate(controlplane_chaos_experiments_completed{success="true"}[5m])) 
/ 
sum(rate(controlplane_chaos_experiments_total[5m]))
```

### Policy Execution Count by System
```promql
sum by (system) (rate(controlplane_policy_executions_total[5m]))
```

### State Transition Heatmap
```promql
sum by (from, to) (rate(controlplane_state_transition[5m]))
```

---

## Best Practices

1. **Filter by experimentId** - Track entire chaos experiment lifecycle
2. **Correlate by systemType** - See all events for a specific system
3. **Alert on policy failures** - Monitor `policy.executions.total{success="false"}`
4. **Track recovery time** - Use `chaos.faults.recovered` latency histograms
