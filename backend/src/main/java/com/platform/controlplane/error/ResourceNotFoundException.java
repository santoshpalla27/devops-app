package com.platform.controlplane.error;

/**
 * Exception for resource not found errors.
 */
public class ResourceNotFoundException extends ControlPlaneException {
    
    private final String resourceType;
    private final String resourceId;
    
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(ErrorCode.RESOURCE_NOT_FOUND, 
            String.format("%s not found: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    public ResourceNotFoundException(ErrorCode errorCode, String resourceType, String resourceId) {
        super(errorCode, 
            String.format("%s not found: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getResourceId() {
        return resourceId;
    }
}
