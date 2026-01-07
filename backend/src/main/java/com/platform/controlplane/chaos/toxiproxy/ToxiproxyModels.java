package com.platform.controlplane.chaos.toxiproxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * DTOs for Toxiproxy API communication.
 */
public class ToxiproxyModels {
    
    /**
     * Toxiproxy proxy definition.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Proxy {
        private String name;
        private String listen;
        private String upstream;
        private boolean enabled = true;
        
        @JsonProperty("toxics")
        private Toxic[] toxics;
    }
    
    /**
     * Toxiproxy toxic definition.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Toxic {
        private String name;
        private String type;
        private String stream = "downstream"; // downstream or upstream
        private double toxicity = 1.0; // 0.0 - 1.0
        private Map<String, Object> attributes;
    }
    
    /**
     * Request to create a toxic.
     */
    @Data
    public static class CreateToxicRequest {
        private String name;
        private String type;
        private String stream = "downstream";
        private double toxicity = 1.0;
        private Map<String, Object> attributes;
        
        public static CreateToxicRequest latency(String name, int latencyMs, int jitter) {
            CreateToxicRequest req = new CreateToxicRequest();
            req.setName(name);
            req.setType("latency");
            req.setStream("downstream");
            req.setToxicity(1.0);
            req.setAttributes(Map.of(
                "latency", latencyMs,
                "jitter", jitter
            ));
            return req;
        }
        
        public static CreateToxicRequest timeout(String name, int timeoutMs) {
            CreateToxicRequest req = new CreateToxicRequest();
            req.setName(name);
            req.setType("timeout");
            req.setStream("downstream");
            req.setToxicity(1.0);
            req.setAttributes(Map.of("timeout", timeoutMs));
            return req;
        }
        
        public static CreateToxicRequest resetPeer(String name, int timeout) {
            CreateToxicRequest req = new CreateToxicRequest();
            req.setName(name);
            req.setType("reset_peer");
            req.setStream("downstream");
            req.setToxicity(1.0);
            req.setAttributes(Map.of("timeout", timeout));
            return req;
        }
        
        public static CreateToxicRequest bandwidth(String name, int rateKBps) {
            CreateToxicRequest req = new CreateToxicRequest();
            req.setName(name);
            req.setType("bandwidth");
            req.setStream("downstream");
            req.setToxicity(1.0);
            req.setAttributes(Map.of("rate", rateKBps));
            return req;
        }
        
        public static CreateToxicRequest slowClose(String name, int delayMs) {
            CreateToxicRequest req = new CreateToxicRequest();
            req.setName(name);
            req.setType("slow_close");
            req.setStream("downstream");
            req.setToxicity(1.0);
            req.setAttributes(Map.of("delay", delayMs));
            return req;
        }
    }
    
    /**
     * Request to create/update a proxy.
     */
    @Data
    public static class CreateProxyRequest {
        private String name;
        private String listen;
        private String upstream;
        private boolean enabled = true;
    }
}
