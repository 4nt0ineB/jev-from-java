package api.typesafe.jev;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** How {@link JevClient} retries 429 and 529: exponential backoff with full jitter, up to {@code attempts} calls. */
public record Retry(int attempts, Duration first, Duration cap) {
    public static final Retry NONE = new Retry(1, Duration.ZERO, Duration.ZERO);
    public static final Retry DEFAULT = new Retry(5, Duration.ofMillis(500), Duration.ofSeconds(8));

    public Retry {
        if (attempts < 1) throw new IllegalArgumentException("At least one attempt: " + attempts);
    }

    /** Full jitter spreads parallel callers that were rejected together, so they do not come back together. */
    Duration delay(int attempt) {
        var ceiling = Math.min(cap.toMillis(), first.toMillis() << Math.min(attempt, 20));
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(ceiling + 1));
    }
}
