package dev.goldberry.css.parse;

import java.util.Objects;

/// A rule a [ParseMode#LENIENT] parse left out, and why.
///
/// The warning in the log says the same thing once; this is the value a lint
/// or a test reads instead of the log. `StyleLint` reports each one as a
/// finding, so an application that asks gets the same answer as one that reads
/// its log.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#strict-and-lenient-sheets).
///
/// @param text   the selector list or the at-rule's prelude, as written
/// @param reason what in it is outside the subset
/// @param line   1-based line the rule starts on
/// @param column 1-based column
public record DroppedRule(String text, String reason, int line, int column) {

    public DroppedRule {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public String toString() {
        return line + ":" + column + " " + text + " — " + reason;
    }
}
