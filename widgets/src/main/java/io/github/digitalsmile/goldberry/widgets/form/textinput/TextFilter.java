package io.github.digitalsmile.goldberry.widgets.form.textinput;

/// What a field will accept — `docs/core-widgets.md` §4's "input filters
/// (numeric etc.)".
///
/// ## It filters the text, not the keystroke
///
/// A filter is asked about **the whole value the edit would produce**, not about
/// the character that arrived. That is the difference between a filter that works
/// and one that only looks like it does: a numeric field that tested keystrokes
/// would accept `1-2-3` (every character is legal) and reject a pasted `-5`
/// (the minus arrives first, with no digits after it yet). Asking about the
/// result gets both right, and gets pasting right for free — a paste is one edit
/// like any other.
///
/// ## Rejecting, not correcting
///
/// A filter says yes or no; it does not rewrite. A filter that silently fixed
/// what was typed would move the caret out from under somebody mid-word, and
/// worse, would make the field's contents depend on the order the characters
/// arrived in. A rejected edit leaves the field exactly as it was, which is what
/// a user reads as "that key did nothing".
///
/// Formatting — grouping separators, a currency symbol, a date's slashes — is a
/// different job and belongs to whatever owns the value. A `date-picker` parses
/// and formats with a `java.time` formatter the application supplies (§4); it
/// does not do it with one of these.
@FunctionalInterface
public interface TextFilter {

    /// Whether a field may hold `text`.
    ///
    /// Called with the value the edit *would* produce. Must be pure and cheap: it
    /// runs on every keystroke, and one that queried something would make typing
    /// as slow as whatever it queried.
    boolean accepts(String text);

    /// Everything. What a field with no `filter=` has.
    TextFilter NONE = text -> true;

    /// Digits only — `0` to `9`, and nothing else.
    ///
    /// An empty field is accepted, because a user has to be able to clear one
    /// before typing a different number, and a filter that refused would trap
    /// whatever was in there.
    ///
    /// **Not "a number".** No sign, no decimal point, no grouping: this is what
    /// a PIN, a port, a year and a quantity are. A field that wants a decimal
    /// wants a filter that knows the locale's separator, and that is the
    /// application's to supply because the toolkit does not know whether the
    /// value is a price or a measurement.
    TextFilter DIGITS = text -> {
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    };

    /// A whole number, sign included — `-12`, `0`, `4096`.
    ///
    /// A lone `-` is accepted, because it is what a field looks like after the
    /// first keystroke of `-12` and rejecting it would make a negative number
    /// untypeable.
    TextFilter INTEGER = text -> {
        if (text.isEmpty() || text.equals("-")) {
            return true;
        }
        var digits = text.startsWith("-") ? text.substring(1) : text;
        return !digits.isEmpty() && DIGITS.accepts(digits);
    };

    /// Letters and digits, in any script — `Character.isLetterOrDigit`.
    ///
    /// What `code-input type="alnum"` will want.
    TextFilter ALPHANUMERIC = text -> text.codePoints().allMatch(Character::isLetterOrDigit);

    /// The filter named in markup — `text-input filter="digits"`.
    ///
    /// An unknown name is [#NONE] rather than a failure: a document naming a
    /// filter this toolkit does not have is a typo already visible in the markup,
    /// and a field that refuses every keystroke is a worse way to learn about it
    /// than a field that accepts them. The caller logs it — the same treatment
    /// an unparseable accelerator gets (ADR-0163).
    ///
    /// @return the named filter, or null if there is no such name
    static TextFilter named(String name) {
        if (name == null) {
            return NONE;
        }
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "none", "" -> NONE;
            case "digits" -> DIGITS;
            case "integer" -> INTEGER;
            case "alnum", "alphanumeric" -> ALPHANUMERIC;
            default -> null;
        };
    }
}
