import api.typesafe.jev.ApiKey;
import api.typesafe.jev.JevClient;
import api.typesafe.jev.JevJson;
import api.typesafe.jev.ModelId;
import api.typesafe.jev.Price;
import assistant.Assistant;
import assistant.Candidates;
import io.javalin.http.Context;
import java.math.BigDecimal;
import io.javalin.Javalin;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Properties;
import triage.Triage;
import typed.decision.model.Question;
import web.AssistantPage;
import web.SessionStats;
import web.TriagePage;

void main() throws IOException {
    var properties = loadProperties();
    var jev = new JevClient(
        HttpClient.newHttpClient(),
        URI.create(properties.getProperty("typesafe.api.url")),
        new ModelId(properties.getProperty("typesafe.model")),
        new ApiKey(System.getenv("TYPESAFE_API_KEY")),
        JevJson.mapper());
    var price = new Price(new BigDecimal(properties.getProperty("typesafe.price.usd.per.million.input.tokens")));
    var triagePage = new TriagePage();
    var assistantPage = new AssistantPage();
    var clock = Clock.systemDefaultZone();

    Javalin.create(config -> {
        config.concurrency.useVirtualThreads = true;
        config.routes.get("/", ctx -> ctx.html(triagePage.empty(stats(ctx))));
        config.routes.post("/", ctx -> {
            var message = message(ctx);
            if (message.isEmpty()) {
                ctx.html(triagePage.empty(stats(ctx)));
                return;
            }
            var call = ask(ctx, jev, price, message, Triage.QUESTIONS);
            ctx.html(triagePage.render(message, call.result(), call.cost(), call.took(), call.stats()));
        });
        config.routes.get("/assistant", ctx -> ctx.html(assistantPage.empty(stats(ctx))));
        config.routes.post("/assistant", ctx -> {
            var message = message(ctx);
            if (message.isEmpty()) {
                ctx.html(assistantPage.empty(stats(ctx)));
                return;
            }
            var now = LocalDateTime.now(clock);
            var candidates = Candidates.of(message, now.toLocalDate());
            var mode = "multi".equals(ctx.formParam("mode")) ? Assistant.Mode.MULTI : Assistant.Mode.SINGLE;
            var call = ask(ctx, jev, price, message, Assistant.questions(candidates, mode));
            ctx.html(assistantPage.render(message, mode, candidates, call.result(), call.cost(), call.took(), call.stats(), now));
        });
    }).start(Integer.getInteger("port", 7070));
}

/** One Jev round trip, timed and added to the session's spend. */
private record Call(JevClient.Result result, BigDecimal cost, Duration took, SessionStats stats) {}

private Call ask(Context ctx, JevClient jev, Price price, String message, Map<String, Question> questions)
    throws InterruptedException {
    var start = System.nanoTime();
    var result = jev.ask(message, questions);
    var took = Duration.ofNanos(System.nanoTime() - start);
    var stats = stats(ctx);
    var cost = BigDecimal.ZERO;
    if (result instanceof JevClient.Result.Success(var response)) {
        cost = price.of(response.usage());
        stats = stats.add(cost, took);
        ctx.sessionAttribute("stats", stats);
    }
    return new Call(result, cost, took, stats);
}

private String message(Context ctx) {
    return Objects.requireNonNullElse(ctx.formParam("message"), "").strip();
}

/** Per browser session, in memory: resets on restart. Only successful calls are billed, so only they count. */
private SessionStats stats(Context ctx) {
    return Objects.requireNonNullElse(ctx.sessionAttribute("stats"), SessionStats.NONE);
}

private Properties loadProperties() throws IOException {
    try (var in = Objects.requireNonNull(
        getClass().getClassLoader().getResourceAsStream("app.properties"), "app.properties not on classpath")) {
        var properties = new Properties();
        properties.load(in);
        return properties;
    }
}
