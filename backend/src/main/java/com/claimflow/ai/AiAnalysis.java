package com.claimflow.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Output of the AI step.
 *
 * @param summary         short plain-English summary for the adjuster
 * @param extracted       structured fields pulled from description + documents
 * @param billTotal       the total the documents seem to show, if found
 * @param riskAdjustment  0..20 extra fraud points from things the rules can't see (tone, inconsistencies)
 * @param riskReasons     why the AI added risk points
 * @param provider        which engine produced this (groq model name or "rules")
 */
public record AiAnalysis(
        String summary,
        Map<String, Object> extracted,
        BigDecimal billTotal,
        int riskAdjustment,
        List<String> riskReasons,
        String provider
) {}
