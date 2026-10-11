package dev.goldberry.css.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.conformance.Outcome.Kind;

/// One line of `rows.txt`: what to probe, what it does today, and which item of
/// the CSS plan changes that.
///
/// ```text
/// decl  | transition: opacity 200ms ease | IGNORED | CSS-15 | transition: opacity 200ms ease-enter
/// sheet | a:not(.b) { color: red }       | REFUSED | CSS-21
/// ```
///
/// Columns are separated by ` | ` with the spaces, so a `|` inside CSS
/// (`ns|a`) is not a column break. `#` starts a comment line.
///
/// @param kind      `decl` or `sheet`
/// @param css       the declaration, or the sheet's text
/// @param expected  what the engine does with it today
/// @param plan      `-` for a feature the subset has; otherwise the plan item
///                  that will change this row (`CSS-15`), `by-design` for a gap
///                  kept on purpose, or `unplanned` for one no item covers yet
/// @param reference for an `IGNORED` row, the declaration it comes out equal
///                  to; null otherwise
/// @param line      where the row is in `rows.txt`, for the failure message
record Row(
        Kind kind,
        String css,
        Outcome expected,
        String plan,
        @Nullable String reference,
        int line) {

    static final String RESOURCE = "rows.txt";

    private static final Pattern PLAN_ITEM = Pattern.compile("CSS-\\d{2}");
    private static final String SEPARATOR = " | ";

    Row {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(css, "css");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(plan, "plan");
    }

    /// Every row in the table, in file order.
    static List<Row> all() {
        try (var in = Row.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing beside " + Row.class.getName());
            }
            var rows = new ArrayList<Row>();
            var number = 0;
            for (var text : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList()) {
                number++;
                var trimmed = text.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    rows.add(parse(trimmed, number));
                }
            }
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// One row, from its line.
    static Row parse(String text, int line) {
        var columns = split(text);
        if (columns.size() < 4 || columns.size() > 5) {
            throw new IllegalArgumentException(
                    RESOURCE + ":" + line + " has " + columns.size() + " columns, not 4 or 5: " + text);
        }
        var kind = switch (columns.getFirst()) {
            case "decl" -> Kind.DECLARATION;
            case "sheet" -> Kind.SHEET;
            default ->
                throw new IllegalArgumentException(
                        RESOURCE + ":" + line + ": the kind is decl or sheet, not " + columns.getFirst());
        };
        var expected = Outcome.valueOf(columns.get(2).toUpperCase(Locale.ROOT));
        var reference = columns.size() == 5 ? columns.get(4) : null;
        return new Row(kind, columns.get(1), expected, columns.get(3), reference, line);
    }

    /// Why this row cannot be right whatever the engine does, or null when it
    /// is well formed.
    ///
    /// The table is the plan's status, so its rows have to say which item
    /// changes them: a gap with no item is either kept on purpose or written
    /// down as not covered yet, never silent.
    @Nullable
    String malformation() {
        if (!expected.fits(kind)) {
            return expected + " is not an outcome a " + kind + " row can have";
        }
        if (expected.supported() != plan.equals("-")) {
            return expected.supported()
                    ? "a supported row has no plan item, so its plan column is -, not " + plan
                    : "a gap names the plan item that closes it, by-design or unplanned, not -";
        }
        if (!expected.supported()
                && !PLAN_ITEM.matcher(plan).matches()
                && !plan.equals("by-design")
                && !plan.equals("unplanned")) {
            return "the plan column is CSS-NN, by-design or unplanned, not " + plan;
        }
        if ((expected == Outcome.IGNORED) != (reference != null)) {
            return "an IGNORED row, and only an IGNORED row, names the declaration it equals";
        }
        return null;
    }

    @Override
    public String toString() {
        return RESOURCE + ":" + line + " " + css;
    }

    private static List<String> split(String text) {
        var columns = new ArrayList<String>();
        var from = 0;
        while (true) {
            var at = text.indexOf(SEPARATOR, from);
            if (at < 0) {
                columns.add(text.substring(from).strip());
                return columns;
            }
            columns.add(text.substring(from, at).strip());
            from = at + SEPARATOR.length();
        }
    }
}
