package snake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.typesafe.jev.JevJson;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import snake.Game.Cell;
import typed.decision.model.Answer;
import typed.decision.model.DecisionModel.Decision;
import typed.decision.model.Probability;

class GameTest {
    private static final Random RANDOM = new Random(1);
    /** Head at (3,5) heading right, apple two cells right of the head. */
    private static final Game GAME = new Game(List.of(new Cell(3, 5), new Cell(2, 5), new Cell(1, 5)), new Cell(5, 5), 0, false);

    @Test
    void movesWithoutGrowing() {
        var next = GAME.step(Direction.UP, RANDOM);
        assertEquals(List.of(new Cell(3, 4), new Cell(3, 5), new Cell(2, 5)), next.snake());
        assertFalse(next.over());
    }

    @Test
    void eatsGrowsAndMovesTheApple() {
        var next = GAME.step(Direction.RIGHT, RANDOM).step(Direction.RIGHT, RANDOM);
        assertEquals(4, next.snake().size());
        assertEquals(1, next.apples());
        assertFalse(next.snake().contains(next.apple()));
    }

    @Test
    void diesOnItsBodyAndOnWalls() {
        assertTrue(GAME.step(Direction.LEFT, RANDOM).over());
        var corner = new Game(List.of(new Cell(0, 0)), new Cell(5, 5), 0, false);
        assertTrue(corner.kills(Direction.UP));
        assertTrue(corner.kills(Direction.LEFT));
        assertFalse(corner.kills(Direction.RIGHT));
    }

    @Test
    void headMayTakeTheCellTheTailLeaves() {
        var loop = new Game(List.of(new Cell(1, 1), new Cell(2, 1), new Cell(2, 2), new Cell(1, 2)), new Cell(9, 9), 0, false);
        assertFalse(loop.kills(Direction.DOWN));
    }

    @Test
    void fullGivesOffsetsAndDeadlyMoves() {
        var state = GAME.state(Semantics.FULL);
        assertTrue(state.contains("The apple is at column 5, row 5: 2 columns to the right and in the same row."), state);
        assertTrue(state.contains("Moving left is deadly"), state);
        assertTrue(state.contains("Moving up is safe"), state);
    }

    @Test
    void simplifiedGivesOnlyThePosition() {
        assertEquals("""
            Snake game. Grid is 15x10. x: 0->14 (left to right), y: 0->9 (top to bottom).
            Snake head: (3,5)
            Snake body is 2 cells long: (2,5), (1,5)
            Apple: (5,5)
            Can move up/down/right""", GAME.state(Semantics.SIMPLIFIED));
    }

    @Test
    void formattedIsValidJson() {
        var json = JevJson.mapper().readTree(GAME.state(Semantics.FORMATTED));
        assertEquals(3, json.get("head").get("x").asInt());
        assertEquals(2, json.get("body").size());
        assertEquals(5, json.get("apple").get("x").asInt());
        assertEquals(List.of("up", "down", "right"), json.get("moves").valueStream().map(m -> m.asString()).toList());
    }

    @Test
    void gridDrawsEverySquare() {
        var rows = GAME.state(Semantics.GRID).lines().toList();
        assertEquals(2 + Game.HEIGHT, rows.size());
        assertEquals(". B B H . A" + " .".repeat(9), rows.get(7));
    }

    @Test
    void rejectsPositionsOutsideTheGrid() {
        assertThrows(IllegalArgumentException.class, () -> new Game(List.of(new Cell(15, 0)), new Cell(1, 1), 0, false));
        assertThrows(IllegalArgumentException.class, () -> new Game(List.of(), new Cell(1, 1), 0, false));
    }

    @Test
    void roundTripsThroughJson() {
        var mapper = JevJson.mapper();
        assertEquals(GAME, mapper.readValue(mapper.writeValueAsString(GAME), Game.class));
    }

    @Test
    void playsTheModelsChoice() throws InterruptedException {
        var turn = Turn.play(GAME, Semantics.FULL, (state, questions) -> new Decision.Answered(Map.of(Game.MOVE, new Answer.Choice("up",
            Map.of("up", new Probability(0.9), "right", new Probability(0.1)), new Probability(0.9))), BigDecimal.ONE), RANDOM);
        assertEquals(Direction.UP, turn.move());
        assertEquals(new Cell(3, 4), turn.game().snake().getFirst());
        assertNull(turn.error());
    }

    @Test
    void anUnknownLabelIsAnErrorNotAMove() throws InterruptedException {
        var turn = Turn.play(GAME, Semantics.FULL, (state, questions) -> new Decision.Answered(Map.of(Game.MOVE, new Answer.Choice("north",
            Map.of(), new Probability(0.9))), BigDecimal.ZERO), RANDOM);
        assertNotNull(turn.error());
        assertEquals(GAME, turn.game());
    }
}
