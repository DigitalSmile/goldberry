package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// What a [CodeInput] will accept — §4's `type="digits|alnum"`.
///
/// ## Per character, and dropping rather than refusing
///
/// [io.github.digitalsmile.goldberry.widgets.form.textinput.TextFilter] is asked
/// about the **whole value an edit would produce** and answers yes or no, and its
/// own documentation explains why: a filter that rewrote what was typed would
/// move the caret out from under somebody mid-word, and would make a field's
/// contents depend on the order the characters arrived in.
///
/// This one is asked about **one character at a time** and silently drops what it
/// does not want, which is the opposite policy, and it is the right one here for
/// two reasons that do not hold for a text field. There is **no caret to move** —
/// a code has one insertion point and it is wherever the filled boxes end — so
/// there is nothing for a correction to disturb. And the case §4 calls "the thing
/// users actually do" is a paste, out of a message that reads `Your code is
/// 123 456`: a whole-value filter rejects that paste entirely, and a per-character
/// one fills the six boxes. Refusing the paste a user was told to make is a worse
/// answer than ignoring a space.
///
/// The filters themselves are `TextFilter`'s, not second copies of them —
/// `TextFilter.ALPHANUMERIC`'s javadoc has said "what `code-input type=\"alnum\"`
/// will want" since it was written.
public enum CodeType {

    /// `0` to `9`. What an SMS or authenticator code is.
    DIGITS {
        @Override
        public boolean accepts(int codePoint) {
            return codePoint >= '0' && codePoint <= '9';
        }
    },

    /// Letters and digits in any script — `Character.isLetterOrDigit`, which is
    /// `TextFilter.ALPHANUMERIC`'s rule.
    ALNUM {
        @Override
        public boolean accepts(int codePoint) {
            return Character.isLetterOrDigit(codePoint);
        }
    };

    /// Whether one code point may occupy a box.
    ///
    /// A **code point** rather than a `char`, so a letter outside the basic plane
    /// takes one box rather than being split across two by its surrogate pair.
    /// Not a grapheme cluster, which is what `TextEdit` steps by: a combining
    /// mark is neither a letter nor a digit, so neither filter here can let one
    /// in and there is no cluster to keep together.
    public abstract boolean accepts(int codePoint);

    /// The type named in markup — `code-input type="alnum"`.
    ///
    /// @return the named type, or null if there is no such name — which the
    ///         caller logs, exactly as `text-input` does for an unknown `filter=`
    public static @Nullable CodeType named(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return DIGITS;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "digits", "digit", "numeric" -> DIGITS;
            case "alnum", "alphanumeric" -> ALNUM;
            default -> null;
        };
    }
}
