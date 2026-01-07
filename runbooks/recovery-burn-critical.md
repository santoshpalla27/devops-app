# Runbook: RecoveryBurnCritical

## Alert Details
- **Severity**: Critical (Page)
- **SLO**: Chaos Recovery 99%
- **Burn Rate**: 14.4x

---

## Symptoms
- Chaos recovery operations failing
- Systems stuck in degraded state
- Experiments not completing

---

## Immediate Actions (< 5 min)

### 1. Check Active Experiments
```bash
curl http://localhost:8080/api/chaos/experiments?status=ACTIVE
```

### 2. Force Recovery
```bash
# Get experiment ID from dashboard or API
curl -X POST http://localhost:8080/api/chaos/recover?experimentId=<id>
```

### 3. Check Connector Health
```bash
curl http://localhost:8080/actuator/health/connectivity
```

---

## Investigation

### 1. Open Dashboard
[Chaos Engineering Dashboard](http://localhost:3001/d/chaos-engineering)

### 2. Check Recovery Logs
```bash
# Kibana query
event_type:CHAOS_RECOVERY_FAILED OR event_type:CHAOS_RECOVERY_STARTED
```

### 3. Check System Under Test
- Is the target system responsive?
- Are there network issues?
- Is Toxiproxy healthy?

---

## Remediation

### If Toxiproxy Issue
```bash
docker restart toxiproxy
```

### If Connector Issue
```bash
# Check connector logs
docker logs controlplane-backend --tail 100 | grep -i connect
```

### If Timeout
- Increase recovery timeout in configuration
- Check for resource constraints

---

## Escalation
- **> 10 min**: Page chaos team lead
- **Multiple failures**: Disable chaos experiments globally

---

## Post-Incident
1. Document failure mode
2. Add test coverage
3. Update recovery mechanism
