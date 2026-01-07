# EFK Stack - Production-Grade Observability

## Overview

Production-faithful EFK (Elasticsearch + Fluent Bit + Kibana) observability stack. Fully automated, declarative, no manual configuration required.

## Quick Start

```bash
cd efk
docker compose up -d
```

Wait ~2 minutes for all services to initialize.

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌───────────────┐
│ Application │────▶│  Fluent Bit │────▶│ Elasticsearch │
│   (JSON)    │     │ (Processor) │     │   (Storage)   │
└─────────────┘     └─────────────┘     └───────────────┘
                                               │
                                               ▼
                                        ┌─────────────┐
                                        │   Kibana    │
                                        │    (UI)     │
                                        └─────────────┘
```

## Access Points

| Service | URL | Description |
|---------|-----|-------------|
| Kibana | http://localhost:5601 | Log visualization |
| Elasticsearch | http://localhost:9200 | API access |
| Fluent Bit | localhost:24224 | Log ingestion |

## Index Strategy

| Index Pattern | Purpose | Retention |
|--------------|---------|-----------|
| `application-logs-*` | App logs | 14 days |
| `audit-logs-*` | Audit trail | 90 days |
| `chaos-events-*` | Chaos experiments | 30 days |
| `security-events-*` | Security events | 180 days |

## Log Format Requirement

**All logs MUST be JSON with these fields:**

```json
{
  "@timestamp": "2026-01-07T12:00:00.000Z",
  "log": { "level": "info" },
  "service": { "name": "controlplane" },
  "environment": "production",
  "event_type": "CHAOS_INJECTION_STARTED",
  "trace_id": "abc123",
  "span_id": "def456",
  "host": { "name": "app-server-1" }
}
```

## Routing Rules

Fluent Bit routes logs based on `event_type`:

| Event Type Prefix | Target Index |
|-------------------|--------------|
| `APP_`, `HTTP_`, `CONNECTOR_` | application-logs |
| `POLICY_CREATED/UPDATED/DELETED`, `SECURITY_AUDIT` | audit-logs |
| `CHAOS_`, `RECOVERY_` | chaos-events |
| `SECURITY_`, `AUTH_` | security-events |

## Validation

Verify the stack is working:

```bash
# Check all services are healthy
docker compose ps

# Run validation
docker compose run --rm efk-validation

# Manual verification
curl http://localhost:9200/_cat/indices?v
curl http://localhost:9200/_ilm/policy
```

## Send Test Log

```bash
# Via TCP to Fluent Bit
echo '{"@timestamp":"2026-01-07T12:00:00Z","log":{"level":"info"},"service":{"name":"test"},"environment":"dev","event_type":"APP_STARTUP","trace_id":"test-001","span_id":"span-001","host":{"name":"localhost"},"message":"Test log"}' | nc localhost 24224
```

## Limitations

1. **Single-node Elasticsearch**: Not suitable for production HA
2. **No security**: X-Pack security disabled for simplicity
3. **Local volumes**: Data stored in Docker volumes

## Troubleshooting

```bash
# Check Elasticsearch health
curl http://localhost:9200/_cluster/health?pretty

# Check Fluent Bit
curl http://localhost:2020/api/v1/health

# View Fluent Bit logs
docker compose logs fluent-bit

# Check ILM policies
curl http://localhost:9200/_ilm/policy?pretty

# Check index templates
curl http://localhost:9200/_index_template?pretty
```
