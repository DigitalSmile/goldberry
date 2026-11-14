package dev.goldberry.text.mark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.mark.MarkKind.Behaviour;
import dev.goldberry.text.mark.MarkKind.Custom;
import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;
import dev.goldberry.text.mark.MarkKind.Style;

/// What each kind is called, how it behaves, and which kinds may share a char.
class MarkKindTest {

    private static final Link HOME = new Link(URI.create("https://goldberry.dev"));
    private static final Link ELSEWHERE = new Link(URI.create("https://example.org"));
    private static final Mention ANN = new Mention("user:1");

    @Test
    @DisplayName("each kind is styled by a class of its own name")
    void classNames() {
        assertEquals("bold", Style.BOLD.className());
        assertEquals("spoiler", Style.SPOILER.className());
        assertEquals("link", HOME.className());
        assertEquals("mention", ANN.className());
        assertEquals("hashtag", new Custom("hashtag", "x", Behaviour.REFERENCE).className());
    }

    @Test
    @DisplayName("formatting grows at its end, a link does not, a mention is an atom")
    void behaviours() {
        assertTrue(Style.ITALIC.behaviour().growsAtEnd());
        assertFalse(HOME.behaviour().growsAtEnd());
        assertTrue(ANN.isAtomic());
        assertFalse(HOME.isAtomic());
    }

    @Test
    @DisplayName("formatting overlaps formatting")
    void stylesOverlap() {
        assertFalse(MarkKind.conflict(Style.BOLD, Style.ITALIC));
        assertFalse(MarkKind.conflict(Style.BOLD, HOME));
        assertFalse(MarkKind.conflict(Style.BOLD, Style.BOLD));
    }

    @Test
    @DisplayName("code overlaps nothing but a spoiler")
    void codeIsAlone() {
        assertTrue(MarkKind.conflict(Style.CODE, Style.BOLD));
        assertTrue(MarkKind.conflict(Style.ITALIC, Style.CODE));
        assertTrue(MarkKind.conflict(HOME, Style.CODE));
        assertFalse(MarkKind.conflict(Style.CODE, Style.SPOILER));
        assertFalse(MarkKind.conflict(Style.SPOILER, Style.CODE));
    }

    @Test
    @DisplayName("a word goes to one place")
    void twoLinksConflict() {
        assertTrue(MarkKind.conflict(HOME, ELSEWHERE));
        assertFalse(MarkKind.conflict(HOME, HOME));
        var tag = new Custom("hashtag", "a", Behaviour.REFERENCE);
        assertTrue(MarkKind.conflict(tag, new Custom("hashtag", "b", Behaviour.REFERENCE)));
        assertFalse(MarkKind.conflict(tag, new Custom("command", "b", Behaviour.REFERENCE)));
    }

    @Test
    @DisplayName("nothing overlaps an atom, not even the same atom")
    void atomsAreAlone() {
        assertTrue(MarkKind.conflict(ANN, Style.BOLD));
        assertTrue(MarkKind.conflict(HOME, ANN));
        assertTrue(MarkKind.conflict(ANN, ANN));
    }

    @Test
    @DisplayName("an exclusive custom kind overlaps nothing")
    void exclusiveCustom() {
        var emoji = new Custom("emoji", "smile", new Behaviour(false, false, true));
        assertTrue(MarkKind.conflict(emoji, Style.BOLD));
        assertTrue(MarkKind.conflict(Style.SPOILER, emoji));
    }

    @Test
    @DisplayName("a custom kind's name is a class, and not a built-in one")
    void customNames() {
        assertThrows(IllegalArgumentException.class, () -> new Custom("Hash Tag", "", Behaviour.REFERENCE));
        assertThrows(IllegalArgumentException.class, () -> new Custom("bold", "", Behaviour.STYLE));
        assertThrows(IllegalArgumentException.class, () -> new Behaviour(false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new Mention(" "));
    }
}
