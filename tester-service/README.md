# Tester Service

External black-box testing service for the DevOps Control Plane.

## Purpose

Validates system behavior under real failure and recovery conditions through scenario-driven testing.

**Core Question:** "Does the system behave correctly under real failure and recovery conditions?"

## Architecture

- **Scenario-driven**: Tests defined in YAML, not code
- **Black-box**: External observation via REST, Kafka, Prometheus
- **Multi-source validation**: API + Events + Metrics
- **Deterministic**: Event-driven, not sleep-based

## Quick Start

### Local Testing
```bash
# Install dependencies
pip install -r requirements.txt

# Run single scenario
python main.py run --scenario scenarios/mysql_failure.yaml

# Run all scenarios
python main.py ci --scenarios-dir scenarios
```

### Docker
```bash
# Build
docker build -t tester-service .

# Run
docker run --network=host tester-service
```

## Scenarios

- `mysql_failure.yaml` - MySQL connection failure and recovery
- `chaos_latency_injection.yaml` - Deep chaos latency experiment
- `policy_evaluation.yaml` - Policy trigger and execution
- `reconciliation_loop.yaml` - Drift detection and convergence

## Scenario Format

```yaml
name: scenario_name
description: What this tests
timeout: 120

preconditions:
  - system.state == EXPECTED

actions:
  - type: CHAOS
    system: mysql
    fault: CONNECTION_LOSS
    duration: 60

expectations:
  states:
    - system: mysql
      final_state: CONNECTED
  
  policies:
    - policy_id: auto-recover
      should_fire: true
  
  events:
    - type: CONNECTION_LOST
      count: 1
```

## Observers

1. **API Observer** - Polls REST endpoints for state, policies, chaos, drift
2. **Kafka Observer** - Subscribes to event stream
3. **Prometheus Observer** - Scrapes metrics

## Assertions

- **State**: Transition sequences, illegal states, final state
- **Policy**: Triggered/not triggered, success/failure
- **Metrics**: Thresholds, retry counts, latency
- **Events**: Count, existence, order, duplicates

## CI Integration

```bash
# Fail fast on first failure
python main.py ci --fail-fast

# Custom configuration
python main.py ci \
  --control-plane-url http://backend:8080 \
  --kafka-bootstrap kafka:9092 \
  --prometheus-url http://prometheus:9090
```

## Exit Codes

- `0` - All tests passed
- `1` - One or more tests failed

## Interview Talking Points

✅ External black-box testing  
✅ Scenario-driven validation  
✅ Multi-source observation (API + Kafka + Metrics)  
✅ Deterministic without sleep()  
✅ Evidence-based reporting  
✅ CI/CD ready
