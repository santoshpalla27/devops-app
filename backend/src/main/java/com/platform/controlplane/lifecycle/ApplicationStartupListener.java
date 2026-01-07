package com.platform.controlplane.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Startup listener that marks application as ready when fully initialized.
 */
@Slf4j
@Component
public class ApplicationStartupListener {
    
    private final ApplicationLifecycleManager lifecycleManager;
    
    public ApplicationStartupListener(ApplicationLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("Application startup complete, marking as ready");
        lifecycleManager.markReady();
    }
}
