package web;

import api.typesafe.jev.JevClient.Result;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import triage.Route;
import triage.Triage;

public final class TriagePage {
    private final Template template = Views.template("triage");

    public String empty(SessionStats stats) {
        return page("", stats).render();
    }

    /** {@code cost} and {@code took} describe this call; they are already included in {@code stats}. */
    public String render(String message, Result result, BigDecimal cost, Duration took, SessionStats stats) {
        var page = page(message, stats).data("cost", cost).data("took", took);
        return switch (result) {
            case Result.Success(var response) -> {
                var triage = Triage.of(response.answers());
                page.data("triage", triage).data("usage", response.usage());
                yield switch (triage.route()) {
                    case Route.Automatic _ -> page.data("automatic", true).data("reasons", List.of()).render();
                    case Route.HumanReview(var reasons) -> page.data("automatic", false).data("reasons", reasons).render();
                };
            }
            default -> page.data("error", Views.error(result)).render();
        };
    }

    /** Qute's strict rendering fails on names never set, so the optional ones start as null. */
    private TemplateInstance page(String message, SessionStats stats) {
        return template.data("message", message).data("stats", stats).data("error", null).data("triage", null);
    }
}
