package web;

import api.typesafe.jev.JevClient;
import api.typesafe.jev.JevClient.Result;
import api.typesafe.jev.Price;
import assistant.Assistant;
import assistant.Candidates;
import assistant.Outcome;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs the single-action assistant over many messages, in parallel, and reports each row as it finishes. Rows come
 * back in completion order, not input order; {@link Row#index} says where each one came from.
 */
public final class Batch {
    public static final int MAX_ROWS = 1000;

    /** {@code gold} is null when the CSV has no label column. */
    public record Line(String text, String gold) {}

    /**
     * One finished message, in the shape the page's script reads. {@code error} is set, and the answer fields are
     * null, when the call failed.
     */
    public record Row(int index, String text, String gold, String intent, Double confidence, Double intelligible,
                      String outcome, String headline, String command, BigDecimal cost, long millis, String error) {}

    private final JevClient jev;
    private final Price price;
    private final Clock clock;
    private final int parallelism;

    public Batch(JevClient jev, Price price, Clock clock, int parallelism) {
        this.jev = jev;
        this.price = price;
        this.clock = clock;
        this.parallelism = parallelism;
    }

    /**
     * A header row is recognised by a first cell of {@code text}. A second column, when present and not blank, is the
     * gold intent. Throws when there are more than {@link #MAX_ROWS} messages.
     */
    public static List<Line> lines(String csv) {
        var rows = Csv.parse(csv);
        if (!rows.isEmpty() && rows.getFirst().getFirst().strip().equalsIgnoreCase("text")) rows = rows.subList(1, rows.size());
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("%d rows, at most %d per run".formatted(rows.size(), MAX_ROWS));
        }
        return rows.stream()
            .map(r -> new Line(r.getFirst().strip(), r.size() > 1 && !r.get(1).isBlank() ? r.get(1).strip() : null))
            .filter(l -> !l.text().isEmpty())
            .toList();
    }

    /**
     * Blocks until every line is done. {@code sink} is called from several threads, one row at a time; if it throws
     * (the browser went away), the rows not yet started are skipped. {@code parallelism} bounds the calls in flight,
     * which keeps a large file from turning into a burst of 429s.
     */
    public void run(List<Line> lines, Consumer<Row> sink) throws InterruptedException {
        var permits = new Semaphore(parallelism);
        var stopped = new AtomicBoolean();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var i = 0; i < lines.size() && !stopped.get(); i++) {
                permits.acquire();
                var index = i;
                executor.submit(() -> {
                    try {
                        var row = row(index, lines.get(index));
                        synchronized (sink) {
                            if (!stopped.get()) sink.accept(row);
                        }
                    } catch (RuntimeException e) {
                        stopped.set(true);
                    } finally {
                        permits.release();
                    }
                    return null;
                });
            }
        }
    }

    /** A reply that breaks the contract is one bad row, not a reason to stop the run. */
    private Row row(int index, Line line) throws InterruptedException {
        var start = System.nanoTime();
        try {
            return answered(index, line, start);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return failed(index, line, start, "Jev's reply broke the contract: " + e.getMessage());
        }
    }

    private Row failed(int index, Line line, long start, String error) {
        return new Row(index, line.text(), line.gold(), null, null, null, null, null, null, BigDecimal.ZERO,
            Duration.ofNanos(System.nanoTime() - start).toMillis(), error);
    }

    private Row answered(int index, Line line, long start) throws InterruptedException {
        var now = LocalDateTime.now(clock);
        var candidates = Candidates.of(line.text(), now.toLocalDate());
        var result = jev.ask(line.text(), Assistant.questions(candidates, Assistant.Mode.SINGLE));
        if (!(result instanceof Result.Success(var response))) return failed(index, line, start, Views.error(result));
        var millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        var assistant = Assistant.of(response.answers(), candidates);
        var outcome = assistant.outcome(now);
        return new Row(index, line.text(), line.gold(), assistant.intent().label(), assistant.confidence().value(),
            assistant.intelligible().value(), outcome.getClass().getSimpleName(), AssistantPage.headline(outcome),
            outcome instanceof Outcome.Execute(var command) ? command.toString() : null,
            price.of(response.usage()), millis, null);
    }
}
