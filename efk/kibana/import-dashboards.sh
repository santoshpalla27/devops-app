#!/bin/bash
# Import Kibana dashboards
# Run this after the EFK stack is up

KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"

echo "=========================================="
echo "Importing Kibana Dashboards"
echo "=========================================="

# Wait for Kibana
echo "Waiting for Kibana..."
until curl -s "${KIBANA_URL}/api/status" | grep -q '"level":"available"'; do
    echo "Kibana not ready, waiting..."
    sleep 5
done
echo "Kibana is ready!"

# Import index patterns
echo ""
echo "Importing index patterns..."
curl -s -X POST "${KIBANA_URL}/api/saved_objects/_import?overwrite=true" \
    -H "kbn-xsrf: true" \
    --form file=@/kibana/index-patterns.ndjson \
    | grep -q '"success":true' && echo "  ✓ Index patterns imported" || echo "  ✗ Failed"

# Import dashboards
echo ""
echo "Importing dashboards..."

for dashboard in /kibana/dashboards/*.json; do
    name=$(basename "$dashboard" .json)
    echo "  - Importing ${name}..."
    
    curl -s -X POST "${KIBANA_URL}/api/saved_objects/_import?overwrite=true" \
        -H "kbn-xsrf: true" \
        --form file=@"${dashboard}" \
        | grep -q '"success":true' && echo "    ✓ Dashboard imported" || echo "    ✗ Failed"
done

echo ""
echo "=========================================="
echo "Dashboard Import Complete!"
echo "=========================================="
echo ""
echo "Access dashboards at:"
echo "  - Chaos Impact: ${KIBANA_URL}/app/dashboards#/view/chaos-impact-dashboard"
echo "  - Policy Execution: ${KIBANA_URL}/app/dashboards#/view/policy-execution-dashboard"
echo "  - Security & Audit: ${KIBANA_URL}/app/dashboards#/view/security-audit-dashboard"
