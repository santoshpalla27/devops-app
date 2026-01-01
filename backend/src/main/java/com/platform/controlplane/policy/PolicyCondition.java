package com.platform.controlplane.policy;

import com.platform.controlplane.state.SystemState;
import com.platform.controlplane.state.SystemStateContext;

/**
 * Interface for policy conditions that determine when a policy should trigger.
 */
public interface PolicyCondition {
    
    /**
     * Evaluates if this condition is met given the current state context.
     */
    boolean evaluate(SystemStateContext context);
    
    /**
     * Returns a human-readable description of this condition.
     */
    String describe();
    
    /**
     * State-based condition - triggers when system is in specific state for duration.
     */
    record StateCondition(
        SystemState state,
        Long durationSeconds,
        Integer retryCountThreshold,
        Integer consecutiveFailuresThreshold
    ) implements PolicyCondition {
        
        @Override
        public boolean evaluate(SystemStateContext context) {
            // Check state match
            if (context.currentState() != state) {
                return false;
            }
            
            // Check duration if specified
            if (durationSeconds != null && !context.isStableFor(durationSeconds)) {
                return false;
            }
            
            // Check retry count if specified
            if (retryCountThreshold != null && context.retryCount() < retryCountThreshold) {
                return false;
            }
            
            // Check consecutive failures if specified
            if (consecutiveFailuresThreshold != null && 
                context.consecutiveFailures() < consecutiveFailuresThreshold) {
                return false;
            }
            
            return true;
        }
        
        @Override
        public String describe() {
            StringBuilder sb = new StringBuilder("State is " + state);
            if (durationSeconds != null) {
                sb.append(" for ").append(durationSeconds).append("s");
            }
            if (retryCountThreshold != null) {
                sb.append(", retries >= ").append(retryCountThreshold);
            }
            if (consecutiveFailuresThreshold != null) {
                sb.append(", consecutive failures >= ").append(consecutiveFailuresThreshold);
            }
            return sb.toString();
        }
    }
    
    /**
     * Latency-based condition - triggers when latency exceeds threshold.
     */
    record LatencyCondition(
        long thresholdMs
    ) implements PolicyCondition {
        
        @Override
        public boolean evaluate(SystemStateContext context) {
            return context.latencyMs() > thresholdMs;
        }
        
        @Override
        public String describe() {
            return "Latency > " + thresholdMs + "ms";
        }
    }
    
    /**
     * Combined condition - all sub-conditions must be true (AND logic).
     */
    record AndCondition(
        PolicyCondition... conditions
    ) implements PolicyCondition {
        
        @Override
        public boolean evaluate(SystemStateContext context) {
            for (PolicyCondition condition : conditions) {
                if (!condition.evaluate(context)) {
                    return false;
                }
            }
            return true;
        }
        
        @Override
        public String describe() {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < conditions.length; i++) {
                if (i > 0) sb.append(" AND ");
                sb.append(conditions[i].describe());
            }
            sb.append(")");
            return sb.toString();
        }
    }
}
