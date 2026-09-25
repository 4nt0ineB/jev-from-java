import api.typesafe.jev.ApiKey;
import api.typesafe.jev.JevClient;
import api.typesafe.jev.JevJson;
import api.typesafe.jev.ModelId;
import api.typesafe.jev.Price;
import api.typesafe.jev.Retry;
import assistant.Assistant;
import assistant.Candidates;
import io.javalin.http.Context;
import io.quarkus.qute.RawString;
import java.math.BigDecimal;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import triage.Verdict;
import web.Batch;
import web.Views;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Properties;
import triage.Triage;
import typed.decision.model.Question;
import snake.Game;
import snake.Semantics;
import snake.Turn;
import tools.jackson.core.JacksonException;
import typed.decision.model.DecisionModel;
import web.AssistantPage;
import web.JevModel;
import web.SessionStats;
import web.TriagePage;

void main() throws IOException {
    var properties = loadProperties();
    var jev = new JevClient(
        HttpClient.newHttpClient(),
        URI.create(properties.getProperty("typesafe.api.url")),
        new ModelId(properties.getProperty("typesafe.model")),
        new ApiKey(System.getenv("TYPESAFE_API_KEY")),
        JevJson.mapper(), Retry.DEFAULT);
    var price = new Price(new BigDecimal(properties.getProperty("typesafe.price.usd.per.million.input.tokens")));
    var triagePage = new TriagePage();
    var assistantPage = new AssistantPage();
    var clock = Clock.systemDefaultZone();
    var batch = new Batch(jev, price, clock, BATCH_PARALLELISM);
    var sample = resource("samples/massive-fr-FR-test-200.csv");
    var batchPage = Views.template("batch").data("maxRows", Batch.MAX_ROWS)
        .data("act", Assistant.ACT_CONFIDENCE).data("unsureFrom", Verdict.UNSURE_FROM).render();
    var rowJson = JsonMapper.builder().build();
    var players = players(properties, jev, price);
    var snakeJson = JevJson.mapper();
    var snakePage = Views.template("snake")
        .data("players", new RawString(snakeJson.writeValueAsString(players.entrySet().stream()
            .map(e -> Map.of("id", e.getKey(), "label", e.getValue().label())).toList())))
        .data("question", Game.QUESTIONS.get(Game.MOVE))
        .render();

    Javalin.create(config -> {
        config.concurrency.useVirtualThreads = true;
        config.staticFiles.add(files -> {
            files.hostedPath = "/static";
            files.directory = "/static";
            files.location = Location.CLASSPATH;
        });
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
        config.routes.get("/snake", ctx -> ctx.html(snakePage));
        config.routes.post("/snake/new", ctx ->
            ctx.contentType("application/json").result(snakeJson.writeValueAsString(Game.start(ThreadLocalRandom.current()))));
        config.routes.post("/snake/step", ctx -> {
            var player = players.get(Objects.requireNonNullElse(ctx.queryParam("player"), ""));
            if (player == null) {
                ctx.status(404).result("No such player");
                return;
            }
            Game game;
            Semantics semantics;
            try {
                game = snakeJson.readValue(ctx.body(), Game.class);
                semantics = Semantics.valueOf(Objects.requireNonNullElse(ctx.queryParam("semantics"), "full").toUpperCase(Locale.ROOT));
            } catch (JacksonException | IllegalArgumentException e) {
                ctx.status(400).result("Bad request: " + e.getMessage());
                return;
            }
            var turn = Turn.play(game, semantics, player.model(), ThreadLocalRandom.current());
            ctx.contentType("application/json").result(snakeJson.writeValueAsString(turn));
        });
        config.routes.get("/batch", ctx -> ctx.html(batchPage));
        config.routes.post("/batch", ctx -> {
            List<Batch.Line> lines;
            try {
                lines = Batch.lines("massive-fr".equals(ctx.queryParam("sample")) ? sample : ctx.body());
            } catch (IllegalArgumentException e) {
                ctx.status(400).result(e.getMessage());
                return;
            }
            if (lines.isEmpty()) {
                ctx.status(400).result("No rows with text in this CSV.");
                return;
            }
            ctx.disableCompression();
            var response = ctx.res();
            response.setContentType("application/x-ndjson");
            response.setHeader("X-Rows", String.valueOf(lines.size()));
            var out = response.getOutputStream();
            batch.run(lines, row -> {
                try {
                    out.write((rowJson.writeValueAsString(row) + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
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

/** A model the snake page can pick; only configured ones are listed. */
private record Player(String label, DecisionModel model) {}

/** Self-hosted players join when their URL variable is set: both servers speak Jev's wire format. */
private Map<String, Player> players(Properties properties, JevClient jev, Price price) {
    var players = new LinkedHashMap<String, Player>();
    players.put("jev", new Player("Jev · " + properties.getProperty("typesafe.model"), new JevModel(jev, price)));
    var laya = properties.getProperty("laya.model");
    selfHosted("LAYA", laya, "Laya · " + laya + " (local)").ifPresent(p -> players.put("laya", p));
    // open-jev ignores the model field; the GPU is billed per hour, not per token
    selfHosted("OPENJEV", "open-jev-2b", "Open-Jev 2B (Modal L4, billed per hour)")
        .ifPresent(p -> players.put("openjev", p));
    return players;
}

/** Reads {@code <prefix>_URL} and, when the server wants one, {@code <prefix>_API_KEY}. */
private Optional<Player> selfHosted(String prefix, String model, String label) {
    var url = System.getenv(prefix + "_URL");
    if (url == null || url.isBlank()) return Optional.empty();
    // laya-serve ignores the header unless it was started with LAYA_API_KEY
    var key = new ApiKey(Objects.requireNonNullElse(System.getenv(prefix + "_API_KEY"), "none"));
    var client = new JevClient(HttpClient.newHttpClient(), URI.create(url), new ModelId(model), key, JevJson.mapper(), Retry.NONE);
    return Optional.of(new Player(label, new JevModel(client, new Price(BigDecimal.ZERO))));
}

/** Per browser session, in memory: resets on restart. Only successful calls are billed, so only they count. */
private SessionStats stats(Context ctx) {
    return Objects.requireNonNullElse(ctx.sessionAttribute("stats"), SessionStats.NONE);
}

/** Enough to keep a 200-row run well under the rate limit while still looking fast. */
private static final int BATCH_PARALLELISM = 8;

private String resource(String name) throws IOException {
    try (var in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name), name + " not on classpath")) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}

private Properties loadProperties() throws IOException {
    try (var in = Objects.requireNonNull(
        getClass().getClassLoader().getResourceAsStream("app.properties"), "app.properties not on classpath")) {
        var properties = new Properties();
        properties.load(in);
        return properties;
    }
}
