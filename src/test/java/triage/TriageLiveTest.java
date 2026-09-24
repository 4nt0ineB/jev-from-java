package triage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import api.typesafe.jev.ApiKey;
import api.typesafe.jev.JevClient;
import api.typesafe.jev.JevJson;
import api.typesafe.jev.ModelId;
import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Calls the real API: runs only with {@code JEV_LIVE_TESTS=true}, since each case costs a request. */
@EnabledIfEnvironmentVariable(named = "JEV_LIVE_TESTS", matches = "true")
class TriageLiveTest {
    private final JevClient jev = new JevClient(HttpClient.newHttpClient(), URI.create("https://api.typesafe.ai"),
        new ModelId("jev-1.13.0"), new ApiKey(System.getenv("TYPESAFE_API_KEY")), JevJson.mapper());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "J'ai été prélevé deux fois ce mois-ci, merci de me rembourser.     | BILLING",
        "I was charged twice this month, please refund me.                   | BILLING",
        "L'application plante à chaque fois que j'exporte un PDF.            | TECHNICAL",
        "The app crashes every time I export a PDF.                          | TECHNICAL",
        "Quel est le tarif pour passer à l'offre entreprise pour 50 postes ? | SALES",
        "What would the enterprise plan cost for 50 seats?                   | SALES",
    })
    void routesFrenchAndEnglishAlike(String message, Department expected) throws InterruptedException {
        var result = jev.ask(message, Triage.QUESTIONS);

        var response = ((JevClient.Result.Success) result).response();
        assertEquals(expected, Triage.of(response.answers()).department());
    }
}
