package com.platform.controlplane.chaos;

/**
 * Exception thrown when a chaos fault intentionally causes a failure.
 * This is used for partial failure injection at the application level.
 */
public class ChaosInducedFailureException extends RuntimeException {
    
    public ChaosInducedFailureException(String message) {
        super(message);
    }
    
    public ChaosInducedFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
