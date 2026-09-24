package typed.decision.model;

import java.util.Map;

public sealed interface Answer {
    /** {@code noul}: probability that the answer is yes. */
    record Noul(Probability noul) implements Answer {}

    record Choice(String choice, Map<String, Probability> probabilities, Probability confidence) implements Answer {}

    /** {@code score}: expected level index, so it can fall between two levels. Maps are keyed by level index. */
    record Score(double score, Map<String, String> legend, Map<String, Probability> probabilities, Probability confidence)
        implements Answer {}

    /** Throws if the answer is missing or of the wrong type: Jev answers under our ids, so that is a contract breach. */
    static <A extends Answer> A get(Map<String, Answer> answers, String id, Class<A> type) {
        var answer = answers.get(id);
        if (!type.isInstance(answer)) {
            throw new IllegalStateException("Expected a %s answer for '%s', got %s".formatted(type.getSimpleName(), id, answer));
        }
        return type.cast(answer);
    }
}
