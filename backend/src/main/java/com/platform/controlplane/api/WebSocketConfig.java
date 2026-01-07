package com.platform.controlplane.api;

import com.platform.controlplane.error.WebSocketExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket configuration for real-time dashboard updates.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Value("${CORS_ALLOWED_ORIGINS:*}")
    private String allowedOrigins;
    
    private final WebSocketExceptionHandler webSocketExceptionHandler;
    
    public WebSocketConfig(WebSocketExceptionHandler webSocketExceptionHandler) {
        this.webSocketExceptionHandler = webSocketExceptionHandler;
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker
        // Clients subscribe to /topic/* destinations
        config.enableSimpleBroker("/topic");
        
        // Application destination prefix for messages from clients
        config.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint for STOMP connections
        // CORS origins controlled via environment variable
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(allowedOrigins.split(","))
            .withSockJS();
        
        // Also allow direct WebSocket without SockJS
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(allowedOrigins.split(","));
        
        // Register error handler for safe session closure
        registry.setErrorHandler(webSocketExceptionHandler);
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Configure transport settings
        registration.setMessageSizeLimit(64 * 1024); // 64KB max message
        registration.setSendBufferSizeLimit(512 * 1024); // 512KB send buffer
        registration.setSendTimeLimit(20000); // 20 second send timeout
    }
}
