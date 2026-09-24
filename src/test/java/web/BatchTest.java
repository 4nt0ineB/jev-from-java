package web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.typesafe.jev.ApiKey;
import api.typesafe.jev.JevClient;
import api.typesafe.jev.JevJson;
import api.typesafe.jev.ModelId;
import api.typesafe.jev.Price;
import api.typesafe.jev.Retry;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BatchTest {

    @Test
    void parsesQuotedFieldsAndHeader() {
        var lines = Batch.lines("text,gold_intent\r\n\"réveille-moi, à 7h\",alarm_set\n\"il a dit \"\"bonjour\"\"\",\n\n");

        assertEquals(List.of(new Batch.Line("réveille-moi, à 7h", "alarm_set"), new Batch.Line("il a dit \"bonjour\"", null)),
            lines);
    }

    @Test
    void textOnlyCsvHasNoGold() {
        assertEquals(List.of(new Batch.Line("bonjour", null)), Batch.lines("bonjour"));
    }

    @Test
    void refusesTooManyRows() {
        var csv = String.join("\n", Collections.nCopies(Batch.MAX_ROWS + 1, "x"));

        assertThrows(IllegalArgumentException.class, () -> Batch.lines(csv));
    }

    @Test
    void reportsEveryRowEvenWhenCallsFail() throws Exception {
        var rows = Collections.synchronizedList(new ArrayList<Batch.Row>());

        withRejectingServer(batch -> batch.run(lines(20), rows::add));

        assertEquals(20, rows.size());
        assertEquals(IntStream.range(0, 20).boxed().toList(), rows.stream().map(Batch.Row::index).sorted().toList());
        assertTrue(rows.stream().allMatch(r -> r.error() != null && r.intent() == null));
    }

    @Test
    void stopsWhenTheSinkFails() throws Exception {
        var delivered = Collections.synchronizedList(new ArrayList<Batch.Row>());

        withRejectingServer(batch -> batch.run(lines(200), row -> {
            delivered.add(row);
            throw new IllegalStateException("browser gone");
        }));

        assertEquals(1, delivered.size());
    }

    private static List<Batch.Line> lines(int n) {
        return IntStream.range(0, n).mapToObj(i -> new Batch.Line("message " + i, null)).toList();
    }

    private interface Run {
        void on(Batch batch) throws Exception;
    }

    /** Every call gets a 401, so rows come back fast and as errors. */
    private static void withRejectingServer(Run run) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            var jev = new JevClient(HttpClient.newHttpClient(), URI.create("http://localhost:" + server.getAddress().getPort()),
                new ModelId("m"), new ApiKey("k"), JevJson.mapper(), Retry.NONE);
            run.on(new Batch(jev, new Price(BigDecimal.ONE), Clock.systemUTC(), 4));
        } finally {
            server.stop(0);
        }
    }
}
