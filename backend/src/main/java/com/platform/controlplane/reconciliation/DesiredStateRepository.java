package com.platform.controlplane.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for desired system states.
 */
@Slf4j
@Component
public class DesiredStateRepository {
    
    private final Map<String, DesiredSystemState> desiredStates = new ConcurrentHashMap<>();
    
    public void save(DesiredSystemState state) {
        desiredStates.put(state.systemType(), state);
        log.info("Saved desired state for {}: {}", state.systemType(), state.desiredState());
    }
    
    public DesiredSystemState get(String systemType) {
        return desiredStates.computeIfAbsent(
            systemType, 
            DesiredSystemState::createDefault
        );
    }
    
    public Map<String, DesiredSystemState> getAll() {
        return Map.copyOf(desiredStates);
    }
    
    public void delete(String systemType) {
        desiredStates.remove(systemType);
        log.info("Deleted desired state for {}", systemType);
    }
}
