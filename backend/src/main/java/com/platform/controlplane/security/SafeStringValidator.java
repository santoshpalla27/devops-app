package com.platform.controlplane.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator for SafeString annotation.
 * Validates input against injection and malformed data.
 */
public class SafeStringValidator implements ConstraintValidator<SafeString, String> {
    
    // Dangerous patterns
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('|--|;|/\\*|\\*/|@@|@|char|nchar|varchar|nvarchar|alter|begin|cast|create|cursor|declare|delete|drop|end|exec|execute|fetch|insert|kill|open|select|sys|sysobjects|syscolumns|table|update)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_INJECTION_PATTERN = Pattern.compile(
        "[;&|`$(){}\\[\\]<>\\\\]");
    
    private int maxLength;
    private boolean allowNewlines;
    private boolean allowHtml;
    
    @Override
    public void initialize(SafeString constraintAnnotation) {
        this.maxLength = constraintAnnotation.maxLength();
        this.allowNewlines = constraintAnnotation.allowNewlines();
        this.allowHtml = constraintAnnotation.allowHtml();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Use @NotNull for null checks
        }
        
        // Check length
        if (value.length() > maxLength) {
            setMessage(context, "Input exceeds maximum length of " + maxLength);
            return false;
        }
        
        // Check for script tags (always blocked)
        if (SCRIPT_PATTERN.matcher(value).find()) {
            setMessage(context, "Script tags are not allowed");
            return false;
        }
        
        // Check for HTML tags
        if (!allowHtml && HTML_TAG_PATTERN.matcher(value).find()) {
            setMessage(context, "HTML tags are not allowed");
            return false;
        }
        
        // Check for newlines
        if (!allowNewlines && (value.contains("\n") || value.contains("\r"))) {
            setMessage(context, "Newlines are not allowed");
            return false;
        }
        
        // Check for SQL injection patterns
        if (SQL_INJECTION_PATTERN.matcher(value).find()) {
            setMessage(context, "Input contains suspicious SQL patterns");
            return false;
        }
        
        // Check for shell injection patterns
        if (SHELL_INJECTION_PATTERN.matcher(value).find()) {
            setMessage(context, "Input contains dangerous shell characters");
            return false;
        }
        
        return true;
    }
    
    private void setMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
