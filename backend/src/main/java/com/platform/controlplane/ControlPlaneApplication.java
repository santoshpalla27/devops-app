package com.platform.controlplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DevOps Control Plane Application
 * 
 * A production-ready monolithic control-plane for managing connections to:
 * - MySQL (Standalone/Replication/Cluster)
 * - Redis (Standalone/Sentinel/Cluster)
 * - Kafka (Event backbone)
 * 
 * Features:
 * - Dynamic topology detection
 * - Automatic reconnection and failover
 * - Circuit breakers and retry mechanisms
 * - Comprehensive observability (metrics, logs, traces)
 */
@SpringBootApplication(exclude = {
    io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration.class,
    io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpSpanExporterAutoConfiguration.class,
    io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpLogRecordExporterAutoConfiguration.class,
    io.opentelemetry.instrumentation.spring.autoconfigure.propagators.PropagationAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
public class ControlPlaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlPlaneApplication.class, args);
    }
}
