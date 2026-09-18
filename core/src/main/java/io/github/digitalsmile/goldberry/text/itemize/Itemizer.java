package io.github.digitalsmile.goldberry.text.itemize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Splits a string into the runs each face shapes — Unicode's emoji rules, read
/// out of the JDK.
///
/// ## What it is for
///
/// A paragraph used to be one string handed to one face, so `Rolling to eu-2 🎉
/// at 14:00` was shaped entirely in Inter and the party popper came back as
/// `.notdef` — the box `docs/gaps.md` G49 reported. This is the split that stops
/// that: the emoji is its own run, shaped in the emoji face, and the words on
/// either side are unchanged ([ADR-0393]).
///
/// ## The rules, and where they come from
///
/// UTS #51's, through `java.lang.Character`. The JDK has carried
/// [Character#isEmoji(int)] and its four siblings since 21, so the properties
/// are Unicode's own and they move when the JDK's Unicode version moves — which
/// is the whole reason this is thirty lines rather than a table the toolkit has
/// to re-fetch every year.
///
/// A cluster goes to [Slot#EMOJI] when it is:
///
/// - a character Unicode draws as a picture by default — `Emoji_Presentation`,
///   which is 🎉 and ❤ is *not*;
/// - any emoji character followed by **U+FE0F**, the variation selector that
///   asks for the picture — `❤️` is `❤` plus that, and it is why the two
///   hearts in this sentence are different strings;
/// - a keycap: `#`, `*` or a digit, then U+FE0F, then U+20E3.
///
/// and it then extends over skin-tone modifiers, further variation selectors,
/// tag sequences (the subdivision flags), and **U+200D joiners** with the emoji
/// after them — so `👨‍👩‍👧` is one run and the face can ligate it into one
/// family rather than three people.
///
/// **U+FE0E, the other variation selector, is honoured too.** It asks for the
/// text form, so `❤︎` stays in the prose face. A toolkit that ignored it would
/// draw a picture where the author asked for a glyph.
///
/// ## What it does not decide
///
/// Whether the emoji face exists, whether it has the character, or what happens
/// if it does not. This reads the text; the fonts are somebody else's.
public final class Itemizer {

    /// U+200D, which joins two emoji into one picture.
    private static final int ZWJ = 0x200D;

    /// U+FE0E — draw the character as text.
    private static final int TEXT_SELECTOR = 0xFE0E;

    /// U+FE0F — draw it as a picture.
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
    /// Adjacent runs never share a slot — two emoji side by side are one run, so
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
                // skin tone, and the *sequence* has emoji presentation however
                // the base is presented on its own. `☝` (U+261D) is
                // `Emoji_Presentation=No`, so without this the base stayed in
                // the text run and the swatch after it started a picture run of
                // its own -- a bare skin tone drawn beside a glyph nobody asked
                // to separate (the 2026-09-18 review, C13).
                presented = true;
            }
        }
        return presented ? extend(text, end) : at;
    }

    /// Swallows everything that belongs to the cluster that has already started —
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

    /// `#`, `*` and the ten digits — the characters a keycap is drawn around.
    private static boolean isKeycapBase(int codePoint) {
        return codePoint == '#' || codePoint == '*' || (codePoint >= '0' && codePoint <= '9');
    }

    private static int codePointAt(CharSequence text, int at) {
        return Character.codePointAt(text, at);
    }
}
