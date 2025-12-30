package com.platform.controlplane.observability;

import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.context.annotation.Bean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Logging configuration for structured JSON logs with correlation IDs.
 */
@Slf4j
@Configuration
public class LoggingConfig {
    
    @Value("${spring.application.name:devops-control-plane}")
    private String applicationName;
    
    @Value("${logging.structured:true}")
    private boolean structuredLogging;
    
    @PostConstruct
    public void init() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.putProperty("application", applicationName);
        
        log.info("Logging configuration initialized for application: {}", applicationName);
    }
    
    /**
     * Filter to add correlation ID to all requests.
     */
    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
    
    public static class CorrelationIdFilter extends OncePerRequestFilter {
        
        private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
        private static final String MDC_CORRELATION_ID = "correlationId";
        private static final String MDC_REQUEST_PATH = "requestPath";
        private static final String MDC_REQUEST_METHOD = "requestMethod";
        
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            
            try {
                // Get or generate correlation ID
                String correlationId = request.getHeader(CORRELATION_ID_HEADER);
                if (correlationId == null || correlationId.isBlank()) {
                    correlationId = UUID.randomUUID().toString();
                }
                
                // Set MDC values
                MDC.put(MDC_CORRELATION_ID, correlationId);
                MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
                MDC.put(MDC_REQUEST_METHOD, request.getMethod());
                
                // Add correlation ID to response header
                response.setHeader(CORRELATION_ID_HEADER, correlationId);
                
                filterChain.doFilter(request, response);
                
            } finally {
                MDC.remove(MDC_CORRELATION_ID);
                MDC.remove(MDC_REQUEST_PATH);
                MDC.remove(MDC_REQUEST_METHOD);
            }
        }
    }
    
    /**
     * Set topology information in MDC for logging.
     */
    public static void setTopologyContext(String system, String topology) {
        MDC.put("system", system);
        MDC.put("topology", topology);
    }
    
    /**
     * Clear topology context from MDC.
     */
    public static void clearTopologyContext() {
        MDC.remove("system");
        MDC.remove("topology");
    }
    
    /**
     * Set operation context for detailed logging.
     */
    public static void setOperationContext(String operation, String target) {
        MDC.put("operation", operation);
        MDC.put("target", target);
    }
    
    /**
     * Clear operation context.
     */
    public static void clearOperationContext() {
        MDC.remove("operation");
        MDC.remove("target");
    }
}
