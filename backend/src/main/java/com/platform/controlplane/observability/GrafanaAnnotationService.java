package com.platform.controlplane.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for creating Grafana annotations when chaos experiments 
 * start/end to visually mark "normal vs chaos" periods.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrafanaAnnotationService {

    private final RestTemplate restTemplate;

    @Value("${grafana.url:http://localhost:3001}")
    private String grafanaUrl;

    @Value("${grafana.api-key:}")
    private String apiKey;

    @Value("${grafana.annotations.enabled:true}")
    private boolean enabled;

    /**
     * Create annotation when chaos experiment starts.
     */
    @Async
    public void annotateExperimentStart(String experimentId, String systemType, 
                                         String faultType, int durationSeconds) {
        if (!enabled) return;

        Map<String, Object> annotation = new HashMap<>();
        annotation.put("time", Instant.now().toEpochMilli());
        annotation.put("isRegion", true);
        annotation.put("timeEnd", Instant.now().plusSeconds(durationSeconds).toEpochMilli());
        annotation.put("tags", List.of("chaos", "experiment", systemType, faultType));
        annotation.put("text", String.format(
            "<b>Chaos Experiment Started</b><br/>" +
            "ID: %s<br/>" +
            "System: %s<br/>" +
            "Fault: %s<br/>" +
            "Duration: %ds",
            experimentId, systemType, faultType, durationSeconds
        ));

        createAnnotation(annotation);
        
        log.info("Created Grafana annotation for chaos start: experimentId={}, systemType={}", 
                experimentId, systemType);
    }

    /**
     * Create annotation when chaos experiment ends.
     */
    @Async
    public void annotateExperimentEnd(String experimentId, String systemType, 
                                       boolean success, long durationMs) {
        if (!enabled) return;

        Map<String, Object> annotation = new HashMap<>();
        annotation.put("time", Instant.now().toEpochMilli());
        annotation.put("tags", List.of("chaos", "recovery", systemType, success ? "success" : "failed"));
        annotation.put("text", String.format(
            "<b>Chaos Experiment Ended</b><br/>" +
            "ID: %s<br/>" +
            "System: %s<br/>" +
            "Status: %s<br/>" +
            "Recovery Time: %dms",
            experimentId, systemType, success ? "SUCCESS" : "FAILED", durationMs
        ));

        createAnnotation(annotation);
        
        log.info("Created Grafana annotation for chaos end: experimentId={}, success={}", 
                experimentId, success);
    }

    /**
     * Create general annotation for observability events.
     */
    @Async
    public void annotate(String title, String text, List<String> tags) {
        if (!enabled) return;

        Map<String, Object> annotation = new HashMap<>();
        annotation.put("time", Instant.now().toEpochMilli());
        annotation.put("tags", tags);
        annotation.put("text", String.format("<b>%s</b><br/>%s", title, text));

        createAnnotation(annotation);
    }

    /**
     * Create region annotation (start/end time).
     */
    @Async
    public void annotateRegion(String title, Instant start, Instant end, List<String> tags) {
        if (!enabled) return;

        Map<String, Object> annotation = new HashMap<>();
        annotation.put("time", start.toEpochMilli());
        annotation.put("timeEnd", end.toEpochMilli());
        annotation.put("isRegion", true);
        annotation.put("tags", tags);
        annotation.put("text", String.format("<b>%s</b>", title));

        createAnnotation(annotation);
    }

    private void createAnnotation(Map<String, Object> annotation) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            } else {
                // Use basic auth for default setup
                headers.setBasicAuth("admin", "admin");
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(annotation, headers);
            
            restTemplate.postForEntity(
                grafanaUrl + "/api/annotations",
                request,
                String.class
            );
        } catch (Exception e) {
            log.warn("Failed to create Grafana annotation: {}", e.getMessage());
        }
    }
}
