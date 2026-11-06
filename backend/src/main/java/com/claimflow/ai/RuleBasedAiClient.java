package com.claimflow.ai;

import com.claimflow.common.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback used when no LLM key is configured or the LLM call fails.
 * Keeps the pipeline working offline and makes tests reproducible.
 */
@Component
public class RuleBasedAiClient implements AiClient {

    private static final Pattern TOTAL = Pattern.compile(
            "(?i)(?:grand\\s+total|total\\s+amount|net\\s+payable|amount\\s+payable|total)\\s*[:\\-]?\\s*(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern DATE = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b");

    private static final Map<String, String> RISK_PHRASES = new LinkedHashMap<>();
    static {
        RISK_PHRASES.put("cash", "Mentions cash payment");
        RISK_PHRASES.put("no receipt", "Says there is no receipt");
        RISK_PHRASES.put("lost the receipt", "Says the receipt was lost");
        RISK_PHRASES.put("lost receipt", "Says the receipt was lost");
        RISK_PHRASES.put("total loss", "Claims a total loss");
        RISK_PHRASES.put("stolen", "Theft claim (harder to verify)");
        RISK_PHRASES.put("urgent", "Pushes for urgent payout");
        RISK_PHRASES.put("friend", "Third party is a friend or relative");
        RISK_PHRASES.put("no witness", "No witnesses");
    }

    @Override
    public AiAnalysis analyze(ClaimContext ctx) {
        String docs = ctx.documentsText() == null ? "" : ctx.documentsText();
        String all = (ctx.title() + "\n" + ctx.description() + "\n" + docs).toLowerCase(Locale.ROOT);

        BigDecimal billTotal = findBillTotal(docs);
        List<String> dates = new ArrayList<>();
        Matcher dm = DATE.matcher(docs);
        while (dm.find() && dates.size() < 5) dates.add(dm.group(1));

        List<String> reasons = new ArrayList<>();
        int risk = 0;
        for (var e : RISK_PHRASES.entrySet()) {
            if (all.contains(e.getKey())) {
                reasons.add(e.getValue());
                risk += 4;
            }
        }
        if (ctx.description().trim().length() < 40) {
            reasons.add("Very short description");
            risk += 4;
        }

        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("billTotal", billTotal);
        extracted.put("datesInDocuments", dates);
        extracted.put("documentsProvided", !docs.isBlank());
        extracted.put("category", ctx.type());

        String summary = String.format("%s claim for %s, incident on %s. %s",
                capitalize(ctx.type()), rupees(ctx.claimedAmount()), ctx.incidentDate(), firstSentence(ctx.description()));
        if (billTotal != null) {
            summary += " Documents show a total of " + rupees(billTotal) + ".";
        }
        return new AiAnalysis(summary, extracted, billTotal, Math.min(risk, 20), reasons, "rules");
    }

    static BigDecimal findBillTotal(String text) {
        if (text == null || text.isBlank()) return null;
        BigDecimal best = lastMatch(TOTAL, text);
        if (best != null) return best;
        // no explicit "total": take the largest currency amount on the document
        BigDecimal max = null;
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            BigDecimal v = parse(m.group(1));
            if (v != null && (max == null || v.compareTo(max) > 0)) max = v;
        }
        return max;
    }

    private static BigDecimal lastMatch(Pattern p, String text) {
        BigDecimal found = null;
        Matcher m = p.matcher(text);
        while (m.find()) {
            BigDecimal v = parse(m.group(1));
            if (v != null) found = v;
        }
        return found;
    }

    private static BigDecimal parse(String raw) {
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstSentence(String s) {
        String t = s.trim().replaceAll("\\s+", " ");
        int dot = t.indexOf(". ");
        String first = dot > 0 ? t.substring(0, dot + 1) : t;
        return first.length() > 220 ? first.substring(0, 217) + "..." : first;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    static String rupees(BigDecimal amount) {
        return Money.inr(amount);
    }
}
