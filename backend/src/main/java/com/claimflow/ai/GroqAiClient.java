package com.claimflow.ai;

import com.claimflow.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

/** Calls Groq's OpenAI-compatible chat API in JSON mode. */
@Component
public class GroqAiClient implements AiClient {

    private static final String SYSTEM_PROMPT = """
            You are a senior insurance claims analyst in India. You get a claim and the text of its documents.
            Reply with ONLY a JSON object with these keys:
              "summary": 2-3 plain sentences an adjuster can read in 10 seconds,
              "billTotal": number or null - the final total amount shown on the bills/invoices,
              "incidentDateInDocs": "YYYY-MM-DD" or null,
              "provider": hospital / garage / vendor name or null,
              "items": up to 6 short strings of what is being claimed,
              "riskAdjustment": integer 0-20 - extra fraud risk from inconsistencies, vague or evasive wording,
                                documents that don't match the story. 0 if everything looks consistent,
              "riskReasons": list of short strings explaining any risk points
            Do not invent facts that are not in the input.
            """;

    private final AppProperties.Ai config;
    private final ObjectMapper mapper;
    private final RestClient http;

    public GroqAiClient(AppProperties props, ObjectMapper mapper) {
        this.config = props.ai();
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder()
                .baseUrl(config.groqBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + config.groqApiKey())
                .build();
    }

    @Override
    public AiAnalysis analyze(ClaimContext ctx) {
        String docs = ctx.documentsText() == null ? "(no readable documents)" : ctx.documentsText();
        if (docs.length() > 12_000) docs = docs.substring(0, 12_000);
        String userPrompt = """
                Claim type: %s
                Title: %s
                Claimed amount (INR): %s
                Sum insured (INR): %s
                Incident date: %s
                Policy start date: %s
                Customer description:
                %s

                Document text:
                %s
                """.formatted(ctx.type(), ctx.title(), ctx.claimedAmount(), ctx.sumInsured(),
                ctx.incidentDate(), ctx.policyStart(), ctx.description(), docs);

        Map<String, Object> body = Map.of(
                "model", config.groqModel(),
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)));

        JsonNode response = http.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) throw new IllegalStateException("Empty response from Groq");
        String content = response.path("choices").path(0).path("message").path("content").asText();
        return parse(content);
    }

    AiAnalysis parse(String content) {
        try {
            JsonNode json = mapper.readTree(content);
            BigDecimal billTotal = json.hasNonNull("billTotal") && json.get("billTotal").isNumber()
                    ? json.get("billTotal").decimalValue() : null;
            int risk = Math.max(0, Math.min(20, json.path("riskAdjustment").asInt(0)));
            List<String> reasons = json.has("riskReasons")
                    ? mapper.convertValue(json.get("riskReasons"), new TypeReference<List<String>>() {})
                    : List.of();
            Map<String, Object> extracted = new LinkedHashMap<>();
            extracted.put("billTotal", billTotal);
            extracted.put("incidentDateInDocs", textOrNull(json, "incidentDateInDocs"));
            extracted.put("provider", textOrNull(json, "provider"));
            extracted.put("items", json.has("items")
                    ? mapper.convertValue(json.get("items"), new TypeReference<List<String>>() {}) : List.of());
            return new AiAnalysis(json.path("summary").asText(""), extracted, billTotal, risk,
                    reasons == null ? List.of() : reasons, "groq:" + config.groqModel());
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse LLM output: " + e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode json, String field) {
        return json.hasNonNull(field) ? json.get(field).asText() : null;
    }
}
