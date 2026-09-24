package assistant;

import assistant.Candidates.Candidate;
import assistant.Command.Colour;
import assistant.Command.Room;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import triage.Verdict;
import typed.decision.model.Answer;
import typed.decision.model.Probability;
import typed.decision.model.Question;

/**
 * The answers to {@link #questions}, read back into assistant terms. Every slot question is asked in the same call
 * as the intent, whatever the intent turns out to be; {@link #outcome} only reads the slots of the chosen one.
 */
public record Assistant(
    Candidates candidates,
    Probability intelligible,
    Intent intent,
    Map<Intent, Probability> intents,
    Probability confidence,
    Map<Slot<?>, Candidate<?>> chosen) {

    /** Threshold from TypeSafe's docs: 0.6 is enough for a low-stakes action, and a wrong alarm is low-stakes. */
    static final double ACT_CONFIDENCE = 0.6;

    static final String NONE = "none";

    /** One value a handler needs, picked by Jev among the {@link Candidates} of one kind. */
    /** {@code intent}: the handler that reads this slot. */
    public record Slot<T>(String id, Intent intent, String label, String instructions,
                          Function<Candidates, List<Candidate<T>>> candidates) {}

    public static final Slot<LocalTime> ALARM_TIME = new Slot<>("alarm_time", Intent.ALARM_SET, "Alarm · time",
        "If this message asks to set an alarm, which of these times should the alarm ring at?", Candidates::times);
    public static final Slot<LocalDate> ALARM_DATE = new Slot<>("alarm_date", Intent.ALARM_SET, "Alarm · day",
        "If this message asks to set an alarm, which of these days is the alarm for?", Candidates::dates);
    public static final Slot<LocalDate> WEATHER_DATE = new Slot<>("weather_date", Intent.WEATHER_QUERY, "Weather · day",
        "If this message asks about the weather, which of these days is it about?", Candidates::dates);
    public static final Slot<String> WEATHER_CITY = new Slot<>("weather_city", Intent.WEATHER_QUERY, "Weather · city",
        "If this message asks about the weather, which of these places is it about?", Candidates::cities);
    public static final Slot<Colour> LIGHT_COLOUR = new Slot<>("light_colour", Intent.IOT_HUE_LIGHTCHANGE, "Light · colour",
        "If this message asks to change the colour of the lights, which of these colours should they turn?",
        Candidates::colours);
    public static final Slot<Room> LIGHT_ROOM = new Slot<>("light_room", Intent.IOT_HUE_LIGHTCHANGE, "Light · room",
        "If this message asks to change the colour of the lights, which of these rooms are the lights in?",
        Candidates::rooms);
    public static final Slot<LocalDate> EVENT_DATE = new Slot<>("event_date", Intent.CALENDAR_SET, "Event · day",
        "If this message asks to add an event or a reminder to the calendar, which of these days is it on?",
        Candidates::dates);
    public static final Slot<LocalTime> EVENT_TIME = new Slot<>("event_time", Intent.CALENDAR_SET, "Event · time",
        "If this message asks to add an event or a reminder to the calendar, which of these times does it start at?",
        Candidates::times);

    public static final List<Slot<?>> SLOTS =
        List.of(ALARM_TIME, ALARM_DATE, WEATHER_DATE, WEATHER_CITY, LIGHT_COLOUR, LIGHT_ROOM, EVENT_DATE, EVENT_TIME);

    public Assistant {
        intents = Collections.unmodifiableMap(new EnumMap<>(intents));
        chosen = Map.copyOf(chosen);
    }

    /** A slot with no candidate is not asked: its only possible answer would be {@code none}. */
    public static Map<String, Question> questions(Candidates candidates) {
        var questions = new HashMap<String, Question>();
        questions.put("intelligible", new Question.Noul(
            "Is this an understandable request or question that someone could address to a voice assistant?"));
        questions.put("intent", new Question.Choice(
            "What does the user want the voice assistant to do?",
            Arrays.stream(Intent.values()).collect(Collectors.toMap(Intent::label, i -> i.description))));
        for (var slot : SLOTS) {
            var options = slot.candidates().apply(candidates);
            if (options.isEmpty()) continue;
            var criteria = new HashMap<String, String>();
            options.forEach(c -> criteria.put(c.text(), "“%s” as written in the message".formatted(c.text())));
            criteria.put(NONE, "None of these, or the message does not ask for this");
            questions.put(slot.id(), new Question.Choice(slot.instructions(), criteria));
        }
        return Map.copyOf(questions);
    }

    /** Throws if an answer is missing or of the wrong type, see {@link Answer#get}. */
    public static Assistant of(Map<String, Answer> answers, Candidates candidates) {
        var intent = Answer.get(answers, "intent", Answer.Choice.class);
        var intents = new EnumMap<Intent, Probability>(Intent.class);
        intent.probabilities().forEach((label, p) -> intents.put(Intent.fromLabel(label), p));
        var chosen = new HashMap<Slot<?>, Candidate<?>>();
        for (var slot : SLOTS) {
            var options = slot.candidates().apply(candidates);
            if (options.isEmpty()) continue;
            var pick = Answer.get(answers, slot.id(), Answer.Choice.class).choice();
            options.stream().filter(c -> c.text().equals(pick)).findFirst().ifPresent(c -> chosen.put(slot, c));
        }
        return new Assistant(candidates, Answer.get(answers, "intelligible", Answer.Noul.class).noul(),
            Intent.fromLabel(intent.choice()), intents, intent.confidence(), chosen);
    }

    /** The only unchecked cast: {@link #of} stores each slot's pick from that slot's own candidates. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> value(Slot<T> slot) {
        return Optional.ofNullable((Candidate<T>) chosen.get(slot)).map(Candidate::value);
    }

    /** The {@code n} most probable intents, most probable first. */
    public SequencedMap<Intent, Probability> top(int n) {
        return intents.entrySet().stream()
            .sorted(Comparator.comparingDouble((Map.Entry<Intent, Probability> e) -> e.getValue().value()).reversed())
            .limit(n)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, _) -> a, LinkedHashMap::new));
    }

    /** {@code now} resolves defaults such as "the next 7:00" for an alarm without a day. */
    public Outcome outcome(LocalDateTime now) {
        if (Verdict.of(intelligible) == Verdict.NO) return new Outcome.NotUnderstood();
        if (confidence.value() < ACT_CONFIDENCE) return new Outcome.Unsure(intent, confidence);
        return switch (intent) {
            case UNSUPPORTED -> new Outcome.Unsupported();
            case ALARM_SET -> value(ALARM_TIME)
                .<Outcome>map(time -> new Outcome.Execute(new Command.SetAlarm(
                    value(ALARM_DATE).orElse(time.isAfter(now.toLocalTime()) ? now.toLocalDate() : now.toLocalDate().plusDays(1)),
                    time)))
                .orElse(new Outcome.Missing(intent, "At what time should the alarm ring?"));
            case WEATHER_QUERY -> new Outcome.Execute(new Command.QueryWeather(
                value(WEATHER_DATE).orElse(now.toLocalDate()), value(WEATHER_CITY)));
            case IOT_HUE_LIGHTCHANGE -> value(LIGHT_COLOUR)
                .<Outcome>map(colour -> new Outcome.Execute(new Command.ChangeLight(colour, value(LIGHT_ROOM))))
                .orElse(new Outcome.Missing(intent, "Which colour should the lights turn?"));
            case CALENDAR_SET -> value(EVENT_DATE)
                .<Outcome>map(date -> new Outcome.Execute(new Command.AddEvent(date, value(EVENT_TIME))))
                .orElse(new Outcome.Missing(intent, "On which day is the event?"));
            default -> new Outcome.Unhandled(intent);
        };
    }
}
