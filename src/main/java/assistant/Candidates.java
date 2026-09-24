package assistant;

import assistant.Command.Colour;
import assistant.Command.Room;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Values found by code in the message, French or English. Jev only selects among them, so a value missing
 * here can never reach a {@link Command}.
 */
// ponytail: fixed patterns and word lists; no absolute dates ("le 3 mars"), no minutes in words, a small city list.
// Widen the lists (or add a date parser) when the batch run shows misses.
public record Candidates(
    List<Candidate<LocalTime>> times,
    List<Candidate<LocalDate>> dates,
    List<Candidate<String>> cities,
    List<Candidate<Colour>> colours,
    List<Candidate<Room>> rooms) {

    /** {@code text} is the span as written, which is what Jev chooses between. */
    public record Candidate<T>(String text, T value) {}

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
        Map.entry("une", 1), Map.entry("un", 1), Map.entry("deux", 2), Map.entry("trois", 3), Map.entry("quatre", 4),
        Map.entry("cinq", 5), Map.entry("six", 6), Map.entry("sept", 7), Map.entry("huit", 8), Map.entry("neuf", 9),
        Map.entry("dix", 10), Map.entry("onze", 11), Map.entry("douze", 12),
        Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5),
        Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10),
        Map.entry("eleven", 11), Map.entry("twelve", 12));
    private static final String HOUR = "(\\d{1,2}|" + alternation(NUMBER_WORDS.keySet()) + ")";
    private static final String LATER = "du soir|de l'après-midi|de l’après-midi|pm|p\\.m\\.|in the evening|at night";
    private static final String EARLIER = "du matin|am|a\\.m\\.|in the morning";

    private static final List<TimePattern> TIME_PATTERNS = List.of(
        new TimePattern("midi|noon", _ -> LocalTime.NOON),
        new TimePattern("minuit|midnight", _ -> LocalTime.MIDNIGHT),
        new TimePattern("(\\d{1,2}):(\\d{2})\\s*(" + LATER + "|" + EARLIER + ")?",
            m -> time(hour(m.group(1)), Integer.parseInt(m.group(2)), m.group(3))),
        new TimePattern(HOUR + "\\s*(?:h|heures?)\\s*(\\d{2}|et demie|et quart)?\\s*(" + LATER + "|" + EARLIER + ")?",
            m -> time(hour(m.group(1)), minutes(m.group(2)), m.group(3))),
        new TimePattern(HOUR + "\\s*(?:o'clock\\s*)?(" + LATER + "|" + EARLIER + ")",
            m -> time(hour(m.group(1)), 0, m.group(2))),
        new TimePattern(HOUR + "\\s*o'clock", m -> time(hour(m.group(1)), 0, null)));

    private static final Map<String, Function<LocalDate, LocalDate>> DATE_WORDS = dateWords();

    private static final List<String> CITIES = List.of(
        "Paris", "Lyon", "Marseille", "Toulouse", "Nice", "Nantes", "Bordeaux", "Lille", "Strasbourg", "Montpellier",
        "Rennes", "Grenoble", "Bruxelles", "Brussels", "Genève", "Geneva", "Montréal", "Montreal", "Londres", "London",
        "New York", "Berlin", "Madrid", "Barcelone", "Barcelona", "Rome", "Lisbonne", "Lisbon", "Amsterdam",
        "Tokyo", "Chicago", "Los Angeles", "San Francisco", "Seattle", "Boston", "Dublin");

    public static Candidates of(String message, LocalDate today) {
        return new Candidates(
            times(message),
            words(message, DATE_WORDS).stream().map(c -> new Candidate<>(c.text(), c.value().apply(today))).toList(),
            words(message, CITIES.stream().collect(Collectors.toMap(String::toLowerCase, c -> c))),
            words(message, byWord(Colour.values(), c -> c.words)),
            words(message, byWord(Room.values(), r -> r.words)));
    }

    private static List<Candidate<LocalTime>> times(String message) {
        var claimed = new boolean[message.length()];
        var found = new ArrayList<Candidate<LocalTime>>();
        for (var pattern : TIME_PATTERNS) {
            pattern.regex.matcher(message).results()
                .filter(m -> !anyClaimed(claimed, m))
                .forEach(m -> {
                    try {
                        found.add(new Candidate<>(m.group().strip(), pattern.value.apply(m)));
                        Arrays.fill(claimed, m.start(), m.end(), true);
                    } catch (DateTimeException _) {
                        // "25h" or "13 pm": not a time, so not a candidate
                    }
                });
        }
        return distinct(found);
    }

    /** Longest phrase first, so "après-demain" wins over the "demain" inside it. */
    private static <T> List<Candidate<T>> words(String message, Map<String, T> byWord) {
        var regex = Pattern.compile("(?iu)(?<![\\p{L}\\d])(" + alternation(byWord.keySet()) + ")(?![\\p{L}\\d])");
        return distinct(regex.matcher(message).results()
            .map(m -> new Candidate<>(m.group(), byWord.get(m.group().toLowerCase())))
            .toList());
    }

    private static String alternation(Collection<String> words) {
        return words.stream().sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote).collect(Collectors.joining("|"));
    }

    private static <E> Map<String, E> byWord(E[] values, Function<E, List<String>> words) {
        return Arrays.stream(values)
            .flatMap(v -> words.apply(v).stream().map(w -> Map.entry(w, v)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** The same span written twice is one option for Jev. */
    private static <T> List<Candidate<T>> distinct(List<Candidate<T>> candidates) {
        var byText = new LinkedHashMap<String, Candidate<T>>();
        candidates.forEach(c -> byText.putIfAbsent(c.text().toLowerCase(), c));
        return List.copyOf(byText.values());
    }

    private static boolean anyClaimed(boolean[] claimed, MatchResult m) {
        for (var i = m.start(); i < m.end(); i++) {
            if (claimed[i]) return true;
        }
        return false;
    }

    private static int hour(String hour) {
        var word = NUMBER_WORDS.get(hour.toLowerCase());
        return word != null ? word : Integer.parseInt(hour);
    }

    private static int minutes(String minutes) {
        if (minutes == null) return 0;
        return switch (minutes.toLowerCase()) {
            case "et demie" -> 30;
            case "et quart" -> 15;
            default -> Integer.parseInt(minutes);
        };
    }

    private static LocalTime time(int hour, int minute, String period) {
        if (period == null) return LocalTime.of(hour, minute);
        if (hour > 12) throw new DateTimeException("Hour " + hour + " with a period");
        var later = Pattern.compile("(?iu)" + LATER).matcher(period).matches();
        return LocalTime.of(later ? hour % 12 + 12 : hour % 12, minute);
    }

    private static Map<String, Function<LocalDate, LocalDate>> dateWords() {
        var words = new HashMap<String, Function<LocalDate, LocalDate>>();
        Stream.of("aujourd'hui", "aujourd’hui", "ce soir", "ce matin", "cet après-midi", "today", "tonight",
            "this morning", "this afternoon", "this evening").forEach(w -> words.put(w, d -> d));
        Stream.of("demain", "tomorrow").forEach(w -> words.put(w, d -> d.plusDays(1)));
        Stream.of("après-demain", "day after tomorrow").forEach(w -> words.put(w, d -> d.plusDays(2)));
        var french = List.of("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche");
        for (var day : DayOfWeek.values()) {
            var next = TemporalAdjusters.nextOrSame(day);
            words.put(french.get(day.ordinal()), d -> d.with(next));
            words.put(day.name().toLowerCase(), d -> d.with(next));
        }
        return Map.copyOf(words);
    }

    private record TimePattern(Pattern regex, Function<MatchResult, LocalTime> value) {
        TimePattern(String regex, Function<MatchResult, LocalTime> value) {
            this(Pattern.compile("(?iu)(?<![\\p{L}\\d])" + regex + "(?![\\p{L}\\d])"), value);
        }
    }
}
