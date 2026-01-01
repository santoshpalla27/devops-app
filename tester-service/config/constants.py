# Base URL for the control plane API
CONTROL_PLANE_URL = "http://localhost:8080"

# Kafka configuration
KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
KAFKA_TOPIC = "controlplane-events"

# Prometheus configuration
PROMETHEUS_URL = "http://localhost:9090"

# Timeouts
DEFAULT_TIMEOUT = 120  # seconds
POLL_INTERVAL = 1      # seconds

# Retry configuration
MAX_RETRIES = 3
RETRY_DELAY = 2  # seconds
