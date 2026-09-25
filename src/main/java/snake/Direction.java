package snake;

import java.util.Locale;

public enum Direction {
    UP(0, -1, "Move up, one row closer to row 0."),
    DOWN(0, 1, "Move down, one row further from row 0."),
    LEFT(-1, 0, "Move left, one column closer to column 0."),
    RIGHT(1, 0, "Move right, one column further from column 0.");

    final int dx;
    final int dy;
    final String criterion;

    Direction(int dx, int dy, String criterion) {
        this.dx = dx;
        this.dy = dy;
        this.criterion = criterion;
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
