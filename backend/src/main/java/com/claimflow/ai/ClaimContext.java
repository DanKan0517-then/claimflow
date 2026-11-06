package com.claimflow.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Everything the AI gets to see about a claim. No personal identifiers are sent to the LLM. */
public record ClaimContext(
        String type,
        String title,
        String description,
        BigDecimal claimedAmount,
        BigDecimal sumInsured,
        LocalDate incidentDate,
        LocalDate policyStart,
        String documentsText
) {}
