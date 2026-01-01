package com.platform.controlplane.chaos;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configuration for chaos engineering components.
 */
@Configuration
public class ChaosConfig {
    
    /**
     * Register all fault injectors as a map for easy lookup.
     */
    @Bean
    public Map<String, FaultInjector> faultInjectorsMap(List<FaultInjector> injectors) {
        return injectors.stream()
            .collect(Collectors.toMap(
                FaultInjector::getSystemType,
                Function.identity()
            ));
    }
}
