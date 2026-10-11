package dev.goldberry.text.flow;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// How the case of a text is changed for drawing: CSS's `text-transform`.
///
/// ```css
/// overline { text-transform: uppercase; letter-spacing: 0.08em }
/// ```
///
/// Applied to what is painted, never to the text a widget holds: a field
/// whose value is "hello" drawn under `uppercase` still holds "hello", copies
/// "hello" and is read out as "hello".
///
/// **One character for one character.** Every code point is mapped to one code
/// point of the same length, so the painted string is as long as the held one
/// and every offset into it, the caret's, a selection's, a click's, means the
/// same character in both. That is why an editable field can take a transform
/// at all. It costs the two mappings that change a string's length: `ß` stays
/// `ß` under `uppercase` where CSS would draw `SS`, and a ligature character
/// such as `ﬁ` is not split. Casing is the root locale's, so a Turkish dotted
/// `i` is cased as in English.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#typography).
public enum TextTransform {

    /// As written.
    NONE,

    /// Every letter upper case.
    UPPERCASE,

    /// Every letter lower case.
    LOWERCASE,

    /// The first letter of every word in title case, and the rest as written.
    CAPITALIZE;

    /// The keyword, or null when `name` is not one of the four.
    public static @Nullable TextTransform parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "none" -> NONE;
            case "uppercase" -> UPPERCASE;
            case "lowercase" -> LOWERCASE;
            case "capitalize" -> CAPITALIZE;
            default -> null;
        };
    }

    /// `text` as it is drawn: the same length, every offset the same
    /// character.
    public String apply(String text) {
        if (this == NONE || text.isEmpty()) {
            return text;
        }
        var out = new StringBuilder(text.length());
        var wordStart = true;
        for (var i = 0; i < text.length(); ) {
            var codePoint = text.codePointAt(i);
            var count = Character.charCount(codePoint);
            var mapped = map(codePoint, wordStart);
            // A mapping that would change the length keeps the character.
            out.appendCodePoint(Character.charCount(mapped) == count ? mapped : codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                wordStart = false;
            } else if (Character.isWhitespace(codePoint) || isWordPunctuation(codePoint)) {
                wordStart = true;
            }
            i += count;
        }
        return out.toString();
    }

    /// One code point as this transform draws it, `wordStart` saying whether it
    /// opens a word.
    private int map(int codePoint, boolean wordStart) {
        return switch (this) {
            case UPPERCASE -> Character.toUpperCase(codePoint);
            case LOWERCASE -> Character.toLowerCase(codePoint);
            case CAPITALIZE ->
                wordStart && Character.isLetter(codePoint) ? Character.toTitleCase(codePoint) : codePoint;
            case NONE -> codePoint;
        };
    }

    /// Punctuation that starts a word after it: a dash, a slash or an opening
    /// bracket does and an apostrophe does not, so `capitalize` draws
    /// "rock-n-roll" as "Rock-N-Roll" and "don't" as "Don't".
    private static boolean isWordPunctuation(int codePoint) {
        return codePoint == '-'
                || codePoint == '/'
                || codePoint == '('
                || codePoint == '"'
                || Character.getType(codePoint) == Character.DASH_PUNCTUATION
                || Character.getType(codePoint) == Character.START_PUNCTUATION;
    }
}
