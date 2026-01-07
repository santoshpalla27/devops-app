#!/bin/bash
# ==================== GameDay: Database Latency ====================
# Simulates MySQL latency to validate observability

set -e

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

echo "=========================================="
echo "GameDay: Database Latency Drill"
echo "=========================================="

# Pre-drill state
echo ""
echo "[1/5] Capturing pre-drill latency..."
pre_p95=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=histogram_quantile(0.95,sum(rate(controlplane_http_request_duration_seconds_bucket[5m]))by(le))" | jq -r '.data.result[0].value[1] // "0"')
echo "  - HTTP P95 latency: ${pre_p95}s"

# Inject latency
echo ""
echo "[2/5] Injecting MySQL latency (500ms) via chaos API..."
experiment_id=$(curl -s -X POST "${BACKEND_URL}/api/chaos/inject" \
    -H "Content-Type: application/json" \
    -d '{
        "systemType": "mysql",
        "faultType": "LATENCY_INJECTION",
        "durationSeconds": 60,
        "intensity": 500
    }' | jq -r '.experimentId // "unknown"')

echo "  - Experiment ID: ${experiment_id}"

# Generate load
echo ""
echo "[3/5] Generating load to measure impact..."
for i in {1..10}; do
    curl -s "${BACKEND_URL}/api/policies" > /dev/null &
done
wait
sleep 10

# Check metrics
echo ""
echo "[4/5] Validating metrics..."
post_p95=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=histogram_quantile(0.95,sum(rate(controlplane_http_request_duration_seconds_bucket[5m]))by(le))" | jq -r '.data.result[0].value[1] // "0"')
connector_latency=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=histogram_quantile(0.95,sum(rate(controlplane_connector_latency_seconds_bucket{system_type=\"mysql\"}[5m]))by(le))" | jq -r '.data.result[0].value[1] // "0"')

echo "  - HTTP P95 latency: ${post_p95}s (was: ${pre_p95}s)"
echo "  - MySQL connector P95: ${connector_latency}s"

latency_increased=$(echo "${post_p95} > ${pre_p95}" | bc -l)
if [ "${latency_increased}" -eq 1 ]; then
    echo "  ✓ Latency increased as expected"
else
    echo "  ⊙ Latency unchanged (may need more load)"
fi

# Cleanup
echo ""
echo "[5/5] Triggering recovery..."
curl -s -X POST "${BACKEND_URL}/api/chaos/recover?experimentId=${experiment_id}" > /dev/null

echo ""
echo "=========================================="
echo "GameDay Complete: Database Latency"
echo "=========================================="
echo ""
echo "Validation:"
echo "  - Latency reflected in metrics: $([ "${latency_increased}" -eq 1 ] && echo "✓" || echo "⊙")"
echo "  - Experiment: ${experiment_id}"
echo "  - Dashboard: http://localhost:3001/d/controlplane-health"
