package web;

import api.typesafe.jev.JevClient.Result;
import io.quarkus.qute.Engine;
import io.quarkus.qute.HtmlEscaper;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateLocator.TemplateLocation;
import io.quarkus.qute.ValueResolver;
import io.quarkus.qute.Variant;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import triage.Verdict;
import typed.decision.model.Probability;

/** One Qute engine for every page, so templates can include {@code layout} and share the formatting helpers. */
final class Views {
    private static final Engine ENGINE = Engine.builder()
        .addDefaults()
        .addValueResolver(new ReflectionValueResolver())
        .addValueResolver(ValueResolver.builder().appliesTo(ctx -> ctx.getBase() instanceof Probability
                && ctx.getName().equals("percent"))
            .resolveSync(ctx -> Math.round(((Probability) ctx.getBase()).value() * 100)).build())
        .addValueResolver(ValueResolver.builder().appliesTo(ctx -> ctx.getBase() instanceof Probability
                && ctx.getName().equals("verdict"))
            .resolveSync(ctx -> Verdict.of((Probability) ctx.getBase())).build())
        .addValueResolver(ValueResolver.builder().appliesTo(ctx -> ctx.getBase() instanceof BigDecimal
                && ctx.getName().equals("usd"))
            .resolveSync(ctx -> usd((BigDecimal) ctx.getBase())).build())
        .addValueResolver(ValueResolver.builder().appliesTo(ctx -> ctx.getBase() instanceof BigDecimal
                && ctx.getName().equals("perDollar"))
            .resolveSync(ctx -> perDollar((BigDecimal) ctx.getBase())).build())
        .addValueResolver(ValueResolver.builder().appliesTo(ctx -> ctx.getBase() instanceof Duration
                && ctx.getName().equals("ms"))
            .resolveSync(ctx -> "%,d ms".formatted(((Duration) ctx.getBase()).toMillis())).build())
        .addResultMapper(new HtmlEscaper(List.of(Variant.TEXT_HTML)))
        .addLocator(Views::classpath)
        .build();

    private Views() {
    }

    static Template template(String id) {
        return ENGINE.getTemplate(id);
    }

    /** The user-facing message for a failed call. */
    static String error(Result result) {
        return switch (result) {
            case Result.Success _ -> throw new IllegalArgumentException("Not an error: " + result);
            case Result.Unauthorized() -> "The API key was rejected (401).";
            case Result.Invalid(var detail) -> "Jev rejected the request (422): " + detail;
            case Result.Retryable(var status) -> "Jev is busy (" + status + "), try again shortly.";
            case Result.Unexpected(var status, var body) -> "Unexpected status " + status + ": " + body;
            case Result.Transport(var cause) -> "Could not reach Jev: " + cause.getMessage();
            case Result.Malformed(var cause) -> "Jev's reply broke the contract: " + cause.getOriginalMessage();
        };
    }

    private static Optional<TemplateLocation> classpath(String id) {
        var resource = Views.class.getClassLoader().getResource("templates/" + id + ".html");
        if (resource == null) {
            return Optional.empty();
        }
        return Optional.of(new TemplateLocation() {
            @Override
            public Reader read() {
                try {
                    return new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public Optional<Variant> getVariant() {
                return Optional.of(Variant.forContentType(Variant.TEXT_HTML));
            }
        });
    }

    /** Seven decimals: a single call costs a few hundred-thousandths of a dollar. */
    private static String usd(BigDecimal amount) {
        return "$" + amount.setScale(7, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String perDollar(BigDecimal cost) {
        return cost.signum() == 0 ? "∞" : "%,d".formatted(BigDecimal.ONE.divide(cost, 0, RoundingMode.DOWN).longValue());
    }
}
