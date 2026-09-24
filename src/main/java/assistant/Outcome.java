package assistant;

import typed.decision.model.Probability;

/** What the assistant does with a message, from "no idea" to running a command. */
public sealed interface Outcome {
    /** Not a request at all: a typo, keyboard mash, or noise. */
    record NotUnderstood() implements Outcome {}

    /** A request, but Jev is not sure which one; ask the user to rephrase instead of guessing. */
    record Unsure(Intent best, Probability confidence) implements Outcome {}

    /** A request none of the known intents covers. */
    record Unsupported() implements Outcome {}

    /** A known intent this assistant has no handler for. */
    record Unhandled(Intent intent) implements Outcome {}

    /** A handled intent whose required value is not in the message; {@code question} asks for it. */
    record Missing(Intent intent, String question) implements Outcome {}

    record Execute(Command command) implements Outcome {}
}
