package com.platform.controlplane.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry SDK configuration.
 * 
 * Provides:
 * - Tracer with resource attributes
 * - OTLP exporter for traces
 * - Context propagation (W3C)
 * 
 * Required attributes on all telemetry:
 * - service.name
 * - service.version
 * - environment
 * - instance_id
 */
@Slf4j
@Configuration
public class OpenTelemetryConfig {
    
    @Value("${otel.service.name:controlplane}")
    private String serviceName;
    
    @Value("${otel.service.version:1.0.0}")
    private String serviceVersion;
    
    @Value("${otel.environment:development}")
    private String environment;
    
    @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;
    
    @Value("${otel.enabled:true}")
    private boolean enabled;
    
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    
    private SdkTracerProvider tracerProvider;
    private OpenTelemetrySdk openTelemetry;
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("OpenTelemetry disabled");
            return;
        }
        
        log.info("Initializing OpenTelemetry SDK: service={}, version={}, env={}, instance={}", 
            serviceName, serviceVersion, environment, instanceId);
    }
    
    @Bean
    public OpenTelemetry openTelemetry() {
        if (!enabled) {
            return OpenTelemetry.noop();
        }
        
        // Build resource with required attributes
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                .put("environment", environment)
                .put("instance_id", instanceId)
                .build()));
        
        // Create OTLP exporter
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .setTimeout(10, TimeUnit.SECONDS)
            .build();
        
        // Create tracer provider with batch processor
        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(2048)
                .setScheduleDelay(5, TimeUnit.SECONDS)
                .build())
            .setResource(resource)
            .build();
        
        // Build OpenTelemetry SDK
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
        
        // Register as global
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(openTelemetry);
        
        log.info("OpenTelemetry SDK initialized, exporting to {}", otlpEndpoint);
        
        return openTelemetry;
    }
    
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, serviceVersion);
    }
    
    /**
     * Get the instance ID for this application instance.
     */
    public String getInstanceId() {
        return instanceId;
    }
    
    /**
     * Get resource attributes for adding to spans.
     */
    public Attributes getResourceAttributes() {
        return Attributes.builder()
            .put("service.name", serviceName)
            .put("service.version", serviceVersion)
            .put("environment", environment)
            .put("instance_id", instanceId)
            .build();
    }
    
    @PreDestroy
    public void shutdown() {
        if (tracerProvider != null) {
            log.info("Shutting down OpenTelemetry SDK");
            tracerProvider.shutdown();
        }
    }
}
