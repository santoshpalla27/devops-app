package com.platform.controlplane.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.controlplane.chaos.ChaosExperiment;
import com.platform.controlplane.persistence.entity.ChaosExperimentEntity;
import com.platform.controlplane.persistence.entity.DesiredSystemStateEntity;
import com.platform.controlplane.persistence.entity.PolicyEntity;
import com.platform.controlplane.persistence.entity.PolicyExecutionRecordEntity;
import com.platform.controlplane.policy.Policy;
import com.platform.controlplane.policy.PolicyCondition;
import com.platform.controlplane.policy.PolicyCondition.*;
import com.platform.controlplane.policy.PolicyExecutionRecord;
import com.platform.controlplane.reconciliation.DesiredSystemState;
import com.platform.controlplane.state.SystemState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bidirectional mappers between domain objects and JPA entities.
 */
@Slf4j
@Component
public class EntityMappers {
    
    private final ObjectMapper objectMapper;
    
    public EntityMappers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    // ==================== ChaosExperiment ====================
    
    public ChaosExperimentEntity toEntity(ChaosExperiment domain) {
        return ChaosExperimentEntity.builder()
            .id(domain.getId())
            .name(domain.getName())
            .systemType(domain.getSystemType())
            .faultType(domain.getFaultType())
            .durationSeconds(domain.getDurationSeconds())
            .latencyMs(domain.getLatencyMs())
            .failureRatePercent(domain.getFailureRatePercent())
            .description(domain.getDescription())
            .status(domain.getStatus())
            .createdAt(domain.getCreatedAt())
            .startedAt(domain.getStartedAt())
            .endedAt(domain.getEndedAt())
            .scheduledEndAt(calculateScheduledEndAt(domain))
            .result(domain.getResult())
            .build();
    }
    
    public ChaosExperiment toDomain(ChaosExperimentEntity entity) {
        return ChaosExperiment.builder()
            .id(entity.getId())
            .name(entity.getName())
            .systemType(entity.getSystemType())
            .faultType(entity.getFaultType())
            .durationSeconds(entity.getDurationSeconds())
            .latencyMs(entity.getLatencyMs())
            .failureRatePercent(entity.getFailureRatePercent())
            .description(entity.getDescription())
            .status(entity.getStatus())
            .createdAt(entity.getCreatedAt())
            .startedAt(entity.getStartedAt())
            .endedAt(entity.getEndedAt())
            .result(entity.getResult())
            .build();
    }
    
    private java.time.Instant calculateScheduledEndAt(ChaosExperiment domain) {
        if (domain.getStartedAt() != null && domain.getDurationSeconds() > 0) {
            return domain.getStartedAt().plusSeconds(domain.getDurationSeconds());
        }
        return null;
    }
    
    // ==================== Policy ====================
    
    public PolicyEntity toEntity(Policy domain) {
        String conditionType = getConditionType(domain.getCondition());
        String conditionJson = serializeCondition(domain.getCondition());
        
        return PolicyEntity.builder()
            .id(domain.getId())
            .name(domain.getName())
            .systemType(domain.getSystemType())
            .conditionType(conditionType)
            .conditionJson(conditionJson)
            .action(domain.getAction())
            .severity(domain.getSeverity())
            .enabled(domain.isEnabled())
            .cooldownSeconds(domain.getCooldownSeconds())
            .description(domain.getDescription())
            .createdAt(domain.getCreatedAt())
            .updatedAt(domain.getUpdatedAt())
            .build();
    }
    
