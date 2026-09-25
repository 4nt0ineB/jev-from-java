package typed.decision.model;

import java.math.BigDecimal;
import java.util.Map;

/** Anything that answers typed questions about a state: Jev, a self-hosted Laya, or an adapter over another model. */
@FunctionalInterface
public interface DecisionModel {
    Decision decide(String state, Map<String, Question> questions) throws InterruptedException;

    sealed interface Decision {
        record Answered(Map<String, Answer> answers, BigDecimal usd) implements Decision {}
        /** {@code reason} is shown to the user as is. */
        record Failed(String reason) implements Decision {}
    }
}
