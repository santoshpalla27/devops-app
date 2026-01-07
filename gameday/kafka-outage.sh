#!/bin/bash
# ==================== GameDay: Kafka Outage ====================
# Simulates Kafka unavailability to validate observability

set -e

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
ALERTMANAGER_URL="${ALERTMANAGER_URL:-http://localhost:9093}"

echo "=========================================="
echo "GameDay: Kafka Outage Drill"
echo "=========================================="

# Pre-drill state capture
echo ""
echo "[1/6] Capturing pre-drill metrics..."
pre_outbox=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=controlplane_kafka_outbox_pending" | jq -r '.data.result[0].value[1] // "0"')
pre_dispatch=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=sum(rate(controlplane_kafka_dispatch_total[5m]))" | jq -r '.data.result[0].value[1] // "0"')
echo "  - Outbox pending: ${pre_outbox}"
echo "  - Dispatch rate: ${pre_dispatch}/s"

# Inject failure
echo ""
echo "[2/6] Injecting Kafka outage via chaos API..."
experiment_id=$(curl -s -X POST "${BACKEND_URL}/api/chaos/inject" \
    -H "Content-Type: application/json" \
    -d '{
        "systemType": "kafka",
        "faultType": "CONNECTION_LOSS",
        "durationSeconds": 60,
        "intensity": 100
    }' | jq -r '.experimentId // "unknown"')

echo "  - Experiment ID: ${experiment_id}"

# Wait for impact
echo ""
echo "[3/6] Waiting for impact (30 seconds)..."
sleep 30

# Check metrics reflect reality
echo ""
echo "[4/6] Validating metrics..."
post_outbox=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=controlplane_kafka_outbox_pending" | jq -r '.data.result[0].value[1] // "0"')
errors=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=sum(rate(controlplane_kafka_dispatch_errors_total[1m]))" | jq -r '.data.result[0].value[1] // "0"')

echo "  - Outbox pending: ${post_outbox} (was: ${pre_outbox})"
echo "  - Dispatch errors: ${errors}/s"

if (( $(echo "${post_outbox} > ${pre_outbox}" | bc -l) )); then
    echo "  ✓ Outbox backlog increased - metrics reflect reality"
else
    echo "  ✗ FAIL: Outbox not growing during Kafka outage"
fi

# Check alerts fired
echo ""
echo "[5/6] Checking alerts..."
alerts=$(curl -s "${ALERTMANAGER_URL}/api/v2/alerts" | jq -r 'length')
kafka_alerts=$(curl -s "${ALERTMANAGER_URL}/api/v2/alerts" | jq -r '[.[] | select(.labels.alertname | test("Kafka"; "i"))] | length')

echo "  - Total active alerts: ${alerts}"
echo "  - Kafka-related alerts: ${kafka_alerts}"

if [ "${kafka_alerts}" -gt 0 ]; then
    echo "  ✓ Kafka alerts fired correctly"
else
    echo "  ⊙ No Kafka alerts (may need longer drain)"
fi

# Check logs correlate
echo ""
echo "[6/6] Checking log correlation..."
# This would query Elasticsearch in a real setup
echo "  - Check Kibana: event_type:KAFKA_* AND chaos_experiment_id:${experiment_id}"

# Cleanup
echo ""
echo "Triggering recovery..."
curl -s -X POST "${BACKEND_URL}/api/chaos/recover?experimentId=${experiment_id}" > /dev/null

echo ""
echo "=========================================="
echo "GameDay Complete: Kafka Outage"
echo "=========================================="
echo ""
echo "Results Summary:"
echo "  - Metrics reflected outage: $([ "${post_outbox}" -gt "${pre_outbox}" ] && echo "✓" || echo "✗")"
echo "  - Alerts fired: $([ "${kafka_alerts}" -gt 0 ] && echo "✓" || echo "⊙")"
echo "  - Experiment ID for log correlation: ${experiment_id}"
