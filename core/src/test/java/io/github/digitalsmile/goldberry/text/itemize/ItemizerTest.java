package io.github.digitalsmile.goldberry.text.itemize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// Which face draws what — [ADR-0393]'s half that needs no font at all.
///
/// Every case here is a string somebody actually types. The rules are Unicode's
/// and the JDK carries them; what is tested is that they are applied to the
/// *sequence* correctly, which is where a naive per-code-point split goes wrong.
class ItemizerTest {

    /// The strings that must come out as **one** run over their whole length,
    /// and the face each one lands in — the shape a per-code-point split gets
    /// wrong. The first column says what the case is for, so a failure names it.
    static Stream<Arguments> wholeStrings() {
        return Stream.of(
                arguments("prose is one run, and the face it names is the text face", "hello there", Slot.TEXT),
                // A flag is two regional indicators that the font draws as one
                // picture, and it can only do that if handed both at once.
                arguments(
                        "two emoji side by side are one run, so the face can ligate them",
                        "\uD83C\uDDEC\uD83C\uDDE7",
                        Slot.EMOJI),
                arguments(
                        "a joined family is one run and not three people",
                        "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
                        Slot.EMOJI),
                arguments("a skin tone belongs to the emoji before it", "\uD83D\uDC4B\uD83C\uDFFD", Slot.EMOJI),
                // A bare heart is Emoji=Yes and Emoji_Presentation=No: Unicode
                // draws it as a glyph unless asked otherwise, and U+FE0F asks.
                arguments("U+FE0F asks for the picture, and gets it", "\u2764\uFE0F", Slot.EMOJI),
                arguments("U+FE0E asks for the glyph, and the emoji face never sees it", "\u2764\uFE0E", Slot.TEXT),
                arguments(
                        "a bare heart stays in the prose face, because Unicode says it is a glyph",
                        "\u2764",
                        Slot.TEXT),
                arguments("a keycap is three code points and one picture", "1\uFE0F\u20E3", Slot.EMOJI),
                arguments("but a digit on its own is a digit", "14:00 is", Slot.TEXT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wholeStrings")
    @DisplayName("a string that is one run comes out as one run, in the face Unicode names")
    void oneRun(String what, String text, Slot slot) {
        assertEquals(List.of(new TextRun(0, text.length(), slot)), Itemizer.runs(text), what);
    }

    @Test
    @DisplayName("empty text is no runs at all, rather than one empty one")
    void emptyIsNothing() {
        assertTrue(Itemizer.runs("").isEmpty());
    }

    @Test
    @DisplayName("an emoji inside a sentence is its own run, and the words around it are not disturbed")
    void anEmojiSplitsASentence() {
        // The string from `docs/gaps.md` G49, which is how the gap was reported.
        var text = "Rolling to eu-2 🎉 at 14:00";
        var runs = Itemizer.runs(text);

        assertEquals(3, runs.size());
        assertEquals(Slot.TEXT, runs.get(0).slot());
        assertEquals(Slot.EMOJI, runs.get(1).slot());
        assertEquals(Slot.TEXT, runs.get(2).slot());
        assertEquals("🎉", text.substring(runs.get(1).start(), runs.get(1).end()));
    }

    @Test
    @DisplayName("the runs cover the text exactly, with no gap and no overlap")
    void runsCoverTheText() {
        var text = "a 🎉 b ❤️ c 👨‍👩‍👧 d";
        var runs = Itemizer.runs(text);

        var at = 0;
        for (var run : runs) {
            assertEquals(at, run.start(), "a run starts where the last one ended");
            at = run.end();
        }
        assertEquals(text.length(), at, "and the last one ends at the end");
    }

    @Test
    @DisplayName("a hash before a word is a hashtag, not a keycap")
    void aHashIsNotSwallowed() {
        // `#` is Emoji_Component, so an itemizer that extended a cluster over
        // every component would eat the hash of `🎉#ship` and route it to a face
        // that would draw it as a keycap with nothing in it.
        var runs = Itemizer.runs("🎉#ship");

        assertEquals(2, runs.size());
        assertEquals(Slot.EMOJI, runs.get(0).slot());
        assertEquals("#ship", "🎉#ship".substring(runs.get(1).start()));
    }

    @Test
    @DisplayName("a joiner with nothing to join belongs to the text")
    void aDanglingJoinerDoesNotExtend() {
        var text = "🎉‍";
        var runs = Itemizer.runs(text);

        assertEquals(2, runs.size());
        assertEquals(Slot.EMOJI, runs.get(0).slot());
        assertEquals(Slot.TEXT, runs.get(1).slot(), "the joiner is left where it was typed");
    }

    @Test
    @DisplayName("a run cannot end before it starts")
    void aBackwardsRunIsRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> new TextRun(4, 2, Slot.TEXT));

        // The two offsets and not the sentence around them: what a caller needs
        // is which pair was transposed.
        assertTrue(refusal.getMessage().contains("4..2"), refusal.getMessage());
    }
}
