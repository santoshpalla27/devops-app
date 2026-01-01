# Running Tester Service as Container

## Quick Start

### Option 1: Run Tests Once (Auto-Exit)
```bash
# Build and run tester
docker-compose up --build tester

# The container will:
# 1. Wait for backend and kafka to be healthy
# 2. Run all scenarios in CI mode
# 3. Exit with code 0 (pass) or 1 (fail)
```

### Option 2: Keep Container Running for Manual Tests
```bash
# Edit docker-compose.yml - uncomment this line:
# command: ["tail", "-f", "/dev/null"]

# Start tester service
docker-compose up -d tester

# Execute manual tests
docker exec -it controlplane-tester python main.py run --scenario scenarios/mysql_failure.yaml
docker exec -it controlplane-tester python main.py run --scenario scenarios/chaos_latency_injection.yaml
docker exec -it controlplane-tester python main.py ci --fail-fast
```

## Full Stack with Tests

### Start Everything Including Tester
```bash
# Build all services
docker-compose build

# Start infrastructure
docker-compose up -d mysql-primary redis-master kafka prometheus

# Wait ~30s for infrastructure to be ready

# Start application
docker-compose up -d backend frontend

# Run tests (will auto-exit after completion)
docker-compose up --build tester
```

### View Test Results
```bash
# View real-time logs
docker-compose logs -f tester

# Check exit code
docker-compose ps tester

# Re-run tests
docker-compose up tester
```

## Configuration

The tester service uses these environment variables (already set in docker-compose.yml):

```yaml
environment:
  - CONTROL_PLANE_URL=http://backend:8080
  - KAFKA_BOOTSTRAP=kafka:9092
  - PROMETHEUS_URL=http://prometheus:9090
```

## Commands Available

### Inside Container
```bash
# Interactive shell
docker exec -it controlplane-tester sh

# Run single scenario
docker exec -it controlplane-tester python main.py run \
  --scenario scenarios/mysql_failure.yaml

# Run all scenarios
docker exec -it controlplane-tester python main.py ci

# Run with fail-fast
docker exec -it controlplane-tester python main.py ci --fail-fast

# Custom endpoints
docker exec -it controlplane-tester python main.py ci \
  --control-plane-url http://backend:8080 \
  --kafka-bootstrap kafka:9092 \
  --prometheus-url http://prometheus:9090
```

## CI/CD Integration

### In CI Pipeline
```yaml
# .github/workflows/test.yml
- name: Start Stack
  run: docker-compose up -d backend frontend

- name: Run Tests
  run: docker-compose up --exit-code-from tester tester

- name: Cleanup
  run: docker-compose down -v
```

### Exit Codes
- `0` - All tests passed ✅
- `1` - One or more tests failed ❌

## Troubleshooting

### Tests Failing Immediately
```bash
# Check if backend is ready
docker-compose logs backend

# Check backend health
curl http://localhost:8080/actuator/health

# Manually verify endpoints
curl http://localhost:8080/api/system/status
```

### Kafka Connection Issues
```bash
# Check kafka is running
docker-compose ps kafka

# Check kafka logs
docker-compose logs kafka

# Verify kafka topic
docker exec -it kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Container Won't Start
```bash
# Check build logs
docker-compose build tester

# Check Python dependencies
docker-compose run tester pip list

# Test manually
docker-compose run tester python -c "import aiohttp, yaml, kafka"
```

## Development Workflow

### 1. Local Development
```bash
cd tester-service

# Install dependencies
pip install -r requirements.txt

# Run against local stack
python main.py run --scenario scenarios/mysql_failure.yaml \
  --control-plane-url http://localhost:8080 \
  --kafka-bootstrap localhost:9092 \
  --prometheus-url http://localhost:9090
```

### 2. Test Changes in Container
```bash
# Rebuild tester only
docker-compose build tester

# Run updated tests
docker-compose up tester
```

### 3. Add New Scenario
```bash
# Create new YAML in scenarios/
vim tester-service/scenarios/new_test.yaml

# Rebuild and run
docker-compose build tester
docker-compose run tester python main.py run \
  --scenario scenarios/new_test.yaml
```

## Production Deployment

### Kubernetes Example
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: controlplane-tests
spec:
  template:
    spec:
      containers:
      - name: tester
        image: your-registry/tester-service:latest
        env:
        - name: CONTROL_PLANE_URL
          value: "http://backend-service:8080"
        - name: KAFKA_BOOTSTRAP
          value: "kafka-service:9092"
        - name: PROMETHEUS_URL
          value: "http://prometheus-service:9090"
        args: ["ci", "--fail-fast"]
      restartPolicy: Never
  backoffLimit: 3
```

## Scheduled Testing

### Docker Compose with Cron
```yaml
# Add to docker-compose.yml
  tester-scheduled:
    image: controlplane-tester
    environment:
      - CONTROL_PLANE_URL=http://backend:8080
    command: |
      sh -c "while true; do
        python main.py ci --scenarios-dir scenarios
        sleep 3600  # Run every hour
      done"
```

### Kubernetes CronJob
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: controlplane-tests
spec:
  schedule: "0 * * * *"  # Every hour
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: tester
            image: your-registry/tester-service:latest
            args: ["ci", "--fail-fast"]
          restartPolicy: OnFailure
```

## Best Practices

1. **Run tests after deployments** to validate behavior
2. **Use fail-fast in CI** to stop on first failure
3. **Keep container running in dev** for iterative testing
4. **Monitor test metrics** to track reliability trends
5. **Version test scenarios** alongside code changes
6. **Add new scenarios** for each new feature

## Example Output

```
============================================================
  DevOps Control Plane - Tester Service
  Scenario-Driven Black-Box Testing
============================================================

🚀 Starting scenario: mysql_connection_failure

  📌 Action 1/2: CHAOS
  📌 Action 2/2: WAIT

✅ PASSED: mysql_connection_failure
   Duration: 65.2s
   Assertions: 8 passed, 0 failed

   Assertion Details:
     ✅ No illegal states detected
     ✅ Policy 'mysql-auto-recover' was triggered
     ✅ Event 'CONNECTION_LOST' count: 1
     ✅ Event 'CONNECTION_ESTABLISHED' count: 1

============================================================
  Summary: 1 passed, 0 failed
============================================================
```
