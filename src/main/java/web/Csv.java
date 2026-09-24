package web;

import java.util.ArrayList;
import java.util.List;

/** RFC 4180: comma-separated, fields optionally quoted, {@code ""} for a quote inside quotes, CRLF or LF. */
final class Csv {
    private Csv() {
    }

    static List<List<String>> parse(String text) {
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        var quoted = false;
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            if (quoted) {
                if (c != '"') field.append(c);
                else if (i + 1 < text.length() && text.charAt(i + 1) == '"') field.append(text.charAt(++i));
                else quoted = false;
            } else switch (c) {
                case '"' -> quoted = true;
                case ',' -> {
                    row.add(field.toString());
                    field.setLength(0);
                }
                case '\r' -> { }
                case '\n' -> {
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                }
                default -> field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        rows.removeIf(r -> r.stream().allMatch(String::isBlank));
        return rows;
    }
}
