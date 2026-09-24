package api.typesafe.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
                new ModelId("m"), new ApiKey("k"), JevJson.mapper());

            var result = client.ask("s", Map.of("q", new Question.Noul("?")));

            assertEquals(expected, result.getClass().getSimpleName());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void apiKeyNeverPrinted() {
        assertEquals("ApiKey[***]", new ApiKey("secret").toString());
    }
}
