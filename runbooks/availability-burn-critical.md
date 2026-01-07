# Runbook: AvailabilityBurnCritical

## Alert Details
- **Severity**: Critical (Page)
- **SLO**: API Availability 99.9%
- **Burn Rate**: 14.4x (exhausts budget in ~2 hours)

---

## Symptoms
- HTTP error rate significantly elevated
- Users experiencing failures
- Error budget being consumed rapidly

---

## Immediate Actions (< 5 min)

### 1. Check Service Status
```bash
# Check backend health
curl http://localhost:8080/actuator/health

# Check container status
docker ps | grep backend
docker logs controlplane-backend --tail 100
```

### 2. Check Dependencies
```bash
# MySQL
docker exec mysql-primary mysql -u root -ppassword -e "SELECT 1"

# Redis
docker exec redis-master redis-cli ping

# Kafka
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### 3. Check Recent Deployments
- Review last deployment time
- Check for config changes
- Review recent chaos experiments

---

## Investigation Steps

### 1. Open Dashboard
[Control Plane Health Dashboard](http://localhost:3001/d/controlplane-health)

### 2. Check Error Distribution
```promql
sum(rate(controlplane_http_request_errors_total[5m])) by (error_code, endpoint)
```

### 3. Check Logs
```bash
# Kibana
http://localhost:5601/app/discover#/?_g=(filters:!(),query:(query:'event_type:ERROR_*'))
```

---

## Remediation Options

### If Dependency Issue
1. Restart affected dependency
2. Check connection pool settings
3. Enable circuit breaker

### If Code Issue
1. Roll back to last known good version
2. Disable problematic feature flag
3. Scale up instances if load-related

### If Chaos-Related
1. Check active experiments: `curl http://localhost:8080/api/chaos/experiments`
2. Trigger recovery: `POST /api/chaos/recover?experimentId=<id>`

---

## Escalation
- **> 15 min unresolved**: Notify team lead
- **> 30 min unresolved**: Incident commander
- **During off-hours**: Follow on-call escalation

---

## Post-Incident
1. Create incident report
2. Update this runbook if needed
3. Review SLO thresholds
