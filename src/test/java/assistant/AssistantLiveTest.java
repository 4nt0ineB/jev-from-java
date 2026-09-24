package assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import api.typesafe.jev.ApiKey;
import api.typesafe.jev.JevClient;
import api.typesafe.jev.JevJson;
import api.typesafe.jev.ModelId;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Calls the real API: runs only with {@code JEV_LIVE_TESTS=true}, since each case costs a request. */
@EnabledIfEnvironmentVariable(named = "JEV_LIVE_TESTS", matches = "true")
class AssistantLiveTest {
    private final JevClient jev = new JevClient(HttpClient.newHttpClient(), URI.create("https://api.typesafe.ai"),
        new ModelId("jev-1.13.0"), new ApiKey(System.getenv("TYPESAFE_API_KEY")), JevJson.mapper());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Réveille-moi demain à sept heures et demie  | Execute",
        "Quel temps fera-t-il à Lyon après-demain ?  | Execute",
        "Mets la lumière du salon en bleu            | Execute",
        "Add a dentist appointment on Friday at 2 pm | Execute",
        "Joue-moi un peu de jazz                     | Unhandled",
        "set an infinite lightyear alarm             | Missing",
        "bweruserisurapo                             | NotUnderstood",
    })
    void outcomes(String message, String expected) throws InterruptedException {
        var now = LocalDateTime.now();
        var candidates = Candidates.of(message, now.toLocalDate());

        var response = ((JevClient.Result.Success) jev.ask(message, Assistant.questions(candidates, Assistant.Mode.SINGLE))).response();
        var outcome = Assistant.of(response.answers(), candidates).outcome(now);

        assertEquals(expected, outcome.getClass().getSimpleName(), outcome::toString);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Réveille-moi à 7h et dis-moi s'il pleut demain                    | SetAlarm QueryWeather",
        "Mets la lumière de la chambre en rouge et ajoute un rdv lundi à 9h | AddEvent ChangeLight",
        "Wake me up at 6 am                                                 | SetAlarm",
    })
    void multiAction(String message, String expected) throws InterruptedException {
        var now = LocalDateTime.now();
        var candidates = Candidates.of(message, now.toLocalDate());

        var response = ((JevClient.Result.Success) jev.ask(message, Assistant.questions(candidates, Assistant.Mode.MULTI))).response();
        var outcomes = Assistant.of(response.answers(), candidates).outcomes(now);

        var commands = outcomes.stream()
            .map(o -> o instanceof Outcome.Execute(var command) ? command.getClass().getSimpleName() : o.toString())
            .collect(Collectors.joining(" "));
        assertEquals(expected, commands);
    }
}
