package com.platform.controlplane.security;

import com.platform.controlplane.chaos.FaultType;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Validated request DTOs for sensitive operations.
 * All fields use @SafeString or standard validators.
 */
public class ValidatedRequests {
    
    /**
     * Validated chaos injection request.
     */
    @Data
    public static class ChaosInjectRequest {
        
        @NotBlank(message = "System type is required")
        @Pattern(regexp = "^(mysql|redis|kafka)$", message = "Invalid system type")
        private String systemType;
        
        @NotNull(message = "Fault type is required")
        private FaultType faultType;
        
        @Min(value = 1, message = "Duration must be at least 1 second")
        @Max(value = 3600, message = "Duration cannot exceed 1 hour")
        private int durationSeconds = 30;
        
        @Min(value = 0, message = "Intensity must be non-negative")
        @Max(value = 100, message = "Intensity cannot exceed 100")
        private int intensity = 50;
        
        @SafeString(maxLength = 500)
        private String description;
    }
    
    /**
     * Validated policy create/update request.
     */
    @Data
    public static class PolicyRequest {
        
        @NotBlank(message = "Policy name is required")
        @Size(min = 3, max = 100, message = "Name must be 3-100 characters")
        @SafeString(maxLength = 100)
        private String name;
        
        @NotBlank(message = "System type is required")
        @Pattern(regexp = "^(mysql|redis|kafka|\\*)$", message = "Invalid system type")
        private String systemType;
        
        @SafeString(maxLength = 500, allowNewlines = true)
        private String description;
        
        @Min(value = 0, message = "Cooldown must be non-negative")
        @Max(value = 86400, message = "Cooldown cannot exceed 24 hours")
        private Long cooldownSeconds;
        
        private Boolean enabled;
    }
    
    /**
     * Validated experiment ID path parameter.
     */
    @Data
    public static class ExperimentIdParam {
        
        @NotBlank(message = "Experiment ID is required")
        @Pattern(regexp = "^[a-zA-Z0-9-]{1,64}$", message = "Invalid experiment ID format")
        private String experimentId;
    }
    
    /**
     * Validated policy ID path parameter.
     */
    @Data
    public static class PolicyIdParam {
        
        @NotBlank(message = "Policy ID is required")
        @Pattern(regexp = "^[a-zA-Z0-9-]{1,64}$", message = "Invalid policy ID format")
        private String policyId;
    }
    
    /**
     * Validated system type path parameter.
     */
    @Data
    public static class SystemTypeParam {
        
        @NotBlank(message = "System type is required")
        @Pattern(regexp = "^(mysql|redis|kafka)$", message = "Invalid system type")
        private String systemType;
    }
}
