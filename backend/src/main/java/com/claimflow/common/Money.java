package com.claimflow.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formats rupees with Indian digit grouping (1,00,000) which java.text.DecimalFormat can't do. */
public final class Money {
    private Money() {}

    public static String inr(BigDecimal amount) {
        if (amount == null) return "₹0";
        BigDecimal v = amount.setScale(2, RoundingMode.HALF_UP);
        boolean negative = v.signum() < 0;
        String plain = v.abs().toPlainString();
        String whole = plain.substring(0, plain.indexOf('.'));
        String fraction = plain.substring(plain.indexOf('.') + 1);

        StringBuilder sb = new StringBuilder();
        int len = whole.length();
        if (len <= 3) {
            sb.append(whole);
        } else {
            String last3 = whole.substring(len - 3);
            String rest = whole.substring(0, len - 3);
            StringBuilder head = new StringBuilder();
            for (int i = rest.length(); i > 0; i -= 2) {
                head.insert(0, rest.substring(Math.max(0, i - 2), i));
                if (i - 2 > 0) head.insert(0, ',');
            }
            sb.append(head).append(',').append(last3);
        }
        if (!fraction.equals("00")) sb.append('.').append(fraction);
        return (negative ? "-₹" : "₹") + sb;
    }
}
