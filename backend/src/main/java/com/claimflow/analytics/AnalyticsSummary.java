package com.claimflow.analytics;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalyticsSummary(
        long totalClaims,
        long openClaims,
        long slaBreached,
        long highRisk,
        BigDecimal totalClaimed,
        BigDecimal totalApproved,
        double approvalRate,
        double avgDecisionHours,
        Map<String, Long> byStatus,
        Map<String, Long> byType,
        Map<String, Long> byRisk,
        List<DailyCount> daily,
        List<AdjusterLoad> adjusters,
        Instant generatedAt
) implements Serializable {

    public record DailyCount(String date, long submitted, long decided) implements Serializable {}

    public record AdjusterLoad(Long id, String name, long open, long decided) implements Serializable {}
}
