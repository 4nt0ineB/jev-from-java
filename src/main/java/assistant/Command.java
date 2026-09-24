package assistant;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** What the assistant can actually execute. Every other intent is recognised but has no handler. */
public sealed interface Command {
    record SetAlarm(LocalDate date, LocalTime time) implements Command {}

    /** Empty {@code city}: the user's current location. */
    record QueryWeather(LocalDate date, Optional<String> city) implements Command {}

    /** Empty {@code room}: every light. */
    record ChangeLight(Colour colour, Optional<Room> room) implements Command {}

    /** Empty {@code time}: an all-day event. */
    record AddEvent(LocalDate date, Optional<LocalTime> time) implements Command {}

    enum Colour {
        RED("rouge", "red"), BLUE("bleu", "bleue", "blue"), GREEN("vert", "verte", "green"),
        YELLOW("jaune", "yellow"), WHITE("blanc", "blanche", "white"), PURPLE("violet", "violette", "purple"),
        ORANGE("orange"), PINK("rose", "pink");

        final List<String> words;

        Colour(String... words) {
            this.words = List.of(words);
        }
    }

    enum Room {
        LIVING_ROOM("salon", "living room", "lounge"), KITCHEN("cuisine", "kitchen"),
        BEDROOM("chambre", "bedroom"), BATHROOM("salle de bain", "salle de bains", "bathroom"),
        OFFICE("bureau", "office");

        final List<String> words;

        Room(String... words) {
            this.words = List.of(words);
        }
    }
}
