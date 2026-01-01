package com.platform.controlplane.policy;

import com.platform.controlplane.connectors.kafka.KafkaEventProducer;
import com.platform.controlplane.connectors.mysql.MySQLConnector;
import com.platform.controlplane.connectors.redis.RedisConnector;
import com.platform.controlplane.model.FailureEvent;
import com.platform.controlplane.state.SystemState;
import com.platform.controlplane.state.SystemStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Executes policy actions with safety checks and audit logging.
 */
@Slf4j
@Component
public class ActionExecutor {
    
    private final MySQLConnector mysqlConnector;
    private final RedisConnector redisConnector;
    private final KafkaEventProducer kafkaProducer;
    private final SystemStateMachine stateMachine;
    
    public ActionExecutor(
            MySQLConnector mysqlConnector,
            RedisConnector redisConnector,
            KafkaEventProducer kafkaProducer,
            SystemStateMachine stateMachine) {
        this.mysqlConnector = mysqlConnector;
        this.redisConnector = redisConnector;
        this.kafkaProducer = kafkaProducer;
        this.stateMachine = stateMachine;
    }
    
    /**
     * Execute a policy action for a specific system.
     * Returns execution record with success status and message.
     */
    public PolicyExecutionRecord execute(Policy policy, String systemType) {
        long startTime = System.currentTimeMillis();
        log.info("Executing policy action: {} for system: {}", policy.getAction(), systemType);
        
        try {
            boolean success = switch (policy.getAction()) {
                case FORCE_RECONNECT -> executeReconnect(systemType);
                case OPEN_CIRCUIT -> executeOpenCircuit(systemType);
                case CLOSE_CIRCUIT -> executeCloseCircuit(systemType);
                case EMIT_ALERT -> executeEmitAlert(policy, systemType);
                case MARK_DEGRADED -> executeMarkDegraded(systemType);
                case NO_ACTION -> true; // No-op
            };
            
            long duration = System.currentTimeMillis() - startTime;
            
            return PolicyExecutionRecord.builder()
                .policyId(policy.getId())
                .policyName(policy.getName())
                .systemType(systemType)
                .action(policy.getAction())
                .success(success)
                .message(success ? "Action executed successfully" : "Action failed")
                .durationMs(duration)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to execute policy action: {}", e.getMessage(), e);
            long duration = System.currentTimeMillis() - startTime;
            
            return PolicyExecutionRecord.builder()
                .policyId(policy.getId())
                .policyName(policy.getName())
                .systemType(systemType)
                .action(policy.getAction())
                .success(false)
                .message("Error: " + e.getMessage())
                .durationMs(duration)
                .build();
        }
    }
    
    private boolean executeReconnect(String systemType) {
        log.info("Executing FORCE_RECONNECT for {}", systemType);
        
        boolean result = switch (systemType.toLowerCase()) {
            case "mysql" -> mysqlConnector.reconnect();
            case "redis" -> redisConnector.reconnect();
            default -> {
                log.warn("Unknown system type for reconnect: {}", systemType);
                yield false;
            }
        };
        
        if (result) {
            kafkaProducer.emit(FailureEvent.create(
                FailureEvent.EventType.SYSTEM_RECONNECT,
                systemType,
                "Policy triggered reconnection"
            ));
        }
        
        return result;
    }
    
    private boolean executeOpenCircuit(String systemType) {
        log.info("Executing OPEN_CIRCUIT for {}", systemType);
        
        stateMachine.transition(systemType, SystemState.CIRCUIT_OPEN, 
            "Policy-triggered circuit breaker");
        
        kafkaProducer.emitCircuitBreakerOpened(systemType);
        return true;
    }
    
    private boolean executeCloseCircuit(String systemType) {
        log.info("Executing CLOSE_CIRCUIT for {}", systemType);
        
        stateMachine.transition(systemType, SystemState.RECOVERING, 
            "Policy-triggered circuit recovery");
        
        kafkaProducer.emitCircuitBreakerClosed(systemType);
        return true;
    }
    
    private boolean executeEmitAlert(Policy policy, String systemType) {
        log.info("Executing EMIT_ALERT for {}", systemType);
        
        kafkaProducer.emit(FailureEvent.create(
            FailureEvent.EventType.SYSTEM_ALERT,
            systemType,
            String.format("Policy '%s' triggered alert: %s", 
                policy.getName(), policy.getCondition().describe())
        ));
        
        return true;
    }
    
    private boolean executeMarkDegraded(String systemType) {
        log.info("Executing MARK_DEGRADED for {}", systemType);
        
        stateMachine.transition(systemType, SystemState.DEGRADED, 
            "Policy-triggered degradation");
        
        return true;
    }
}
