package snake;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;
import typed.decision.model.Answer;
import typed.decision.model.DecisionModel;
import typed.decision.model.DecisionModel.Decision;
import typed.decision.model.Probability;

/**
 * One model call and the position it led to, in the shape the page reads. When {@code error} is set, the game is
 * unchanged and the answer fields are empty.
 */
public record Turn(Game game, String state, Direction move, Map<String, Probability> probabilities,
                   Probability confidence, BigDecimal usd, long millis, String error) {

    public static Turn play(Game game, Semantics semantics, DecisionModel model, RandomGenerator random)
        throws InterruptedException {
        var state = game.state(semantics);
        var start = System.nanoTime();
        var decision = model.decide(state, Game.QUESTIONS);
        var millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        return switch (decision) {
            case Decision.Failed(var reason) -> failed(game, state, BigDecimal.ZERO, millis, reason);
            case Decision.Answered(var answers, var usd) -> {
                try {
                    var choice = Answer.get(answers, Game.MOVE, Answer.Choice.class);
                    var move = Direction.valueOf(choice.choice().toUpperCase(Locale.ROOT));
                    yield new Turn(game.step(move, random), state, move, choice.probabilities(), choice.confidence(),
                        usd, millis, null);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    yield failed(game, state, usd, millis, "The reply broke the contract: " + e.getMessage());
                }
            }
        };
    }

    private static Turn failed(Game game, String state, BigDecimal usd, long millis, String error) {
        return new Turn(game, state, null, Map.of(), null, usd, millis, error);
    }
}
