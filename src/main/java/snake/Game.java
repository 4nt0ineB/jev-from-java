package snake;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import typed.decision.model.Question;

/**
 * One position, head first. Immutable and round-tripped through the browser, so the server keeps no game state.
 * {@code over} is set by the move that killed the snake.
 */
public record Game(List<Cell> snake, Cell apple, int apples, boolean over) {
    public static final int WIDTH = 15;
    public static final int HEIGHT = 10;
    public static final String MOVE = "move";

    public static final Map<String, Question> QUESTIONS = Map.of(MOVE, new Question.Choice(
        "Which way should the snake move next to get closer to the apple? Never pick a move that hits a wall or the"
            + " snake's own body.",
        Arrays.stream(Direction.values()).collect(Collectors.toMap(Direction::label, d -> d.criterion))));

    public record Cell(int x, int y) {
        Cell to(Direction d) {
            return new Cell(x + d.dx, y + d.dy);
        }

        boolean inside() {
            return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
        }
    }

    public Game {
        snake = List.copyOf(snake);
        if (snake.isEmpty() || !snake.stream().allMatch(Cell::inside) || !apple.inside()) {
            throw new IllegalArgumentException("Snake or apple outside the grid");
        }
    }

    public static Game start(RandomGenerator random) {
        var y = HEIGHT / 2;
        var snake = List.of(new Cell(3, y), new Cell(2, y), new Cell(1, y));
        return new Game(snake, freeCell(snake, random), 0, false);
    }

    /** The tail leaves its cell on the same move, so the head may take it. */
    public boolean kills(Direction d) {
        var next = head().to(d);
        return !next.inside() || snake.subList(0, snake.size() - 1).contains(next);
    }

    public Game step(Direction d, RandomGenerator random) {
        if (kills(d)) return new Game(snake, apple, apples, true);
        var next = new ArrayList<Cell>(snake.size() + 1);
        next.add(head().to(d));
        next.addAll(snake);
        if (!next.getFirst().equals(apple)) {
            next.removeLast();
            return new Game(next, apple, apples, false);
        }
        // ponytail: a full grid would have no free cell; unreachable in practice on 150 cells
        return new Game(next, freeCell(next, random), apples + 1, false);
    }

    public String state(Semantics semantics) {
        return switch (semantics) {
            case FULL -> full();
            case SIMPLIFIED -> simplified();
            case FORMATTED -> formatted();
            case GRID -> grid();
        };
    }

    /** Coordinates plus the facts code can compute for the model: offsets to the apple and which moves are deadly. */
    private String full() {
        var head = head();
        var moves = Arrays.stream(Direction.values())
            .map(d -> "Moving %s %s.".formatted(d.label(), kills(d) ? "is deadly (wall or body)" : "is safe"))
            .collect(Collectors.joining(" "));
        return """
            Snake game on a %d by %d grid. Columns go from 0 on the left to %d on the right, rows from 0 at the top \
            to %d at the bottom.
            The snake's head is at column %d, row %d. The snake is %d cells long.
            The apple is at column %d, row %d: %s and %s.
            %s""".formatted(WIDTH, HEIGHT, WIDTH - 1, HEIGHT - 1, head.x(), head.y(), snake.size(),
            apple.x(), apple.y(), offset(apple.x() - head.x(), "column", "to the right", "to the left"),
            offset(apple.y() - head.y(), "row", "down", "up"), moves);
    }

    /** The position and the moves that do not kill: which of them gets closer to the apple is left to the model. */
    private String simplified() {
        var body = body().stream().map(c -> "(%d,%d)".formatted(c.x(), c.y())).collect(Collectors.joining(", "));
        return """
            Snake game. Grid is %dx%d. x: 0->%d (left to right), y: 0->%d (top to bottom).
            Snake head: (%d,%d)
            Snake body is %d cells long: %s
            Apple: (%d,%d)
            Can move %s""".formatted(WIDTH, HEIGHT, WIDTH - 1, HEIGHT - 1, head().x(), head().y(),
            snake.size() - 1, body, apple.x(), apple.y(), safeMoves().isEmpty() ? "nowhere" : String.join("/", safeMoves()));
    }

    /** The same facts as {@link #simplified()}, as a JSON document. Built by hand: every value is an int or a label. */
    private String formatted() {
        return """
            {"game":"snake","grid":{"width":%d,"height":%d,"x":"0 is left, %d is right","y":"0 is top, %d is bottom"},\
            "head":%s,"body":[%s],"apple":%s,"moves":[%s]}""".formatted(WIDTH, HEIGHT, WIDTH - 1, HEIGHT - 1,
            json(head()), body().stream().map(Game::json).collect(Collectors.joining(",")), json(apple),
            safeMoves().stream().map(m -> '"' + m + '"').collect(Collectors.joining(",")));
    }

    /** The picture only, one symbol per square. Spaces keep each square a token of its own, so columns stay aligned. */
    private String grid() {
        return "Snake game. H is the snake's head, B its body, A the apple, . an empty square.\n\n"
            + IntStream.range(0, HEIGHT).mapToObj(y -> IntStream.range(0, WIDTH).mapToObj(x -> mark(new Cell(x, y)))
                .collect(Collectors.joining(" "))).collect(Collectors.joining("\n"));
    }

    private String mark(Cell c) {
        if (c.equals(head())) return "H";
        if (snake.contains(c)) return "B";
        return c.equals(apple) ? "A" : ".";
    }

    private List<Cell> body() {
        return snake.subList(1, snake.size());
    }

    private List<String> safeMoves() {
        return Arrays.stream(Direction.values()).filter(d -> !kills(d)).map(Direction::label).toList();
    }

    private static String json(Cell c) {
        return "{\"x\":%d,\"y\":%d}".formatted(c.x(), c.y());
    }

    private Cell head() {
        return snake.getFirst();
    }

    private static String offset(int delta, String unit, String positive, String negative) {
        if (delta == 0) return "in the same " + unit;
        var n = Math.abs(delta);
        return "%d %s%s %s".formatted(n, unit, n == 1 ? "" : "s", delta > 0 ? positive : negative);
    }

    private static Cell freeCell(List<Cell> snake, RandomGenerator random) {
        var free = IntStream.range(0, WIDTH * HEIGHT).mapToObj(i -> new Cell(i % WIDTH, i / WIDTH))
            .filter(c -> !snake.contains(c)).toList();
        return free.get(random.nextInt(free.size()));
    }
}
