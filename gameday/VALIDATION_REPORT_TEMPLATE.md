# GameDay Validation Report Template

## Drill Information

| Field | Value |
|-------|-------|
| **Date** | YYYY-MM-DD |
| **Drill Type** | Kafka Outage / DB Latency / Partial Failure |
| **Duration** | XX minutes |
| **Participants** | Team names |

---

## Pre-Drill Checklist

- [ ] All services healthy
- [ ] Prometheus scraping
- [ ] Grafana accessible
- [ ] Alertmanager configured
- [ ] Runbooks accessible

---

## Drill Execution

### Scenario: [Describe scenario]

**Injection Time:** HH:MM:SS
**Recovery Time:** HH:MM:SS
**Total Duration:** XX seconds

---

## Validation Results

### 1. Metrics Reflect Reality

| Metric | Pre-Drill | During Drill | Expected | Pass? |
|--------|-----------|--------------|----------|-------|
| Request rate | | | ↓ or stable | |
| Error rate | | | ↑ | |
| Latency P95 | | | ↑ | |
| Outbox pending | | | ↑ | |

**Result:** ✅ Pass / ❌ Fail

### 2. Logs Correlate

| Check | Result |
|-------|--------|
| Events have trace_id | ✅/❌ |
| Events have experiment_id | ✅/❌ |
| Logs appear in Kibana | ✅/❌ |
| Can trace request flow | ✅/❌ |

**Result:** ✅ Pass / ❌ Fail

### 3. Alerts Fire Correctly

| Alert | Expected | Fired? | Time to Fire |
|-------|----------|--------|--------------|
| ConnectorUnhealthy | Yes | ✅/❌ | Xs |
| KafkaOutboxBacklog | Yes | ✅/❌ | Xs |
| AvailabilityBurn | Maybe | ✅/❌ | Xs |

**Result:** ✅ Pass / ❌ Fail

### 4. Recovery Successful

| Check | Result |
|-------|--------|
| Recovery triggered | ✅/❌ |
| Metrics returned to baseline | ✅/❌ |
| No lingering alerts | ✅/❌ |
| Grafana annotation created | ✅/❌ |

**Result:** ✅ Pass / ❌ Fail

---

## Issues Found

| Issue | Severity | Action Item |
|-------|----------|-------------|
| | High/Medium/Low | |

---

## Overall Result

| Category | Result |
|----------|--------|
| Metrics | ✅/❌ |
| Logs | ✅/❌ |
| Alerts | ✅/❌ |
| Recovery | ✅/❌ |
| **OVERALL** | **✅/❌** |

---

## Recommendations

1. 
2. 
3. 

---

## Sign-off

| Role | Name | Date |
|------|------|------|
| Drill Lead | | |
| SRE | | |
| Dev Lead | | |
