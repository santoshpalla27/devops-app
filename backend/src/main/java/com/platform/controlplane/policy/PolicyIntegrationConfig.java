package com.platform.controlplane.policy;

import com.platform.controlplane.state.SystemStateMachine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to wire policy evaluator into state machine (lazy to avoid circular dependency).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PolicyIntegrationConfig {
    
    private final SystemStateMachine stateMachine;
    private final PolicyEvaluator policyEvaluator;
    
    @PostConstruct
    public void wirePolicyEvaluator() {
        log.info("Wiring policy evaluator into state machine");
        stateMachine.setPolicyEvaluator(policyEvaluator);
    }
}
