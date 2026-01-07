package com.platform.controlplane.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application lifecycle state manager.
 * 
 * Tracks application phases:
 * - STARTING: Application is initializing
 * - READY: Application is ready to serve requests
 * - DRAINING: Application is draining (shutdown initiated)
 * - STOPPED: Application is stopped
 * 
 * Integrates with Kubernetes liveness/readiness probes.
 */
@Slf4j
@Component
public class ApplicationLifecycleManager {
    
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicReference<LifecyclePhase> currentPhase = new AtomicReference<>(LifecyclePhase.STARTING);
    private Instant phaseStartTime = Instant.now();
    
    @Value("${controlplane.lifecycle.drain-period-seconds:30}")
    private int drainPeriodSeconds;
    
    public ApplicationLifecycleManager(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        log.info("Application lifecycle manager initialized");
    }
    
    /**
     * Mark application as ready.
     */
    public void markReady() {
        if (currentPhase.compareAndSet(LifecyclePhase.STARTING, LifecyclePhase.READY)) {
            phaseStartTime = Instant.now();
            log.info("Application marked READY");
            
            // Publish readiness
            eventPublisher.publishEvent(
                AvailabilityChangeEvent.publish(eventPublisher, ReadinessState.ACCEPTING_TRAFFIC)
            );
        }
    }
    
    /**
     * Start draining (pre-shutdown).
     */
    public void startDraining() {
        LifecyclePhase previous = currentPhase.getAndSet(LifecyclePhase.DRAINING);
        if (previous != LifecyclePhase.DRAINING) {
            phaseStartTime = Instant.now();
            log.info("Application entering DRAINING phase (was {})", previous);
            
            // Publish not ready for new traffic
            eventPublisher.publishEvent(
                AvailabilityChangeEvent.publish(eventPublisher, ReadinessState.REFUSING_TRAFFIC)
            );
        }
    }
    
    /**
     * Mark as stopped.
     */
    public void markStopped() {
        LifecyclePhase previous = currentPhase.getAndSet(LifecyclePhase.STOPPED);
        phaseStartTime = Instant.now();
        log.info("Application marked STOPPED (was {})", previous);
        
        // Publish broken liveness
        eventPublisher.publishEvent(
            AvailabilityChangeEvent.publish(eventPublisher, LivenessState.BROKEN)
        );
    }
    
    /**
     * Get current lifecycle phase.
     */
    public LifecyclePhase getCurrentPhase() {
        return currentPhase.get();
    }
    
    /**
     * Check if application is ready to serve requests.
     */
    public boolean isReady() {
        return currentPhase.get() == LifecyclePhase.READY;
    }
    
    /**
     * Check if application is draining (shutdown in progress).
     */
    public boolean isDraining() {
        return currentPhase.get() == LifecyclePhase.DRAINING;
    }
    
    /**
     * Get time in current phase.
     */
    public long getTimeInCurrentPhaseMs() {
        return Instant.now().toEpochMilli() - phaseStartTime.toEpochMilli();
    }
    
    /**
     * Get lifecycle status for health endpoints.
     */
    public LifecycleStatus getStatus() {
        return new LifecycleStatus(
            currentPhase.get(),
            phaseStartTime,
            getTimeInCurrentPhaseMs()
        );
    }
    
    public enum LifecyclePhase {
        STARTING,
        READY,
        DRAINING,
        STOPPED
    }
    
    public record LifecycleStatus(
        LifecyclePhase phase,
        Instant phaseStartTime,
        long durationMs
    ) {}
}
