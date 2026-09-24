package typed.decision.model;

import java.util.List;
import java.util.Map;

public sealed interface Question {
    String instructions();

    record Noul(String instructions) implements Question {}

    /** {@code criteria}: option label to its description. */
    record Choice(String instructions, Map<String, String> criteria) implements Question {
        public Choice {
            criteria = Map.copyOf(criteria);
        }
    }

    /** {@code criteria}: level descriptions, lowest first. */
    record Score(String instructions, List<String> criteria) implements Question {
        public Score {
            criteria = List.copyOf(criteria);
        }
    }
}
