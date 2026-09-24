package assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import assistant.Command.Colour;
import assistant.Command.Room;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import typed.decision.model.Answer;
import typed.decision.model.Probability;

class AssistantTest {
    /** A Thursday, late evening, so an alarm without a day at 07:00 means tomorrow. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 22, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "réveille-moi à 7h                | 07:00",
        "à 7h30 du soir                   | 19:30",
        "vers sept heures et demie        | 07:30",
        "rdv à 14:15                      | 14:15",
        "wake me up at 6 am               | 06:00",
        "at eight o'clock in the evening  | 20:00",
        "à midi                           | 12:00",
    })
    void findsTimes(String message, LocalTime expected) {
        assertEquals(List.of(expected), Candidates.of(message, TODAY).times().stream().map(c -> c.value()).toList());
    }

    @ParameterizedTest
    @CsvSource({"alarme à 25h", "infinite lightyear alarm", "13 pm"})
    void findsNoTimeInNonsense(String message) {
        assertEquals(List.of(), Candidates.of(message, TODAY).times());
    }

    @Test
    void longestDatePhraseWins() {
        var dates = Candidates.of("il fera beau après-demain à Paris", TODAY).dates();

        assertEquals(List.of(TODAY.plusDays(2)), dates.stream().map(c -> c.value()).toList());
    }

    @Test
    void findsWordsInBothLanguages() {
        var candidates = Candidates.of("Mets la lumière du salon en bleu, and the bedroom red", TODAY);

        assertEquals(List.of(Colour.BLUE, Colour.RED), candidates.colours().stream().map(c -> c.value()).toList());
        assertEquals(List.of(Room.LIVING_ROOM, Room.BEDROOM), candidates.rooms().stream().map(c -> c.value()).toList());
    }

    @Test
    void asksOnlySlotsThatHaveCandidates() {
        var questions = Assistant.questions(Candidates.of("réveille-moi à 7h", TODAY), Assistant.Mode.SINGLE);

        assertEquals(Set.of("intelligible", "intent", "alarm_time", "event_time"), questions.keySet());
    }

    @Test
    void alarmWithoutDayRingsAtTheNextOccurrence() {
        var assistant = read("réveille-moi à 7h", "alarm_set", 0.9, 0.99, Map.of("alarm_time", "7h", "event_time", "7h"));

        assertEquals(new Outcome.Execute(new Command.SetAlarm(TODAY.plusDays(1), LocalTime.of(7, 0))), assistant.outcome(NOW));
    }

    @Test
    void alarmWithoutTimeAsksBack() {
        var outcome = read("infinite lightyear alarm", "alarm_set", 0.9, 0.9, Map.of()).outcome(NOW);

        assertEquals(Intent.ALARM_SET, ((Outcome.Missing) outcome).intent());
    }

    @Test
    void speculativeSlotOfAnotherIntentIsIgnored() {
        var assistant = read("quel temps demain à 7h", "weather_query", 0.9, 0.99,
            Map.of("alarm_time", "7h", "alarm_date", "demain", "weather_date", "demain", "event_date", "none",
                "event_time", "none"));

        assertEquals(new Outcome.Execute(new Command.QueryWeather(TODAY.plusDays(1), Optional.empty())), assistant.outcome(NOW));
    }

    @Test
    void gatesComeBeforeHandlers() {
        assertEquals(new Outcome.NotUnderstood(), read("bweruserisurapo", "alarm_set", 0.9, 0.1, Map.of()).outcome(NOW));
        assertEquals(Outcome.Unsure.class, read("bof", "alarm_set", 0.59, 0.9, Map.of()).outcome(NOW).getClass());
        assertEquals(new Outcome.Unsupported(), read("fais la vaisselle", "unsupported", 0.9, 0.9, Map.of()).outcome(NOW));
        assertEquals(new Outcome.Unhandled(Intent.PLAY_MUSIC), read("joue du jazz", "play_music", 0.9, 0.9, Map.of()).outcome(NOW));
    }

    @Test
    void readsNoneAsNoValue() {
        var assistant = read("à 7h", "alarm_set", 0.9, 0.9, Map.of("alarm_time", "none", "event_time", "none"));

        assertFalse(assistant.value(Assistant.ALARM_TIME).isPresent());
    }

    @Test
    void multiAddsOneNoulPerHandler() {
        var questions = Assistant.questions(Candidates.of("", TODAY), Assistant.Mode.MULTI);

        assertEquals(Set.of("intelligible", "intent", "asks_alarm_set", "asks_weather_query", "asks_iot_hue_lightchange",
            "asks_calendar_set"), questions.keySet());
    }

    @Test
    void multiRunsEveryClearYesAndFlagsTheUnsure() {
        var answers = answers("alarm_set", 0.5, 0.99,
            Map.of("alarm_time", "7h", "event_time", "none", "alarm_date", "none", "weather_date", "demain",
                "event_date", "none"));
        answers.put("asks_alarm_set", new Answer.Noul(new Probability(0.9)));
        answers.put("asks_weather_query", new Answer.Noul(new Probability(0.8)));
        answers.put("asks_iot_hue_lightchange", new Answer.Noul(new Probability(0.5)));
        answers.put("asks_calendar_set", new Answer.Noul(new Probability(0.1)));

        var outcomes = Assistant.of(answers, Candidates.of("réveille-moi à 7h et météo de demain", TODAY)).outcomes(NOW);

        assertEquals(List.of(
            new Outcome.Execute(new Command.SetAlarm(TODAY.plusDays(1), LocalTime.of(7, 0))),
            new Outcome.Unsure(Intent.IOT_HUE_LIGHTCHANGE, new Probability(0.5)),
            new Outcome.Execute(new Command.QueryWeather(TODAY.plusDays(1), Optional.empty()))), outcomes);
    }

    @Test
    void multiWithNoRequestedHandlerFallsBackToTheIntent() {
        var answers = answers("play_music", 0.9, 0.99, Map.of());
        Assistant.HANDLED.keySet().forEach(i -> answers.put(Assistant.asksId(i), new Answer.Noul(new Probability(0.05))));

        var outcomes = Assistant.of(answers, Candidates.of("joue du jazz", TODAY)).outcomes(NOW);

        assertEquals(List.of(new Outcome.Unhandled(Intent.PLAY_MUSIC)), outcomes);
    }

    /** Fake Jev answers; {@code slots} must cover every slot that has candidates, as the real API would. */
    private static Assistant read(String message, String intent, double confidence, double intelligible,
                                  Map<String, String> slots) {
        return Assistant.of(answers(intent, confidence, intelligible, slots), Candidates.of(message, TODAY));
    }

    private static Map<String, Answer> answers(String intent, double confidence, double intelligible, Map<String, String> slots) {
        var answers = new HashMap<String, Answer>();
        answers.put("intelligible", new Answer.Noul(new Probability(intelligible)));
        answers.put("intent", new Answer.Choice(intent, Map.of(intent, new Probability(confidence)), new Probability(confidence)));
        slots.forEach((id, pick) -> answers.put(id, new Answer.Choice(pick, Map.of(), new Probability(1))));
        return answers;
    }
}
