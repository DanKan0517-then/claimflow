package com.claimflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String instanceId,
        Jwt jwt,
        Cors cors,
        Storage storage,
        Workflow workflow,
        Ai ai,
        boolean seedDemo
) {
    public record Jwt(String secret, long ttlMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Storage(String uploadDir) {}

    public record Workflow(BigDecimal managerApprovalThreshold, int highRiskFraudScore) {}

    public record Ai(String provider, String groqApiKey, String groqModel, String groqBaseUrl, boolean workerEnabled) {
        public boolean groqEnabled() {
            boolean hasKey = groqApiKey != null && !groqApiKey.isBlank();
            return hasKey && !"rules".equalsIgnoreCase(provider);
        }
    }
}
