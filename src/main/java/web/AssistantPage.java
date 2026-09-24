package web;

import api.typesafe.jev.JevClient.Result;
import assistant.Assistant;
import assistant.Candidates;
import assistant.Command;
import assistant.Outcome;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class AssistantPage {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

    /** One row of the slot table: what code found, what Jev picked, and whether the chosen intent reads it. */
    public record SlotRow(String label, List<String> found, String picked, boolean used) {}

    private final Template template = Views.template("assistant");

    public String empty(SessionStats stats) {
        return page("", stats).render();
    }

    /** {@code cost} and {@code took} describe this call; they are already included in {@code stats}. */
    public String render(String message, Candidates candidates, Result result, BigDecimal cost, Duration took,
                         SessionStats stats, LocalDateTime now) {
        var page = page(message, stats).data("cost", cost).data("took", took);
        if (!(result instanceof Result.Success(var response))) {
            return page.data("error", Views.error(result)).render();
        }
        var assistant = Assistant.of(response.answers(), candidates);
        var outcome = assistant.outcome(now);
        return page.data("assistant", assistant)
            .data("usage", response.usage())
            .data("kind", outcome.getClass().getSimpleName())
            .data("headline", headline(outcome))
            .data("command", outcome instanceof Outcome.Execute(var command) ? command : null)
            .data("slots", slots(assistant))
            .render();
    }

    private static List<SlotRow> slots(Assistant assistant) {
        return Assistant.SLOTS.stream()
            .map(slot -> new SlotRow(slot.label(),
                slot.candidates().apply(assistant.candidates()).stream().map(Candidates.Candidate::text).toList(),
                assistant.chosen().containsKey(slot) ? assistant.chosen().get(slot).text() : null,
                slot.intent() == assistant.intent()))
            .toList();
    }

    private static String headline(Outcome outcome) {
        return switch (outcome) {
            case Outcome.NotUnderstood() -> "Sorry, I didn't understand that.";
            case Outcome.Unsure(var best, var confidence) -> "Not sure what you mean. Maybe %s? (%d%%)"
                .formatted(best.label(), Math.round(confidence.value() * 100));
            case Outcome.Unsupported() -> "Understood, but that is not something I can do.";
            case Outcome.Unhandled(var intent) -> "Understood as %s, but there is no handler for it.".formatted(intent.label());
            case Outcome.Missing(_, var question) -> question;
            case Outcome.Execute(var command) -> switch (command) {
                case Command.SetAlarm(var date, var time) -> "Alarm set for %s at %s.".formatted(DAY.format(date), time);
                case Command.QueryWeather(var date, var city) -> "Weather for %s, %s.".formatted(
                    city.orElse("your location"), DAY.format(date));
                case Command.ChangeLight(var colour, var room) -> "%s lights turned %s.".formatted(
                    room.map(r -> capitalise(r.name().replace('_', ' '))).orElse("All"), colour.name().toLowerCase());
                case Command.AddEvent(var date, var time) -> "Event added on %s%s.".formatted(
                    DAY.format(date), time.map(t -> " at " + t).orElse(", all day"));
            };
        };
    }

    private static String capitalise(String words) {
        return words.charAt(0) + words.substring(1).toLowerCase();
    }

    /** Qute's strict rendering fails on names never set, so the optional ones start as null. */
    private TemplateInstance page(String message, SessionStats stats) {
        return template.data("message", message).data("stats", stats).data("error", null).data("assistant", null);
    }
}
