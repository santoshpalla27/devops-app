#!/bin/bash
# ==================== GameDay: Partial Failure ====================
# Simulates Redis intermittent failures (50% packet loss)

set -e

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

echo "=========================================="
echo "GameDay: Partial Failure Drill (Redis)"
echo "=========================================="

# Pre-drill state
echo ""
echo "[1/5] Capturing pre-drill error rate..."
pre_errors=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=sum(rate(controlplane_connector_errors_total{system_type=\"redis\"}[5m]))" | jq -r '.data.result[0].value[1] // "0"')
echo "  - Redis error rate: ${pre_errors}/s"

# Inject partial failure
echo ""
echo "[2/5] Injecting Redis partial failure (50% packet loss)..."
experiment_id=$(curl -s -X POST "${BACKEND_URL}/api/chaos/inject" \
    -H "Content-Type: application/json" \
    -d '{
        "systemType": "redis",
        "faultType": "PACKET_LOSS",
        "durationSeconds": 60,
        "intensity": 50
    }' | jq -r '.experimentId // "unknown"')

echo "  - Experiment ID: ${experiment_id}"

# Generate load
echo ""
echo "[3/5] Generating load to trigger failures..."
for i in {1..20}; do
    curl -s "${BACKEND_URL}/api/health" > /dev/null 2>&1 &
done
wait
sleep 15

# Check metrics
echo ""
echo "[4/5] Validating metrics..."
post_errors=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=sum(rate(controlplane_connector_errors_total{system_type=\"redis\"}[5m]))" | jq -r '.data.result[0].value[1] // "0"')
reconnects=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=sum(rate(controlplane_connector_reconnects_total{system_type=\"redis\"}[5m]))" | jq -r '.data.result[0].value[1] // "0"')

echo "  - Redis error rate: ${post_errors}/s (was: ${pre_errors}/s)"
echo "  - Reconnect rate: ${reconnects}/s"

errors_increased=$(echo "${post_errors} > ${pre_errors}" | bc -l 2>/dev/null || echo "0")
if [ "${errors_increased}" -eq 1 ]; then
    echo "  ✓ Errors increased as expected - observability working"
else
    echo "  ⊙ Errors unchanged (partial failure may be absorbed by retries)"
fi

# Cleanup
echo ""
echo "[5/5] Triggering recovery..."
curl -s -X POST "${BACKEND_URL}/api/chaos/recover?experimentId=${experiment_id}" > /dev/null

echo ""
echo "=========================================="
echo "GameDay Complete: Partial Failure"
echo "=========================================="
echo ""
echo "Validation:"
echo "  - Errors reflected: $([ "${errors_increased}" -eq 1 ] && echo "✓" || echo "⊙")"
echo "  - Reconnects tracked: $([ "$(echo "${reconnects} > 0" | bc -l 2>/dev/null || echo "0")" -eq 1 ] && echo "✓" || echo "⊙")"
echo "  - Dashboard: http://localhost:3001/d/chaos-engineering"
