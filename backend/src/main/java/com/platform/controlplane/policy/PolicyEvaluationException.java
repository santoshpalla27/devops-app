package com.platform.controlplane.policy;

/**
 * Exception thrown when policy evaluation fails after retries.
 */
public class PolicyEvaluationException extends RuntimeException {
    
    public PolicyEvaluationException(String message) {
        super(message);
    }
    
    public PolicyEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