    public Policy toDomain(PolicyEntity entity) {
        PolicyCondition condition = deserializeCondition(entity.getConditionType(), entity.getConditionJson());
        
        return Policy.builder()
            .id(entity.getId())
            .name(entity.getName())
            .systemType(entity.getSystemType())
            .condition(condition)
            .action(entity.getAction())
            .severity(entity.getSeverity())
            .enabled(entity.isEnabled())
            .cooldownSeconds(entity.getCooldownSeconds())
            .description(entity.getDescription())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
    
    private String getConditionType(PolicyCondition condition) {
        if (condition instanceof StateCondition) {
            return "StateCondition";
        } else if (condition instanceof LatencyCondition) {
            return "LatencyCondition";
        } else if (condition instanceof AndCondition) {
            return "AndCondition";
        }
        return "Unknown";
    }
    
    private String serializeCondition(PolicyCondition condition) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            
            if (condition instanceof StateCondition sc) {
                node.put("state", sc.state() != null ? sc.state().name() : null);
                node.put("durationSeconds", sc.durationSeconds());
                node.put("retryCountThreshold", sc.retryCountThreshold());
                node.put("consecutiveFailuresThreshold", sc.consecutiveFailuresThreshold());
            } else if (condition instanceof LatencyCondition lc) {
                node.put("thresholdMs", lc.thresholdMs());
            } else if (condition instanceof AndCondition ac) {
                var conditionsArray = node.putArray("conditions");
                for (PolicyCondition c : ac.conditions()) {
                    ObjectNode childNode = objectMapper.createObjectNode();
                    childNode.put("type", getConditionType(c));
                    childNode.put("data", serializeCondition(c));
                    conditionsArray.add(childNode);
                }
            }
            
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize condition", e);
            return "{}";
        }
    }
    
    private PolicyCondition deserializeCondition(String conditionType, String conditionJson) {
        try {
            JsonNode node = objectMapper.readTree(conditionJson);
            
            return switch (conditionType) {
                case "StateCondition" -> new StateCondition(
                    node.has("state") && !node.get("state").isNull() 
                        ? SystemState.valueOf(node.get("state").asText()) : null,
                    node.has("durationSeconds") && !node.get("durationSeconds").isNull() 
                        ? node.get("durationSeconds").asLong() : null,
                    node.has("retryCountThreshold") && !node.get("retryCountThreshold").isNull() 
                        ? node.get("retryCountThreshold").asInt() : null,
                    node.has("consecutiveFailuresThreshold") && !node.get("consecutiveFailuresThreshold").isNull() 
                        ? node.get("consecutiveFailuresThreshold").asInt() : null
                );
                case "LatencyCondition" -> new LatencyCondition(
                    node.get("thresholdMs").asLong()
                );
                case "AndCondition" -> {
                    JsonNode conditionsArray = node.get("conditions");
                    PolicyCondition[] conditions = new PolicyCondition[conditionsArray.size()];
                    for (int i = 0; i < conditionsArray.size(); i++) {
                        JsonNode child = conditionsArray.get(i);
                        conditions[i] = deserializeCondition(
                            child.get("type").asText(),
                            child.get("data").asText()
                        );
                    }
                    yield new AndCondition(conditions);
                }
                default -> throw new IllegalArgumentException("Unknown condition type: " + conditionType);
            };
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize condition: {}", conditionJson, e);
            throw new RuntimeException("Failed to deserialize policy condition", e);
        }
    }
    
    // ==================== PolicyExecutionRecord ====================
    
    public PolicyExecutionRecordEntity toEntity(PolicyExecutionRecord domain) {
        return PolicyExecutionRecordEntity.builder()
            .id(domain.getId())
            .policyId(domain.getPolicyId())
            .policyName(domain.getPolicyName())
            .systemType(domain.getSystemType())
            .action(domain.getAction())
            .success(domain.isSuccess())
            .message(domain.getMessage())
            .executedAt(domain.getExecutedAt())
            .durationMs(domain.getDurationMs())
            .build();
    }
    
    public PolicyExecutionRecord toDomain(PolicyExecutionRecordEntity entity) {
        return PolicyExecutionRecord.builder()
            .id(entity.getId())
            .policyId(entity.getPolicyId())
            .policyName(entity.getPolicyName())
            .systemType(entity.getSystemType())
            .action(entity.getAction())
            .success(entity.isSuccess())
            .message(entity.getMessage())
            .executedAt(entity.getExecutedAt())
            .durationMs(entity.getDurationMs())
            .build();
    }
    
    // ==================== DesiredSystemState ====================
    
    public DesiredSystemStateEntity toEntity(DesiredSystemState domain) {
        return DesiredSystemStateEntity.builder()
            .systemType(domain.systemType())
            .desiredState(domain.desiredState())
            .maxLatencyMs(domain.maxLatencyMs())
            .maxRetryCount(domain.maxRetryCount())
            .autoRecover(domain.autoRecover())
            .build();
    }
    
    public DesiredSystemState toDomain(DesiredSystemStateEntity entity) {
        return new DesiredSystemState(
            entity.getSystemType(),
            entity.getDesiredState(),
            entity.getMaxLatencyMs(),
            entity.getMaxRetryCount(),
            entity.isAutoRecover()
        );
    }
}
