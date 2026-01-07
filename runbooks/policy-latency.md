# Runbook: PolicyLatency

## Alert Details
- **Severity**: Warning (Ticket)
- **SLO**: Policy Latency P95 < 100ms
- **Current**: Exceeds threshold

---

## Symptoms
- Policy evaluations slow
- Delayed responses to chaos events
- Backlog building up

---

## Investigation

### 1. Open Dashboard
[Policy Engine Dashboard](http://localhost:3001/d/policy-engine)

### 2. Check Evaluation Load
```promql
sum(rate(controlplane_policy_evaluations_total[5m])) by (system_type)
```

### 3. Check Database
```bash
# Check slow queries
docker exec mysql-primary mysql -u root -ppassword -e "SHOW PROCESSLIST"
```

---

## Remediation

### If High Load
- Check for policy loops
- Review trigger conditions

### If Database Slow
- Optimize policy queries
- Add missing indexes
- Check connection pool

### If Memory Pressure
- Increase JVM heap
- Review GC settings

---

## Long-term
1. Profile policy evaluation code
2. Consider caching policy rules
3. Review policy count per system
