package com.claimflow.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedAiClientTest {

    @Test
    void findsExplicitTotalOnInvoice() {
        String bill = """
                Apollo Hospitals, Chennai
                Room charges   Rs. 12,000
                Surgery        Rs. 60,000
                Medicines      Rs. 12,500
                Grand Total: Rs. 84,500.00
                """;
        assertThat(RuleBasedAiClient.findBillTotal(bill)).isEqualByComparingTo("84500.00");
    }

    @Test
    void fallsBackToLargestAmountWithoutTotalLine() {
        assertThat(RuleBasedAiClient.findBillTotal("Paid ₹1,200 and then INR 9,800 for parts"))
                .isEqualByComparingTo("9800");
    }

    @Test
    void noAmountsMeansNull() {
        assertThat(RuleBasedAiClient.findBillTotal("nothing here")).isNull();
        assertThat(RuleBasedAiClient.findBillTotal(null)).isNull();
    }

    @Test
    void flagsRiskyLanguage() {
        ClaimContext ctx = new ClaimContext("PROPERTY", "Fire", "Total loss in the kitchen, paid in cash and there is no receipt.",
                new BigDecimal("100000"), new BigDecimal("200000"), LocalDate.now(), LocalDate.now().minusYears(1), null);
        AiAnalysis a = new RuleBasedAiClient().analyze(ctx);
        assertThat(a.riskAdjustment()).isGreaterThanOrEqualTo(12);
        assertThat(a.riskReasons()).contains("Mentions cash payment", "Claims a total loss");
        assertThat(a.provider()).isEqualTo("rules");
        assertThat(a.summary()).contains("₹1,00,000");
    }
}
