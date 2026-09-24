package api.typesafe.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import api.typesafe.jev.JevClient.Result;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import typed.decision.model.Question;

class JevClientTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """ 
        200 | {"model":"m","answers":{},"usage":{"input_tokens":1,"output_tokens":1}} | Success
        200 | not json          | Malformed
        401 | {}                | Unauthorized
        422 | {"detail":"x"} | Invalid
        429 | {}                | Retryable
        529 | {}                | Retryable
        500 | {}                | Unexpected
        """
    )
    void mapsStatusToResult(int status, String body, String expected) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new JevClient(HttpClient.newHttpClient(),
                URI.create("http://localhost:" + server.getAddress().getPort()),
                new ModelId("m"), new ApiKey("k"), JevJson.mapper(), Retry.NONE);

            var result = client.ask("s", Map.of("q", new Question.Noul("?")));

            assertEquals(expected, result.getClass().getSimpleName());
        } finally {
            server.stop(0);
        }
    }

    private static final String OK = "{\"model\":\"m\",\"answers\":{},\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
    private static final Retry FAST = new Retry(3, Duration.ofMillis(1), Duration.ofMillis(1));

    @Test
    void retriesBusyUntilSuccess() throws Exception {
        var calls = new AtomicInteger();

        var result = askAgainst(() -> calls.incrementAndGet() < 3 ? 429 : 200, FAST);

        assertEquals(Result.Success.class, result.getClass());
        assertEquals(3, calls.get());
    }

    @Test
    void givesUpAfterTheLastAttempt() throws Exception {
        var calls = new AtomicInteger();

        var result = askAgainst(() -> { calls.incrementAndGet(); return 529; }, FAST);

        assertEquals(new Result.Retryable(529), result);
        assertEquals(3, calls.get());
    }

    @Test
    void neverRetriesAClientError() throws Exception {
        var calls = new AtomicInteger();

        askAgainst(() -> { calls.incrementAndGet(); return 422; }, FAST);

        assertEquals(1, calls.get());
    }

    /** A 200 carries a valid body; any other status an empty object. */
    private static Result askAgainst(IntSupplier status, Retry retry) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            var code = status.getAsInt();
            var bytes = (code == 200 ? OK : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            return new JevClient(HttpClient.newHttpClient(), URI.create("http://localhost:" + server.getAddress().getPort()),
                new ModelId("m"), new ApiKey("k"), JevJson.mapper(), retry).ask("s", Map.of("q", new Question.Noul("?")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void apiKeyNeverPrinted() {
        assertEquals("ApiKey[***]", new ApiKey("secret").toString());
    }
}
