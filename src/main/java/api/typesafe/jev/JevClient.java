package api.typesafe.jev;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import typed.decision.model.Question;

public final class JevClient {

    public sealed interface Result {
        record Success(JevResponse response) implements Result {}
        record Unauthorized() implements Result {}
        /** 422: {@code detail} is the server's explanation of what is invalid. */
        record Invalid(String detail) implements Result {}
        /** 429 or 529: safe to retry after a backoff. */
        record Retryable(int status) implements Result {}
        record Unexpected(int status, String body) implements Result {}
        record Transport(IOException cause) implements Result {}
        /** 200 whose body does not match the contract (shape or value ranges). */
        record Malformed(JacksonException cause) implements Result {}
    }

    private final HttpClient http;
    private final URI endpoint;
    private final ModelId model;
    private final ApiKey apiKey;
    private final ObjectMapper mapper;
    private final Retry retry;

    public JevClient(HttpClient http, URI baseUri, ModelId model, ApiKey apiKey, ObjectMapper mapper, Retry retry) {
        this.http = http;
        this.endpoint = baseUri.resolve("/v1/systemone");
        this.model = model;
        this.apiKey = apiKey;
        this.mapper = mapper;
        this.retry = retry;
    }

    /** Retries 429 and 529 per {@link Retry}; returns {@link Result.Retryable} once the attempts are used up. */
    public Result ask(String state, Map<String, Question> questions) throws InterruptedException {
        var request = HttpRequest.newBuilder(endpoint)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey.value())
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(new JevRequest(state, model, questions))))
            .build();
        for (var attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                return new Result.Transport(e);
            }
            var result = result(response);
            if (!(result instanceof Result.Retryable) || attempt == retry.attempts()) return result;
            var backoff = retry.delay(attempt);
            Thread.sleep(retryAfter(response).orElse(backoff));
        }
    }

    private Result result(HttpResponse<String> response) {
        return switch (response.statusCode()) {
            case 200 -> parse(response.body());
            case 401 -> new Result.Unauthorized();
            case 422 -> new Result.Invalid(response.body());
            case 429, 529 -> new Result.Retryable(response.statusCode());
            default -> new Result.Unexpected(response.statusCode(), response.body());
        };
    }

    /** The server's own hint, in seconds, capped like our backoff so one reply cannot stall a caller for minutes. */
    private Optional<Duration> retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
            .filter(v -> v.strip().matches("\\d{1,6}"))
            .map(v -> Duration.ofSeconds(Long.parseLong(v.strip())))
            .map(d -> d.compareTo(retry.cap()) > 0 ? retry.cap() : d);
    }

    private Result parse(String body) {
        try {
            return new Result.Success(mapper.readValue(body, JevResponse.class));
        } catch (JacksonException e) {
            return new Result.Malformed(e);
        }
    }
}
