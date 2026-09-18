package io.github.digitalsmile.goldberry.text.itemize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Which face draws what — [ADR-0393]'s half that needs no font at all.
///
/// Every case here is a string somebody actually types. The rules are Unicode's
/// and the JDK carries them; what is tested is that they are applied to the
/// *sequence* correctly, which is where a naive per-code-point split goes wrong.
class ItemizerTest {

    @Test
    @DisplayName("prose is one run, and the face it names is the text face")
    void proseIsOneRun() {
        assertEquals(List.of(new TextRun(0, 11, Slot.TEXT)), Itemizer.runs("hello there"));
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
    @DisplayName("two emoji side by side are one run, so the face can ligate them")
    void adjacentEmojiShareARun() {
        // A flag is two regional indicators that the font draws as one picture,
        // and it can only do that if it is handed both at once.
        var runs = Itemizer.runs("🇬🇧");

        assertEquals(1, runs.size());
        assertEquals(Slot.EMOJI, runs.getFirst().slot());
    }

    @Test
    @DisplayName("a joined family is one run and not three people")
    void zeroWidthJoinerHoldsAClusterTogether() {
        var family = "👨‍👩‍👧";
        var runs = Itemizer.runs(family);

        assertEquals(List.of(new TextRun(0, family.length(), Slot.EMOJI)), runs);
    }

    @Test
    @DisplayName("a skin tone belongs to the emoji before it")
    void modifiersJoinTheirBase() {
        var wave = "👋🏽";
        assertEquals(List.of(new TextRun(0, wave.length(), Slot.EMOJI)), Itemizer.runs(wave));
    }

    @Test
    @DisplayName("a skin tone belongs to its base even when the base is drawn as a glyph on its own")
    void modifiersJoinAGlyphBase() {
        // `☝` (U+261D) is Emoji=Yes and Emoji_Presentation=No, so on its own it
        // is a glyph — but UTS #51 says an `emoji_modifier_sequence` has emoji
        // presentation whatever its base has. Without that rule the base stayed
        // in the text run and the swatch after it started a picture run of its
        // own: a bare skin tone drawn beside a pointing finger (the 2026-09-18
        // review, C13).
        var pointing = "☝🏻";

        assertEquals(List.of(new TextRun(0, pointing.length(), Slot.EMOJI)), Itemizer.runs(pointing));
    }

    @Test
    @DisplayName("a skin tone after something that cannot take one is still its own run")
    void aModifierWithNoBaseStandsAlone() {
        // `❤` is Emoji_Modifier_Base=No, so this is not a sequence — the two are
        // two clusters, and the heart keeps its text presentation.
        var runs = Itemizer.runs("❤🏻");

        assertEquals(2, runs.size(), () -> "expected a glyph and a swatch, got " + runs);
        assertEquals(Slot.TEXT, runs.getFirst().slot());
        assertEquals(Slot.EMOJI, runs.getLast().slot());
    }

    @Test
    @DisplayName("U+FE0F asks for the picture, and gets it")
    void theEmojiSelectorRoutes() {
        // A bare heart is Emoji=Yes and Emoji_Presentation=No: Unicode draws it
        // as a glyph unless asked otherwise, and this is the asking.
        var runs = Itemizer.runs("❤️");

        assertEquals(1, runs.size());
        assertEquals(Slot.EMOJI, runs.getFirst().slot());
    }

    @Test
    @DisplayName("U+FE0E asks for the glyph, and the emoji face never sees it")
    void theTextSelectorDoesNotRoute() {
        var runs = Itemizer.runs("❤︎");

        assertEquals(1, runs.size());
        assertEquals(Slot.TEXT, runs.getFirst().slot(), "the author asked for the text form");
    }

    @Test
    @DisplayName("a bare heart stays in the prose face, because Unicode says it is a glyph")
    void defaultTextPresentationStays() {
        assertEquals(List.of(new TextRun(0, 1, Slot.TEXT)), Itemizer.runs("❤"));
    }

    @Test
    @DisplayName("a keycap is three code points and one picture")
    void keycapsAreWhole() {
        var keycap = "1️⃣";
        assertEquals(List.of(new TextRun(0, keycap.length(), Slot.EMOJI)), Itemizer.runs(keycap));
    }

    @Test
    @DisplayName("but a digit on its own is a digit")
    void aBareDigitIsText() {
        assertEquals(List.of(new TextRun(0, 8, Slot.TEXT)), Itemizer.runs("14:00 is"));
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
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class, () -> new TextRun(4, 2, Slot.TEXT))
                .getMessage()
                .contains("4..2"));
    }

    @Test
    @DisplayName("a run knows how long it is")
    void runLength() {
        assertEquals(3, new TextRun(2, 5, Slot.EMOJI).length());
    }
}
