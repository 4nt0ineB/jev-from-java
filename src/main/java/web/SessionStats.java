package web;

import java.math.BigDecimal;
import java.time.Duration;

/** Totals over the successful triages of one browser session. */
public record SessionStats(BigDecimal usd, int triages, Duration latency) {
    public static final SessionStats NONE = new SessionStats(BigDecimal.ZERO, 0, Duration.ZERO);

    public SessionStats add(BigDecimal cost, Duration took) {
        return new SessionStats(usd.add(cost), triages + 1, latency.plus(took));
    }

    public Duration meanLatency() {
        return triages == 0 ? Duration.ZERO : latency.dividedBy(triages);
    }
}
