package com.platform.controlplane.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simplified OpenTelemetry tracing configuration.
 * Uses noop OpenTelemetry to avoid autoconfiguration conflicts.
 */
@Slf4j
@Configuration
public class TracingConfig {
    
    @Value("${spring.application.name:devops-control-plane}")
    private String serviceName;
    
    @Value("${otel.traces.enabled:false}")
    private boolean tracingEnabled;
    
    @Bean
    public OpenTelemetry openTelemetry() {
        // Use noop implementation to avoid conflicts with Spring Boot autoconfiguration
        // Tracing can be enabled via OTEL agent if needed
        log.info("Using noop OpenTelemetry (tracing disabled to avoid conflicts)");
        return OpenTelemetry.noop();
    }
    
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, "1.0.0");
    }
}
