package com.claimflow.ai;

/** Spring application event: "this claim needs AI analysis". Turned into a Redis job after commit. */
public record AnalysisRequested(Long claimId) {}
