# Deep Chaos Experiment: MySQL Latency Injection

## Overview
This experiment injects **real, behavior-level latency** into MySQL query execution to validate the entire control-plane resilience loop.

## Implementation Details

### Fault Type
`LATENCY_INJECTION` - Injects 2000ms (2 second) artificial delay before each MySQL query execution.

### Mechanism
- **Wrapper-level injection**: The `MySQLFaultInjector.beforeQuery()` method is called before each query
- **Thread.sleep()**: Real blocking delay, not just state changes
- **Atomic state**: Thread-safe latency control using `AtomicBoolean` and `AtomicInteger`

### Guardrails
✅ **Time-boxed**: Auto-reverts after experiment duration  
✅ **Thread-safe**: Uses atomic variables for concurrent access  
✅ **Logged**: Full correlation ID tracking via MDC  
✅ **Visible**: UI shows active experiments and validation status  
✅ **Safe**: No permanent damage, automatic cleanup

---

## Expected Control-Plane Loop

### 1. Chaos Injection
```
POST /api/chaos/inject
{
  "systemType": "mysql",
  "faultType": "LATENCY_INJECTION",
  "durationSeconds": 300
}
```

### 2. Immediate Effects
- ✅ `latencyInjectionActive` set to `true`
- ✅ `injectedLatencyMs` set to `2000`
- ✅ State transition: `CONNECTED` → `DEGRADED`

### 3. Query Execution Impact
- ✅ Every MySQL query delayed by 2000ms
- ✅ Latency metric spikes: `controlplane.connection.latency{system=mysql}` > 2000ms
- ✅ Transaction times increase

### 4. Retry Engine Activation
- ✅ Slow queries trigger retry logic
- ✅ Retry count increments
- ✅ Metric: `controlplane.connection.retries{system=mysql}`

### 5. State Machine Transition
- ✅ State: `DEGRADED` → `RETRYING` (if failures occur)
- ✅ Metric: `controlplane.state.transition{from=DEGRADED,to=RETRYING}`

### 6. Policy Evaluation
- ✅ Policy: `mysql-circuit-open-on-retries`
- ✅ Condition: `retries >= 5`
- ✅ Triggered: YES (after 5 retries)

### 7. Action Execution
- ✅ Action: `OPEN_CIRCUIT`
- ✅ Circuit breaker opens
- ✅ State transition: `RETRYING` → `CIRCUIT_OPEN`
- ✅ Metric: `controlplane.policy.executions.total{policy=mysql-circuit-open}`

### 8. Circuit Breaker Protection
- ✅ Further requests blocked
- ✅ Metric: `controlplane.circuit.state{system=mysql,state=OPEN}`

### 9. Auto-Recovery Phase
- ✅ Experiment duration expires (300s)
- ✅ Chaos auto-reverts: `latencyInjectionActive` → `false`
- ✅ Circuit recovery policy evaluates

### 10. System Restoration
- ✅ Circuit closes: `CIRCUIT_OPEN` → `RECOVERING`
- ✅ Connection restored: `RECOVERING` → `CONNECTED`
- ✅ Metrics normalize
- ✅ Full cycle complete

---

## Validation Checklist

| Step | Check | Status |
|------|-------|--------|
| 1 | Latency injection activates | ⏳ |
| 2 | Queries actually delay 2000ms | ⏳ |
| 3 | Latency metric exceeds 2000ms | ⏳ |
| 4 | Retry count increases | ⏳ |
| 5 | State transitions to RETRYING | ⏳ |
| 6 | Policy fires after 5 retries | ⏳ |
| 7 | Circuit breaker opens | ⏳ |
| 8 | Requests blocked | ⏳ |
| 9 | Auto-recovery after 300s | ⏳ |
| 10 | System returns to CONNECTED | ⏳ |

---

## Metrics to Watch

### Latency
```promql
controlplane_connection_latency{system="mysql"}
```
**Expected**: Spike to >2000ms during experiment

### Retries
```promql
controlplane_connection_retries{system="mysql"}
```
**Expected**: Increment as latency causes failures

### State Transitions
```promql
controlplane_state_transition_total{from="CONNECTED",to="DEGRADED"}
controlplane_state_transition_total{from="DEGRADED",to="RETRYING"}
controlplane_state_transition_total{from="RETRYING",to="CIRCUIT_OPEN"}
```

### Policy Executions
```promql
controlplane_policy_executions_total{policy="mysql-circuit-open-on-retries"}
```
**Expected**: Increment when retries >= 5

### Circuit Breaker
```promql
controlplane_circuit_state{system="mysql"}
```
**Expected**: 0 (CLOSED) → 1 (OPEN) → 0 (CLOSED)

---

## Recovery Time Objective (RTO)

**Expected Total Time**: ~6 minutes

- Chaos duration: 5 minutes (300s)
- Policy cooldown: ~30-60s
- Circuit recovery: ~30s
- Connection restore: ~10s

**Actual**: _[To be measured during validation]_

---

## Observed Behavior

### Run 1: [Date]
_[Record actual observations here]_

- Latency injection started at: __:__:__
- First retry at: __:__:__
- Circuit opened at: __:__:__
- Recovery completed at: __:__:__
- Total duration: ___ minutes

### Anomalies
_[Note any unexpected behavior]_

### Improvements
_[Suggested improvements based on observations]_

---

## Interview Talking Points

✅ **Real failure modeling**: Thread.sleep() at wrapper level, not mocked  
✅ **Full loop validation**: Chaos → Metrics → State → Policy → Action → Recovery  
✅ **Production-ready**: Time-bounded, auto-revert, fully logged  
✅ **Observable**: Metrics at every stage, correlation IDs  
✅ **Safe guardrails**: No permanent damage, concurrent experiments limited  

---

## Future Enhancements

1. **Query-specific chaos**: Target SELECT vs INSERT differently
2. **Partial failure injection**: 50% of queries delayed
3. **Progressive latency**: Start at 100ms, escalate to 5000ms
4. **Network partition simulation**: Block specific MySQL nodes
5. **Byzantine failures**: Return corrupt data occasionally
