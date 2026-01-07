package com.platform.controlplane.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket error handler that safely closes sessions on error.
 * 
 * Converts exceptions to structured error messages before closing.
 * Ensures sessions are properly cleaned up.
 */
@Slf4j
@Component
public class WebSocketExceptionHandler extends StompSubProtocolErrorHandler {
    
    private final ObjectMapper objectMapper;
    
    public WebSocketExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage, Throwable ex) {
        
        String sessionId = extractSessionId(clientMessage);
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        
        MDC.put("traceId", traceId);
        MDC.put("wsSessionId", sessionId);
        
        try {
            // Determine error type
            ErrorCode errorCode;
            String message;
            boolean fatal;
            
            if (ex instanceof ControlPlaneException cpe) {
                errorCode = cpe.getErrorCode();
                message = cpe.getMessage();
                fatal = cpe.isFatal();
            } else if (ex instanceof MessageDeliveryException) {
                errorCode = ErrorCode.INTERNAL_ERROR;
                message = "Failed to deliver message";
                fatal = false;
            } else {
                errorCode = ErrorCode.UNEXPECTED_ERROR;
                message = "WebSocket error: " + ex.getMessage();
                fatal = true;
            }
            
            // Log appropriately
            if (fatal) {
                log.error("[{}] FATAL WebSocket error (session={}): {}", 
                    traceId, sessionId, message, ex);
            } else {
                log.warn("[{}] WebSocket error (session={}): {}", 
                    traceId, sessionId, message);
            }
            
            // Build error response
            WebSocketErrorResponse errorResponse = new WebSocketErrorResponse(
                errorCode.getCode(),
                message,
                fatal,
                traceId,
                Instant.now().toString()
            );
            
            // Create ERROR frame
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
            headerAccessor.setMessage(errorCode.getCode() + ": " + message);
            headerAccessor.setLeaveMutable(true);
            
            String json = objectMapper.writeValueAsString(errorResponse);
            
            return MessageBuilder.createMessage(
                json.getBytes(StandardCharsets.UTF_8),
                headerAccessor.getMessageHeaders()
            );
            
        } catch (Exception jsonEx) {
            log.error("[{}] Failed to create WebSocket error response", traceId, jsonEx);
            return super.handleClientMessageProcessingError(clientMessage, ex);
        } finally {
            MDC.clear();
        }
    }
    
    @Override
    public Message<byte[]> handleErrorMessageToClient(Message<byte[]> errorMessage) {
        // Log outgoing error messages
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(errorMessage);
        String message = accessor.getMessage();
        String sessionId = accessor.getSessionId();
        
        log.warn("Sending error to WebSocket client (session={}): {}", sessionId, message);
        
        return super.handleErrorMessageToClient(errorMessage);
    }
    
    private String extractSessionId(Message<?> message) {
        if (message == null) return "unknown";
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String sessionId = accessor.getSessionId();
        return sessionId != null ? sessionId : "unknown";
    }
    
    /**
     * WebSocket error response payload.
     */
    public record WebSocketErrorResponse(
        String code,
        String message,
        boolean fatal,
        String traceId,
        String timestamp
    ) {}
}
