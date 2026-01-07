package com.platform.controlplane.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * WebSocket message tracing interceptor.
 * 
 * Creates spans for WebSocket messages with:
 * - messaging.destination
 * - messaging.operation
 * - session_id
 */
@Slf4j
@Component
public class WebSocketTracingInterceptor implements ChannelInterceptor {
    
    private final Tracer tracer;
    
    public WebSocketTracingInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (accessor.getCommand() == null) {
            return message;
        }
        
        String command = accessor.getCommand().name();
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        
        String spanName = "ws." + command.toLowerCase();
        if (destination != null) {
            spanName += " " + destination;
        }
        
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("messaging.system", "websocket")
            .setAttribute("messaging.operation", command)
            .startSpan();
        
        if (destination != null) {
            span.setAttribute("messaging.destination", destination);
        }
        
        if (sessionId != null) {
            span.setAttribute("session_id", sessionId);
        }
        
        // Add trace info to MDC
        MDC.put("trace_id", span.getSpanContext().getTraceId());
        MDC.put("span_id", span.getSpanContext().getSpanId());
        MDC.put("ws_session_id", sessionId);
        
        // Store span for postSend
        accessor.setHeader("otel.span", span);
        
        return message;
    }
    
    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        Object spanObj = accessor.getHeader("otel.span");
        
        if (spanObj instanceof Span span) {
            try {
                if (sent) {
                    span.setStatus(StatusCode.OK);
                } else {
                    span.setStatus(StatusCode.ERROR, "Message not sent");
                }
            } finally {
                span.end();
                MDC.remove("trace_id");
                MDC.remove("span_id");
                MDC.remove("ws_session_id");
            }
        }
    }
    
    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (ex != null) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            Object spanObj = accessor.getHeader("otel.span");
            
            if (spanObj instanceof Span span) {
                span.setStatus(StatusCode.ERROR, ex.getMessage());
                span.recordException(ex);
                span.end();
            }
        }
    }
}
