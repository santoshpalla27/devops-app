package com.platform.controlplane.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for safe string input.
 * Prevents injection attacks and malformed data.
 */
@Documented
@Constraint(validatedBy = SafeStringValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeString {
    
    String message() default "Invalid characters in input";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Maximum allowed length.
     */
    int maxLength() default 255;
    
    /**
     * Whether to allow newlines.
     */
    boolean allowNewlines() default false;
    
    /**
     * Whether to allow HTML/script tags.
     */
    boolean allowHtml() default false;
}
