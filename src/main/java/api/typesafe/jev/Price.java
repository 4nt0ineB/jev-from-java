package api.typesafe.jev;

import java.math.BigDecimal;

/** Jev bills input tokens only. */
public record Price(BigDecimal usdPerMillionInputTokens) {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public BigDecimal of(Usage usage) {
        return usdPerMillionInputTokens.multiply(BigDecimal.valueOf(usage.inputTokens())).divide(MILLION);
    }
}
