package com.platform.controlplane.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry tracing configuration.
 */
@Slf4j
@Configuration
public class TracingConfig {
    
    @Value("${spring.application.name:devops-control-plane}")
    private String serviceName;
    
    @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;
    
    @Value("${otel.traces.enabled:true}")
    private boolean tracingEnabled;
    
    @Bean
    public OpenTelemetry openTelemetry() {
        if (!tracingEnabled) {
            log.info("OpenTelemetry tracing is disabled");
            return OpenTelemetry.noop();
        }
        
        Resource resource = Resource.getDefault()
            .merge(Resource.create(io.opentelemetry.api.common.Attributes.of(
                ResourceAttributes.SERVICE_NAME, serviceName
            )));
        
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .build();
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build();
        
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.noop())
            .build();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));
        
        log.info("OpenTelemetry tracing configured with endpoint: {}", otlpEndpoint);
        return openTelemetry;
    }
    
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, "1.0.0");
    }
}
