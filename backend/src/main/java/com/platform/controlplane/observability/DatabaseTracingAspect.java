package com.platform.controlplane.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Database operation tracing aspect.
 * 
 * Creates spans for all JPA repository operations with:
 * - db.system = mysql
 * - db.operation (method name)
 * - db.statement (when applicable)
 */
@Slf4j
@Aspect
@Component
public class DatabaseTracingAspect {
    
    private final Tracer tracer;
    
    public DatabaseTracingAspect(Tracer tracer) {
        this.tracer = tracer;
    }
    
    @Around("execution(* org.springframework.data.repository.Repository+.*(..))")
    public Object traceRepositoryMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String spanName = "db." + className + "." + methodName;
        
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("db.system", "mysql")
            .setAttribute("db.operation", methodName)
            .setAttribute("db.repository", className)
            .startSpan();
        
        // Add chaos experiment ID if present
        String chaosExperimentId = MDC.get("chaos_experiment_id");
        if (chaosExperimentId != null) {
            span.setAttribute("chaos_experiment_id", chaosExperimentId);
        }
        
        // Add request ID if present
        String requestId = MDC.get("request_id");
        if (requestId != null) {
            span.setAttribute("request_id", requestId);
        }
        
        try {
            Object result = joinPoint.proceed();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
    
    @Around("execution(* javax.sql.DataSource.getConnection(..))")
    public Object traceConnectionAcquisition(ProceedingJoinPoint joinPoint) throws Throwable {
        Span span = tracer.spanBuilder("db.getConnection")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("db.system", "mysql")
            .setAttribute("db.operation", "getConnection")
            .startSpan();
        
        try {
            Object result = joinPoint.proceed();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
