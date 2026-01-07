package com.platform.controlplane.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration with explicit allowed origins.
 * 
 * NO WILDCARD CORS - only explicitly configured origins allowed.
 */
@Slf4j
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Value("${controlplane.security.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;
    
    @Value("${controlplane.security.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethods;
    
    @Value("${controlplane.security.cors.allowed-headers:*}")
    private String allowedHeaders;
    
    @Value("${controlplane.security.cors.max-age-seconds:3600}")
    private long maxAgeSeconds;
    
    @Value("${controlplane.security.cors.allow-credentials:true}")
    private boolean allowCredentials;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = parseOrigins();
        
        log.info("Configuring CORS for origins: {}", origins);
        
        // Validate no wildcards
        if (origins.contains("*")) {
            log.error("SECURITY: Wildcard CORS is NOT allowed. Ignoring '*' origin.");
            origins = origins.stream().filter(o -> !o.equals("*")).toList();
        }
        
        registry.addMapping("/api/**")
            .allowedOrigins(origins.toArray(new String[0]))
            .allowedMethods(allowedMethods.split(","))
            .allowedHeaders(allowedHeaders.split(","))
            .allowCredentials(allowCredentials)
            .maxAge(maxAgeSeconds);
        
        registry.addMapping("/ws/**")
            .allowedOrigins(origins.toArray(new String[0]))
            .allowedMethods("GET", "POST")
            .allowCredentials(allowCredentials)
            .maxAge(maxAgeSeconds);
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = parseOrigins();
        
        // Filter out wildcards
        origins = origins.stream().filter(o -> !o.equals("*")).toList();
        
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAgeSeconds);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/ws/**", configuration);
        
        return source;
    }
    
    private List<String> parseOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
