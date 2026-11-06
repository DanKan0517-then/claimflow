package com.claimflow.ai;

import com.claimflow.config.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Uses the LLM when configured; falls back to rules on any failure so a claim never gets stuck. */
@Component
public class AiRouter {

    private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

    private final GroqAiClient groq;
    private final RuleBasedAiClient rules;
    private final AppProperties.Ai config;
    private final MeterRegistry metrics;

    public AiRouter(GroqAiClient groq, RuleBasedAiClient rules, AppProperties props, MeterRegistry metrics) {
        this.groq = groq;
        this.rules = rules;
        this.config = props.ai();
        this.metrics = metrics;
    }

    public AiAnalysis analyze(ClaimContext ctx) {
        if (config.groqEnabled()) {
            try {
                AiAnalysis result = groq.analyze(ctx);
                metrics.counter("claimflow.ai.calls", "provider", "groq", "outcome", "ok").increment();
                return result;
            } catch (Exception e) {
                metrics.counter("claimflow.ai.calls", "provider", "groq", "outcome", "fallback").increment();
                log.warn("LLM analysis failed, using rules instead: {}", e.getMessage());
            }
        }
        metrics.counter("claimflow.ai.calls", "provider", "rules", "outcome", "ok").increment();
        return rules.analyze(ctx);
    }
}
