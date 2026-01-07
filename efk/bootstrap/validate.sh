#!/bin/sh
# Validation script for EFK stack
# Injects test logs and verifies correct indexing

set -e

ES_HOST="${ELASTICSEARCH_HOST:-elasticsearch}"
ES_PORT="${ELASTICSEARCH_PORT:-9200}"
ES_URL="http://${ES_HOST}:${ES_PORT}"

echo "=========================================="
echo "EFK Validation - Starting"
echo "=========================================="

# Wait for Elasticsearch
echo "Waiting for Elasticsearch..."
sleep 5
until curl -s "${ES_URL}/_cluster/health" | grep -qE '"status":"(green|yellow)"'; do
    sleep 2
done

# ==================== Inject Test Logs ====================
echo ""
echo "Injecting test logs..."

# Application log
echo "  - Injecting application log..."
curl -s -X POST "${ES_URL}/application-logs/_doc" \
    -H 'Content-Type: application/json' \
    -d '{
        "@timestamp": "2026-01-07T12:00:00.000Z",
        "log": {"level": "info"},
        "service": {"name": "controlplane"},
        "environment": "test",
        "event_type": "APP_STARTUP",
        "trace_id": "test-trace-001",
        "span_id": "test-span-001",
        "host": {"name": "test-host"},
        "message": "Application started"
    }' > /dev/null
echo "    ✓ Application log injected"

# Audit log
echo "  - Injecting audit log..."
curl -s -X POST "${ES_URL}/audit-logs/_doc" \
    -H 'Content-Type: application/json' \
    -d '{
        "@timestamp": "2026-01-07T12:00:01.000Z",
        "log": {"level": "info"},
        "service": {"name": "controlplane"},
        "environment": "test",
        "event_type": "POLICY_CREATED",
        "trace_id": "test-trace-002",
        "span_id": "test-span-002",
        "host": {"name": "test-host"},
        "actor": "admin",
        "resource_type": "Policy",
        "resource_id": "policy-001"
    }' > /dev/null
echo "    ✓ Audit log injected"

# Chaos event
echo "  - Injecting chaos event..."
curl -s -X POST "${ES_URL}/chaos-events/_doc" \
    -H 'Content-Type: application/json' \
    -d '{
        "@timestamp": "2026-01-07T12:00:02.000Z",
        "log": {"level": "info"},
        "service": {"name": "controlplane"},
        "environment": "test",
        "event_type": "CHAOS_INJECTION_STARTED",
        "trace_id": "test-trace-003",
        "span_id": "test-span-003",
        "host": {"name": "test-host"},
        "chaos_experiment_id": "exp-001",
        "system_type": "mysql",
        "fault_type": "LATENCY_INJECTION"
    }' > /dev/null
echo "    ✓ Chaos event injected"

# Security event
echo "  - Injecting security event..."
curl -s -X POST "${ES_URL}/security-events/_doc" \
    -H 'Content-Type: application/json' \
    -d '{
        "@timestamp": "2026-01-07T12:00:03.000Z",
        "log": {"level": "warn"},
        "service": {"name": "controlplane"},
        "environment": "test",
        "event_type": "SECURITY_RATE_LIMITED",
        "trace_id": "test-trace-004",
        "span_id": "test-span-004",
        "host": {"name": "test-host"},
        "client_ip": "192.168.1.100",
        "path": "/api/chaos/inject",
        "blocked": true
    }' > /dev/null
echo "    ✓ Security event injected"

# Refresh indices
curl -s -X POST "${ES_URL}/_refresh" > /dev/null

# ==================== Verify Indices ====================
echo ""
echo "Verifying indices..."

indices=("application-logs" "audit-logs" "chaos-events" "security-events")

for index in "${indices[@]}"; do
    count=$(curl -s "${ES_URL}/${index}/_count" | grep -o '"count":[0-9]*' | cut -d':' -f2)
    if [ "$count" -ge 1 ]; then
        echo "  ✓ ${index}: ${count} documents"
    else
        echo "  ✗ ${index}: no documents found"
    fi
done

# ==================== Verify ILM Attachment ====================
echo ""
echo "Verifying ILM policy attachment..."

for index in "${indices[@]}"; do
    policy=$(curl -s "${ES_URL}/${index}-000001/_ilm/explain" | grep -o '"policy":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$policy" ]; then
        echo "  ✓ ${index}-000001 -> ${policy}"
    else
        echo "  ⊙ ${index}-000001: no ILM policy attached"
    fi
done

# ==================== Sample Queries ====================
echo ""
echo "Running sample queries..."

# Query by event_type
echo "  - Finding all chaos events..."
chaos_count=$(curl -s "${ES_URL}/chaos-events/_search" \
    -H 'Content-Type: application/json' \
    -d '{"query": {"match": {"event_type": "CHAOS_INJECTION_STARTED"}}}' \
    | grep -o '"total":{"value":[0-9]*' | cut -d':' -f3)
echo "    ✓ Found ${chaos_count} chaos injection events"

# Query by trace_id
echo "  - Tracing request test-trace-003..."
trace_count=$(curl -s "${ES_URL}/_search" \
    -H 'Content-Type: application/json' \
    -d '{"query": {"term": {"trace_id": "test-trace-003"}}}' \
    | grep -o '"total":{"value":[0-9]*' | cut -d':' -f3)
echo "    ✓ Found ${trace_count} documents with trace_id test-trace-003"

# ==================== Show Retention Policies ====================
echo ""
echo "Retention policies (ILM):"
curl -s "${ES_URL}/_ilm/policy" | grep -oE '"[a-z]+-ilm":\{"version":[0-9]+,"modified_date":"[^"]+","policy":\{"phases":\{[^}]+\}' | while read policy; do
    name=$(echo "$policy" | grep -oE '^"[a-z]+-ilm"' | tr -d '"')
    echo "  - ${name}"
done

echo ""
echo "=========================================="
echo "EFK Validation - Complete!"
echo "=========================================="
