package dev.goldberry.text.itemize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Splits a string into the runs each face shapes, by Unicode's emoji rules as
/// the JDK carries them.
///
/// ```java
/// for (TextRun run : Itemizer.runs("Rolling to eu-2 🎉 at 14:00")) {
///     Font face = run.slot() == Slot.EMOJI ? font.emoji() : font;
///     // shape text.subSequence(run.start(), run.end()) in `face`
/// }
/// ```
///
/// A paragraph shaped entirely in one face draws an emoji as `.notdef`, a box.
/// The itemizer stops that: the emoji is its own run, shaped in the emoji
/// face, and the words on either side are unchanged.
///
/// The rules are UTS #51's, through `java.lang.Character`. The JDK carries
/// [Character#isEmoji(int)] and its four siblings, so the properties are
/// Unicode's own and move when the JDK's Unicode version moves, which is why
/// this is thirty lines rather than a table the toolkit has to re-fetch every
/// year. A cluster goes to [Slot#EMOJI] when it is a character Unicode draws as
/// a picture by default (`Emoji_Presentation`, which 🎉 is and ❤ is not); any
/// emoji character followed by U+FE0F, the variation selector that asks for the
/// picture (`❤️` is `❤` plus that); or a keycap, `#`, `*` or a digit, then
/// U+FE0F, then U+20E3. The cluster then extends over skin-tone modifiers,
/// further variation selectors, tag sequences (the subdivision flags), and
/// U+200D joiners with the emoji after them, so `👨‍👩‍👧` is one run and the face
/// can ligate it into one family rather than three people. U+FE0E, the other
/// variation selector, is honoured too: it asks for the text form, so `❤︎`
/// stays in the prose face.
///
/// The itemizer does not decide whether the emoji face exists, whether it has
/// the character, or what happens if it does not. It reads the text; the fonts
/// are the font book's.
///
/// Read more: [Emoji](https://goldberry.dev/docs/guide/text.html#emoji).
public final class Itemizer {

    /// U+200D, which joins two emoji into one picture.
    private static final int ZWJ = 0x200D;

    /// U+FE0E: draw the character as text.
    private static final int TEXT_SELECTOR = 0xFE0E;

    /// U+FE0F: draw it as a picture.
    private static final int EMOJI_SELECTOR = 0xFE0F;

    /// U+20E3, the box drawn round a keycap's digit.
    private static final int KEYCAP = 0x20E3;

    /// The tag characters, U+E0020 to U+E007F, which spell out a subdivision's
    /// code inside a flag.
    private static final int TAG_FIRST = 0xE0020;

    private static final int TAG_LAST = 0xE007F;

    private Itemizer() {}

    /// The runs `text` splits into, in order, covering it exactly.
    ///
    /// Adjacent runs never share a slot: two emoji side by side are one run, so
    /// the face sees them together and can ligate a flag or a family. An empty
    /// string produces an empty list rather than an empty run.
    public static List<TextRun> runs(CharSequence text) {
        Objects.requireNonNull(text, "text");
        var length = text.length();
        if (length == 0) {
            return List.of();
        }

        var runs = new ArrayList<TextRun>();
        var start = 0;
        var slot = Slot.TEXT;
        var at = 0;

        while (at < length) {
            var cluster = emojiClusterEnd(text, at);
            var here = cluster > at ? Slot.EMOJI : Slot.TEXT;
            if (here != slot && at > start) {
                runs.add(new TextRun(start, at, slot));
                start = at;
            }
            slot = here;
            at = cluster > at ? cluster : at + Character.charCount(Character.codePointAt(text, at));
        }
        runs.add(new TextRun(start, length, slot));
        return List.copyOf(runs);
    }

    /// One past the end of the emoji cluster starting at `at`, or `at` itself
    /// when nothing there is drawn as a picture.
    private static int emojiClusterEnd(CharSequence text, int at) {
        var length = text.length();
        var first = Character.codePointAt(text, at);
        var end = at + Character.charCount(first);

        if (!Character.isEmoji(first)) {
            return at;
        }

        // A keycap is three code points and the first two of them are ordinary
        // text on their own, so it is recognised whole or not at all.
        if (isKeycapBase(first) && end < length && codePointAt(text, end) == EMOJI_SELECTOR) {
            var afterSelector = end + 1;
            if (afterSelector < length && codePointAt(text, afterSelector) == KEYCAP) {
                return extend(text, afterSelector + 1);
            }
        }

        var presented = Character.isEmojiPresentation(first);
        if (end < length) {
            var next = codePointAt(text, end);
            if (next == EMOJI_SELECTOR) {
                presented = true;
                end++;
            } else if (next == TEXT_SELECTOR) {
                // The author asked for the glyph. Consuming the selector here
                // would leave it to start a run of its own.
                return at;
            } else if (Character.isEmojiModifierBase(first) && Character.isEmojiModifier(next)) {
                // UTS #51: an `emoji_modifier_sequence` is a base followed by a
                // skin tone, and the sequence has emoji presentation however
                // the base is presented on its own. `☝` (U+261D) is
                // `Emoji_Presentation=No`, so without this the base would stay
                // in the text run and the swatch after it would start a picture
                // run of its own: a bare skin tone drawn beside a glyph.
                presented = true;
            }
        }
        return presented ? extend(text, end) : at;
    }

    /// Swallows everything that belongs to the cluster that has already started:
    /// modifiers, selectors, tags, and joined emoji.
    private static int extend(CharSequence text, int from) {
        var length = text.length();
        var at = from;
        while (at < length) {
            var cp = codePointAt(text, at);
            if (Character.isEmojiModifier(cp) || cp == EMOJI_SELECTOR) {
                at += Character.charCount(cp);
            } else if (cp >= TAG_FIRST && cp <= TAG_LAST) {
                at += Character.charCount(cp);
            } else if (cp == ZWJ && joinsAnEmoji(text, at + 1)) {
                // The joiner *and* what it joins, in one step: a joiner with
                // nothing usable after it is a dangling character that belongs to
                // the text run, not to this picture.
                at++;
                at += Character.charCount(codePointAt(text, at));
            } else {
                break;
            }
        }
        return at;
    }

    private static boolean joinsAnEmoji(CharSequence text, int at) {
        return at < text.length() && Character.isEmoji(codePointAt(text, at));
    }

    /// `#`, `*` and the ten digits: the characters a keycap is drawn around.
    private static boolean isKeycapBase(int codePoint) {
        return codePoint == '#' || codePoint == '*' || (codePoint >= '0' && codePoint <= '9');
    }

    private static int codePointAt(CharSequence text, int at) {
        return Character.codePointAt(text, at);
    }
}
