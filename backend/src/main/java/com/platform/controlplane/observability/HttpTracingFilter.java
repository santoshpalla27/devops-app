package com.platform.controlplane.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * HTTP tracing filter.
 * 
 * Creates spans for all HTTP requests with:
 * - HTTP method, path, status
 * - request_id
 * - chaos_experiment_id (when applicable)
 * 
 * Propagates context and correlates logs.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)  // After rate limiting
public class HttpTracingFilter implements Filter {
    
    private final Tracer tracer;
    private final io.opentelemetry.api.OpenTelemetry openTelemetry;
    
    @Value("${otel.enabled:true}")
    private boolean enabled;
    
    // Extract context from HTTP headers
    private static final TextMapGetter<HttpServletRequest> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            return Collections.list(carrier.getHeaderNames());
        }
        
        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier.getHeader(key);
        }
    };
    
    public HttpTracingFilter(Tracer tracer, io.opentelemetry.api.OpenTelemetry openTelemetry) {
        this.tracer = tracer;
        this.openTelemetry = openTelemetry;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!enabled || !(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Extract parent context from headers
        Context extractedContext = openTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(), httpRequest, GETTER);
        
        // Generate request ID
        String requestId = httpRequest.getHeader("X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        
        // Extract chaos experiment ID if present
        String chaosExperimentId = httpRequest.getHeader("X-Chaos-Experiment-ID");
        if (chaosExperimentId == null) {
            // Check path for experiment ID
            String path = httpRequest.getRequestURI();
            if (path.contains("/chaos/") && path.contains("/recover/")) {
                chaosExperimentId = extractExperimentIdFromPath(path);
            }
        }
        
        String method = httpRequest.getMethod();
        String path = httpRequest.getRequestURI();
        String spanName = method + " " + getSpanName(path);
        
        // Create span
        Span span = tracer.spanBuilder(spanName)
            .setParent(extractedContext)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("http.method", method)
            .setAttribute("http.url", httpRequest.getRequestURL().toString())
            .setAttribute("http.path", path)
            .setAttribute("http.client_ip", getClientIp(httpRequest))
            .setAttribute("request_id", requestId)
            .startSpan();
        
        if (chaosExperimentId != null) {
            span.setAttribute("chaos_experiment_id", chaosExperimentId);
        }
        
        // Set MDC for log correlation
        String traceId = span.getSpanContext().getTraceId();
        String spanId = span.getSpanContext().getSpanId();
        
        MDC.put("trace_id", traceId);
        MDC.put("span_id", spanId);
        MDC.put("request_id", requestId);
        if (chaosExperimentId != null) {
            MDC.put("chaos_experiment_id", chaosExperimentId);
        }
        
        // Add trace ID to response headers
        httpResponse.setHeader("X-Trace-ID", traceId);
        httpResponse.setHeader("X-Request-ID", requestId);
        
        try (Scope scope = span.makeCurrent()) {
            chain.doFilter(request, response);
            
            // Set status
            int statusCode = httpResponse.getStatus();
            span.setAttribute("http.status_code", statusCode);
            
            if (statusCode >= 400 && statusCode < 500) {
                span.setStatus(StatusCode.ERROR, "Client error: " + statusCode);
            } else if (statusCode >= 500) {
                span.setStatus(StatusCode.ERROR, "Server error: " + statusCode);
            } else {
                span.setStatus(StatusCode.OK);
            }
            
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
            MDC.clear();
        }
    }
    
    private String getSpanName(String path) {
        // Normalize path for span names (remove IDs)
        return path
            .replaceAll("/[0-9a-f-]{8,36}", "/{id}")
            .replaceAll("/\\d+", "/{id}");
    }
    
    private String extractExperimentIdFromPath(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("recover".equals(parts[i])) {
                return parts[i + 1];
            }
        }
        return null;
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
