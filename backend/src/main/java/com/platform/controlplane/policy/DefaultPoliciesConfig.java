package com.platform.controlplane.policy;

import com.platform.controlplane.policy.PolicyCondition.StateCondition;
import com.platform.controlplane.state.SystemState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration class that sets up default policies.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DefaultPoliciesConfig {
    
    private final PolicyRepository policyRepository;
    
    @PostConstruct
    public void initializeDefaultPolicies() {
        log.info("Initializing default policies...");
        
        // Policy 1: Open circuit after 5 consecutive retries for MySQL
        Policy mysqlCircuitOpen = Policy.builder()
            .name("mysql-circuit-open-on-retries")
            .systemType("mysql")
            .condition(new StateCondition(
                SystemState.RETRYING,
                null,
                5,  // retryCountThreshold
                null
            ))
            .action(PolicyAction.OPEN_CIRCUIT)
            .severity(PolicySeverity.HIGH)
            .cooldownSeconds(300)
            .description("Open circuit breaker after 5 consecutive retries to prevent cascade failures")
            .build();
        
        // Policy 2: Open circuit after 5 consecutive retries for Redis
        Policy redisCircuitOpen = Policy.builder()
            .name("redis-circuit-open-on-retries")
            .systemType("redis")
            .condition(new StateCondition(
                SystemState.RETRYING,
                null,
                5,  // retryCountThreshold
                null
            ))
            .action(PolicyAction.OPEN_CIRCUIT)
            .severity(PolicySeverity.HIGH)
            .cooldownSeconds(300)
            .description("Open circuit breaker after 5 consecutive retries")
            .build();
        
        // Policy 3: Emit alert on degraded state for 60 seconds
        Policy degradedAlert = Policy.builder()
            .name("alert-on-degraded")
            .systemType("*")  // All systems
            .condition(new StateCondition(
                SystemState.DEGRADED,
                60L,  // durationSeconds
                null,
                null
            ))
            .action(PolicyAction.EMIT_ALERT)
            .severity(PolicySeverity.MEDIUM)
            .cooldownSeconds(600)
            .description("Alert when system remains degraded for 60 seconds")
            .build();
        
        // Policy 4: Reconnect after circuit open for 120 seconds (recovery attempt)
        Policy circuitRecovery = Policy.builder()
            .name("circuit-recovery-attempt")
            .systemType("*")
            .condition(new StateCondition(
                SystemState.CIRCUIT_OPEN,
                120L,  // durationSeconds
                null,
                null
            ))
            .action(PolicyAction.CLOSE_CIRCUIT)
            .severity(PolicySeverity.MEDIUM)
            .cooldownSeconds(180)
            .description("Attempt recovery by closing circuit after 2 minutes")
            .build();
        
        // Policy 5: Critical alert on high consecutive failures
        Policy criticalFailures = Policy.builder()
            .name("critical-consecutive-failures")
            .systemType("*")
            .condition(new StateCondition(
                SystemState.DEGRADED,
                null,
                null,
                10  // consecutiveFailuresThreshold
            ))
            .action(PolicyAction.EMIT_ALERT)
            .severity(PolicySeverity.CRITICAL)
            .cooldownSeconds(300)
            .description("Critical alert when 10 consecutive failures occur")
            .enabled(true)
            .build();
        
        // Save policies only if they don't already exist
        List<Policy> defaultPolicies = List.of(
            mysqlCircuitOpen, redisCircuitOpen, degradedAlert, circuitRecovery, criticalFailures
        );
        
        int created = 0;
        for (Policy policy : defaultPolicies) {
            if (policyRepository.findByName(policy.getName()).isEmpty()) {
                policyRepository.save(policy);
                created++;
                log.debug("Created policy: {}", policy.getName());
            } else {
                log.debug("Policy already exists: {}", policy.getName());
            }
        }
        
        log.info("Default policies initialized: {} created, {} total", created, policyRepository.count());
    }
}

