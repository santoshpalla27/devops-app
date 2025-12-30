package com.platform.controlplane.api;

import com.platform.controlplane.core.OrchestratorService;
import com.platform.controlplane.model.SystemHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller for handling client messages and broadcasting updates.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {
    
    private final OrchestratorService orchestratorService;
    
    /**
     * Handle client request for current health status.
     */
    @MessageMapping("/health")
    @SendTo("/topic/health")
    public SystemHealth requestHealth() {
        log.debug("WebSocket: Client requested health status");
        return orchestratorService.getSystemHealth();
    }
    
    /**
     * Handle client request for topology refresh.
     */
    @MessageMapping("/refresh")
    @SendTo("/topic/health")
    public SystemHealth requestRefresh() {
        log.debug("WebSocket: Client requested topology refresh");
        orchestratorService.forceTopologyRefresh();
        return orchestratorService.getSystemHealth();
    }
}
