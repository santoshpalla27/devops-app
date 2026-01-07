#!/bin/bash
# Bootstrap script for EFK stack
# Loads ILM policies, index templates, and ingest pipeline

set -e

ES_HOST="${ELASTICSEARCH_HOST:-elasticsearch}"
ES_PORT="${ELASTICSEARCH_PORT:-9200}"
ES_URL="http://${ES_HOST}:${ES_PORT}"

echo "=========================================="
echo "EFK Bootstrap - Starting"
echo "Elasticsearch: ${ES_URL}"
echo "=========================================="

# Wait for Elasticsearch to be ready
echo "Waiting for Elasticsearch..."
until curl -s "${ES_URL}/_cluster/health" | grep -qE '"status":"(green|yellow)"'; do
    echo "Elasticsearch not ready, waiting..."
    sleep 5
done
echo "Elasticsearch is ready!"

# ==================== Load ILM Policies ====================
echo ""
echo "Loading ILM policies..."

for policy_file in /ilm-policies/*.json; do
    policy_name=$(basename "$policy_file" .json)
    echo "  - Loading ILM policy: ${policy_name}"
    
    response=$(curl -s -w "\n%{http_code}" -X PUT "${ES_URL}/_ilm/policy/${policy_name}" \
        -H 'Content-Type: application/json' \
        -d @"${policy_file}")
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "    ✓ ILM policy ${policy_name} loaded successfully"
    else
        echo "    ✗ Failed to load ILM policy ${policy_name}: ${body}"
        exit 1
    fi
done

# ==================== Load Index Templates ====================
echo ""
echo "Loading index templates..."

for template_file in /index-templates/*.json; do
    template_name=$(basename "$template_file" .json)
    echo "  - Loading index template: ${template_name}"
    
    response=$(curl -s -w "\n%{http_code}" -X PUT "${ES_URL}/_index_template/${template_name}" \
        -H 'Content-Type: application/json' \
        -d @"${template_file}")
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "    ✓ Index template ${template_name} loaded successfully"
    else
        echo "    ✗ Failed to load index template ${template_name}: ${body}"
        exit 1
    fi
done

# ==================== Create Initial Indices with Aliases ====================
echo ""
echo "Creating initial indices with aliases..."

indices=("application-logs" "audit-logs" "chaos-events" "security-events")

for index in "${indices[@]}"; do
    echo "  - Creating initial index: ${index}-000001"
    
    response=$(curl -s -w "\n%{http_code}" -X PUT "${ES_URL}/${index}-000001" \
        -H 'Content-Type: application/json' \
        -d "{
            \"aliases\": {
                \"${index}\": {
                    \"is_write_index\": true
                }
            }
        }")
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "    ✓ Index ${index}-000001 created with alias"
    elif echo "$body" | grep -q "resource_already_exists_exception"; then
        echo "    ⊙ Index ${index}-000001 already exists"
    else
        echo "    ✗ Failed to create index ${index}-000001: ${body}"
        exit 1
    fi
done

# ==================== Create Ingest Pipeline ====================
echo ""
echo "Creating ingest pipeline..."

curl -s -X PUT "${ES_URL}/_ingest/pipeline/logs-pipeline" \
    -H 'Content-Type: application/json' \
    -d '{
        "description": "Log processing pipeline for control plane",
        "processors": [
            {
                "date": {
                    "field": "@timestamp",
                    "target_field": "@timestamp",
                    "formats": ["ISO8601", "yyyy-MM-dd'\''T'\''HH:mm:ss.SSSXXX"],
                    "ignore_failure": true
                }
            },
            {
                "set": {
                    "field": "ingest_time",
                    "value": "{{_ingest.timestamp}}"
                }
            },
            {
                "lowercase": {
                    "field": "log.level",
                    "ignore_missing": true
                }
            }
        ]
    }' > /dev/null

echo "    ✓ Ingest pipeline created"

# ==================== Verification ====================
echo ""
echo "=========================================="
echo "Verifying configuration..."
echo "=========================================="

# Verify ILM policies
echo ""
echo "ILM Policies:"
curl -s "${ES_URL}/_ilm/policy" | grep -oE '"[a-z]+-ilm"' | tr -d '"' | while read policy; do
    echo "  ✓ ${policy}"
done

# Verify index templates
echo ""
echo "Index Templates:"
curl -s "${ES_URL}/_index_template" | grep -oE '"name":"[a-z]+-[a-z]+"' | cut -d'"' -f4 | while read template; do
    echo "  ✓ ${template}"
done

# Verify indices
echo ""
echo "Indices:"
curl -s "${ES_URL}/_cat/indices?v" | grep -E "application-logs|audit-logs|chaos-events|security-events" | while read line; do
    echo "  ✓ ${line}"
done

echo ""
echo "=========================================="
echo "EFK Bootstrap - Complete!"
echo "=========================================="
