package dev.goldberry.text.mark;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// A word being typed after a trigger character, such as `@ann` while somebody
/// is mentioned: what a composer offers suggestions for.
///
/// ```java
/// var edit = MarkedEdit.of(MarkedText.of("Ask @an"));
/// var query = MentionQuery.at(edit, Set.of("@")).orElseThrow();
/// query.trigger();                 // "@"
/// query.query();                   // "an"
/// var piece = query.completion("@Ann Lee", new MarkKind.Mention("user:42"), edit.value());
/// edit = edit.replace(query.start(), query.end(), piece);   // "Ask @Ann Lee |"
/// ```
///
/// ## When there is one
///
/// A query is open when, with nothing selected, the caret is in a **word that
/// begins with a trigger**: the trigger at the start of the text, after
/// whitespace or after an opening bracket or quote, then letters, digits,
/// combining marks or `_` up to the caret. So `mail@example` opens nothing, a
/// space closes the query, and so does a caret moved out of the word. Text under
/// code or inside an atom opens none: a mention already put in is not a query
/// for another. A query longer than [#MAX_LENGTH] is not one either.
///
/// ## What it covers
///
/// [#query()] is what was typed between the trigger and the caret, which is
/// what suggestions are matched against. [#start()] to [#end()] is the whole
/// word, trigger included and running on past the caret when the caret is in
/// the middle of it: what a chosen suggestion replaces.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
///
/// @param trigger the trigger character that opened it, such as `@`
/// @param query   what was typed between the trigger and the caret; may be empty
/// @param start   where the trigger is
/// @param end     where the word ends, at or after the caret
public record MentionQuery(String trigger, String query, int start, int end) {

    /// The longest query, in chars: past it, the word is not a name being
    /// looked up any more.
    public static final int MAX_LENGTH = 64;

    public MentionQuery {
        trigger = requireTrigger(trigger);
        Objects.requireNonNull(query, "query");
        if (start < 0 || end < start + trigger.length() + query.length()) {
            throw new IllegalArgumentException("a query covers its trigger and what was typed after it: start=" + start
                    + " end=" + end + " for " + trigger + query);
        }
    }

    /// The word from the trigger to the caret, as typed: the trigger and the
    /// query.
    public String typed() {
        return trigger + query;
    }

    /// The open query at `edit`'s caret, for any of `triggers`; empty when
    /// there is none.
    ///
    /// @throws IllegalArgumentException when a trigger is not one character
    ///         that could start a word
    public static Optional<MentionQuery> at(MarkedEdit edit, Set<String> triggers) {
        Objects.requireNonNull(edit, "edit");
        var checked = requireTriggers(triggers);
        if (checked.isEmpty() || edit.hasSelection()) {
            return Optional.empty();
        }
        var value = edit.value();
        var text = value.text();
        var caret = edit.caret();
        var at = caret;
        while (at > 0) {
            var point = text.codePointBefore(at);
            var width = Character.charCount(point);
            if (checked.contains(Character.toString(point))) {
                return opened(value, at - width, width, caret);
            }
            if (!inWord(point) || caret - at >= MAX_LENGTH) {
                return Optional.empty();
            }
            at -= width;
        }
        return Optional.empty();
    }

    /// The query with its trigger at `start`, if the trigger begins a word and
    /// nothing between it and the caret is code or an atom.
    private static Optional<MentionQuery> opened(MarkedText value, int start, int width, int caret) {
        var text = value.text();
        if (start > 0 && !opensWord(text.codePointBefore(start))) {
            return Optional.empty();
        }
        if (covered(value, start, Math.max(caret, start + width))) {
            return Optional.empty();
        }
        var end = caret;
        while (end < text.length()) {
            var point = text.codePointAt(end);
            var next = end + Character.charCount(point);
            if (!inWord(point) || covered(value, end, next)) {
                break;
            }
            end = next;
        }
        return Optional.of(new MentionQuery(
                text.substring(start, start + width), text.substring(start + width, caret), start, end));
    }

    /// Whether any of `[from, to)` is code or part of an atom.
    private static boolean covered(MarkedText value, int from, int to) {
        for (var mark : value.marks()) {
            if (mark.overlaps(from, to) && (mark.kind().isAtomic() || mark.kind() == MarkKind.Style.CODE)) {
                return true;
            }
        }
        return false;
    }

    /// Whether `point` can be part of a name being looked up.
    private static boolean inWord(int point) {
        return Character.isLetterOrDigit(point)
                || point == '_'
                || switch (Character.getType(point)) {
                    case Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true;
                    default -> false;
                };
    }

    /// Whether a trigger after `point` is at a word start: after whitespace, an
    /// opening bracket or an opening quote.
    private static boolean opensWord(int point) {
        return Character.isWhitespace(point)
                || point == '"'
                || point == '\''
                || switch (Character.getType(point)) {
                    case Character.START_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION -> true;
                    default -> false;
                };
    }

    /// What goes in place of this query when `text` is chosen for it: `text`
    /// under `kind`, and a space after it unless whitespace follows already, so
    /// the next word is not typed against it.
    ///
    /// `kind` is usually a [MarkKind.Mention], which makes the text an atom;
    /// null puts in plain text, such as an emoji for a `:` shortcode.
    ///
    /// @param in the text this query was found in, which says what follows it
    /// @throws IllegalArgumentException for empty `text`
    public MarkedText completion(String text, @Nullable MarkKind kind, MarkedText in) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(in, "in");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("a query is completed with something to put in its place");
        }
        if (end > in.length()) {
            throw new IllegalArgumentException(
                    "this query ends at " + end + ", past the " + in.length() + " chars it was given");
        }
        var spaced = end == in.length() || !Character.isWhitespace(in.text().codePointAt(end));
        var piece = MarkedText.of(spaced ? text + " " : text);
        return kind == null ? piece : piece.mark(0, text.length(), kind);
    }

    /// `triggers` checked: each one code point that is not a letter, a digit or
    /// whitespace, since a trigger inside a word would open a query in every
    /// word.
    ///
    /// @throws IllegalArgumentException for a trigger that is not
    public static Set<String> requireTriggers(Set<String> triggers) {
        Objects.requireNonNull(triggers, "triggers");
        var checked = new LinkedHashSet<String>();
        for (var trigger : triggers) {
            checked.add(requireTrigger(trigger));
        }
        return Set.copyOf(checked);
    }

    private static String requireTrigger(String trigger) {
        Objects.requireNonNull(trigger, "trigger");
        if (trigger.isEmpty() || trigger.codePointCount(0, trigger.length()) != 1) {
            throw new IllegalArgumentException(
                    "a trigger is one character, such as `@` or `#`, and `" + trigger + "` is not");
        }
        var point = trigger.codePointAt(0);
        if (inWord(point) || Character.isWhitespace(point)) {
            throw new IllegalArgumentException("a trigger cannot be part of a word or a space, or every word"
                    + " would open a query; `" + trigger + "` is");
        }
        return trigger;
    }
}
