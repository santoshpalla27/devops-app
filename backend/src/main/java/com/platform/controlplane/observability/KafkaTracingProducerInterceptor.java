package com.platform.controlplane.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Kafka producer interceptor for tracing.
 * 
 * Creates spans for all Kafka produce operations with:
 * - Topic name
 * - Partition
 * - kafka.message_key
 * - chaos_experiment_id (if present)
 */
public class KafkaTracingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    
    private Tracer tracer;
    private String serviceName;
    
    @Override
    public void configure(Map<String, ?> configs) {
        // Tracer will be injected via config
        Object tracerObj = configs.get("otel.tracer");
        if (tracerObj instanceof Tracer) {
            this.tracer = (Tracer) tracerObj;
        }
        this.serviceName = (String) configs.getOrDefault("otel.service.name", "controlplane");
    }
    
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (tracer == null) {
            return record;
        }
        
        String spanName = "kafka.produce " + record.topic();
        
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute("messaging.system", "kafka")
            .setAttribute("messaging.destination", record.topic())
            .setAttribute("messaging.destination_kind", "topic")
            .startSpan();
        
        if (record.partition() != null) {
            span.setAttribute("messaging.kafka.partition", record.partition());
        }
        
        if (record.key() != null) {
            span.setAttribute("messaging.kafka.message_key", record.key().toString());
        }
        
        // Propagate chaos experiment ID from MDC
        String chaosExperimentId = MDC.get("chaos_experiment_id");
        if (chaosExperimentId != null) {
            span.setAttribute("chaos_experiment_id", chaosExperimentId);
        }
        
        // Store span in thread local for onAcknowledgement
        SpanHolder.set(span);
        
        return record;
    }
    
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        Span span = SpanHolder.get();
        if (span == null) {
            return;
        }
        
        try {
            if (exception != null) {
                span.setStatus(StatusCode.ERROR, exception.getMessage());
                span.recordException(exception);
            } else if (metadata != null) {
                span.setAttribute("messaging.kafka.partition", metadata.partition());
                span.setAttribute("messaging.kafka.offset", metadata.offset());
                span.setStatus(StatusCode.OK);
            }
        } finally {
            span.end();
            SpanHolder.clear();
        }
    }
    
    @Override
    public void close() {
        // No cleanup needed
    }
    
    /**
     * Thread-local holder for spans between onSend and onAcknowledgement.
     */
    private static class SpanHolder {
        private static final ThreadLocal<Span> holder = new ThreadLocal<>();
        
        static void set(Span span) {
            holder.set(span);
        }
        
        static Span get() {
            return holder.get();
        }
        
        static void clear() {
            holder.remove();
        }
    }
}
