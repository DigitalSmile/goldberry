package dev.goldberry.text.mark;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static dev.goldberry.text.mark.MarkKind.Style.CODE;
import static dev.goldberry.text.mark.MarkKind.Style.ITALIC;
import static dev.goldberry.text.mark.MarkKind.Style.SPOILER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;

/// The value: normal form, what it answers, and how its marks move on an edit.
class MarkedTextTest {

    private static final Link HOME = new Link(URI.create("https://goldberry.dev"));
    private static final Link ELSEWHERE = new Link(URI.create("https://example.org"));
    private static final Mention ANN = new Mention("user:1");

    /// "Hi @Ann there": the mention is chars 3 to 7.
    private static MarkedText withAnn() {
        return MarkedText.of("Hi @Ann there").mark(3, 7, ANN);
    }

    @Nested
    @DisplayName("normal form")
    class Normal {

        @Test
        @DisplayName("marks are sorted, whatever order they came in")
        void sorted() {
            var text = new MarkedText("abcdef", List.of(new Mark(4, 6, BOLD), new Mark(0, 2, ITALIC)));
            assertEquals(List.of(new Mark(0, 2, ITALIC), new Mark(4, 6, BOLD)), text.marks());
        }

        @Test
        @DisplayName("two marks of one kind that touch are one")
        void touchingMerge() {
            var text = MarkedText.of("abcdef").mark(0, 2, BOLD).mark(2, 4, BOLD);
            assertEquals(List.of(new Mark(0, 4, BOLD)), text.marks());
        }

        @Test
        @DisplayName("overlapping marks of one kind are one")
        void overlappingMerge() {
            var text = MarkedText.of("abcdef").mark(0, 3, BOLD).mark(2, 6, BOLD).mark(1, 2, BOLD);
            assertEquals(List.of(new Mark(0, 6, BOLD)), text.marks());
        }

        @Test
        @DisplayName("the same formatting reached two ways is the same value")
        void equalValues() {
            var one = MarkedText.of("abcdef").mark(0, 6, BOLD);
            var two = MarkedText.of("abcdef").mark(3, 6, BOLD).mark(0, 3, BOLD);
            assertEquals(one, two);
        }

        @Test
        @DisplayName("two mentions side by side stay two")
        void atomsDoNotMerge() {
            var text = MarkedText.of("AnnAnn").mark(0, 3, ANN).mark(3, 6, ANN);
            assertEquals(2, text.marks().size());
        }

        @Test
        @DisplayName("code cuts the formatting it lands on; a later mark wins")
        void codeCutsBold() {
            var text = MarkedText.of("abcdef").mark(0, 6, BOLD).mark(2, 4, CODE);
            assertEquals(List.of(new Mark(0, 2, BOLD), new Mark(2, 4, CODE), new Mark(4, 6, BOLD)), text.marks());

            var back = text.mark(0, 6, BOLD);
            assertEquals(List.of(new Mark(0, 6, BOLD)), back.marks());
        }

        @Test
        @DisplayName("a spoiler sits over code")
        void spoilerOverCode() {
            var text = MarkedText.of("abcdef").mark(0, 6, CODE).mark(0, 6, SPOILER);
            assertEquals(2, text.marks().size());
        }

        @Test
        @DisplayName("a new link replaces the old one over its range")
        void linkReplacesLink() {
            var text = MarkedText.of("abcdef").mark(0, 6, HOME).mark(0, 3, ELSEWHERE);
            assertEquals(List.of(new Mark(0, 3, ELSEWHERE), new Mark(3, 6, HOME)), text.marks());
        }

        @Test
        @DisplayName("formatting goes around an atom, never over it")
        void formattingSkipsAtoms() {
            var text = withAnn().mark(0, 13, BOLD);
            assertEquals(List.of(new Mark(0, 3, BOLD), new Mark(3, 7, ANN), new Mark(7, 13, BOLD)), text.marks());
        }

        @Test
        @DisplayName("an atom cuts the formatting under it, and a half-covered atom goes")
        void atomCutsFormatting() {
            var text = MarkedText.of("abcdef").mark(0, 6, BOLD).mark(2, 4, ANN);
            assertEquals(List.of(new Mark(0, 2, BOLD), new Mark(2, 4, ANN), new Mark(4, 6, BOLD)), text.marks());

            var other = new Mention("user:2");
            var replaced = text.mark(3, 5, other);
            assertEquals(List.of(new Mark(0, 2, BOLD), new Mark(3, 5, other), new Mark(5, 6, BOLD)), replaced.marks());
        }

        @Test
        @DisplayName("a mark reaching past the text is refused")
        void outside() {
            assertThrows(IllegalArgumentException.class, () -> new MarkedText("ab", List.of(new Mark(0, 3, BOLD))));
            assertThrows(IllegalArgumentException.class, () -> new Mark(2, 2, BOLD));
        }
    }

    @Nested
    @DisplayName("asking")
    class Asking {

        @Test
        @DisplayName("the kinds over a char")
        void kindsAt() {
            var text = MarkedText.of("abcdef").mark(0, 4, BOLD).mark(2, 6, ITALIC);
            assertEquals(Set.of(BOLD), text.kindsAt(1));
            assertEquals(Set.of(BOLD, ITALIC), text.kindsAt(3));
            assertEquals(Set.of(), text.kindsAt(6));
        }

        @Test
        @DisplayName("typing at the end of bold is bold; at the end of a link it is not")
        void inherited() {
            var text = MarkedText.of("bold link").mark(0, 4, BOLD).mark(5, 9, HOME);
            assertEquals(Set.of(BOLD), text.kindsInheritedAt(4));
            assertEquals(Set.of(), text.kindsInheritedAt(9));
            assertEquals(Set.of(HOME), text.kindsInheritedAt(7));
        }

        @Test
        @DisplayName("typing at the start of a mark does not take it")
        void notAtTheStart() {
            var text = MarkedText.of("abcdef").mark(2, 4, BOLD);
            assertEquals(Set.of(), text.kindsInheritedAt(2));
            assertEquals(Set.of(), text.kindsInheritedAt(0));
        }

        @Test
        @DisplayName("nothing typed takes an atom")
        void noAtomInherited() {
            assertEquals(Set.of(), withAnn().kindsInheritedAt(5));
            assertEquals(Set.of(), withAnn().kindsInheritedAt(7));
        }

        @Test
        @DisplayName("a selection holding a mention is bold when all around it is")
        void hasSkipsAtoms() {
            var text = withAnn().mark(0, 13, BOLD);
            assertTrue(text.has(0, 13, BOLD));
            assertEquals(Set.of(BOLD), text.kindsOver(0, 13));
            assertFalse(text.has(3, 7, BOLD), "all atom is nothing to be bold");
            assertFalse(text.has(2, 2, BOLD));
        }

        @Test
        @DisplayName("half bold is not bold")
        void partlyCovered() {
            var text = MarkedText.of("abcdef").mark(0, 3, BOLD);
            assertFalse(text.has(0, 6, BOLD));
            assertEquals(Set.of(), text.kindsOver(0, 6));
        }

        @Test
        @DisplayName("a range touching an atom grows to all of it")
        void atomicRange() {
            assertEquals(new Mark.Span(2, 7), withAnn().atomicRange(2, 5));
            assertEquals(new Mark.Span(5, 5), withAnn().atomicRange(5, 5));
            assertEquals(new Mark.Span(7, 9), withAnn().atomicRange(7, 9));
        }

        @Test
        @DisplayName("a line's marks are cut to it and counted from its start")
        void marksIn() {
            var text = MarkedText.of("one\ntwo").mark(2, 6, BOLD);
            assertEquals(List.of(new Mark(0, 2, BOLD)), text.marksIn(4, 7));
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("rule 1: text removed from a mark shrinks it, and an emptied mark goes")
        void removalShrinks() {
            var text = MarkedText.of("abcdef").mark(1, 5, BOLD).mark(2, 3, ITALIC);
            var next = text.replace(2, 4, MarkedText.EMPTY, Set.of());
            assertEquals("abef", next.text());
            assertEquals(List.of(new Mark(1, 3, BOLD)), next.marks());
        }

        @Test
        @DisplayName("rule 2: a mark spanning the replaced range splits around a paste")
        void pasteSplits() {
            var text = MarkedText.of("abcdef").mark(0, 6, BOLD);
            var next = text.replace(3, 3, MarkedText.of("XY"), Set.of());
            assertEquals(List.of(new Mark(0, 3, BOLD), new Mark(5, 8, BOLD)), next.marks());
        }

        @Test
        @DisplayName("rule 3: typed text inherits and the mark stays one")
        void typingInherits() {
            var text = MarkedText.of("abcdef").mark(0, 6, BOLD);
            var next = text.replace(3, 3, MarkedText.of("XY"), text.kindsInheritedAt(3));
            assertEquals(List.of(new Mark(0, 8, BOLD)), next.marks());
        }

        @Test
        @DisplayName("rule 3: a paste's own marks win")
        void pastedMarksWin() {
            var text = MarkedText.of("abcdef").mark(0, 6, BOLD);
            var pasted = MarkedText.of("XY").mark(0, 2, CODE);
            var next = text.replace(3, 3, pasted, Set.of(BOLD));
            assertEquals(List.of(new Mark(0, 3, BOLD), new Mark(3, 5, CODE), new Mark(5, 8, BOLD)), next.marks());
        }

        @Test
        @DisplayName("marks after the edit move by its length")
        void shifts() {
            var text = MarkedText.of("abcdef").mark(4, 6, ITALIC);
            var next = text.replace(0, 1, MarkedText.of("XYZ"), Set.of());
            assertEquals(List.of(new Mark(6, 8, ITALIC)), next.marks());
        }

        @Test
        @DisplayName("rule 4: deleting part of an atom deletes all of it")
        void atomGoesWhole() {
            var next = withAnn().replace(6, 7, MarkedText.EMPTY, Set.of());
            assertEquals("Hi  there", next.text());
            assertTrue(next.isPlain());
        }

        @Test
        @DisplayName("rule 4: text inserted inside an atom leaves its text and takes its mark")
        void insertInsideAtom() {
            var next = withAnn().replace(5, 5, MarkedText.of("x"), Set.of());
            assertEquals("Hi @Axnn there", next.text());
            assertTrue(next.isPlain());
        }

        @Test
        @DisplayName("an atom beside an insertion is kept and moved")
        void atomBeside() {
            var next = withAnn().replace(3, 3, MarkedText.of("> "), Set.of());
            assertEquals(List.of(new Mark(5, 9, ANN)), next.marks());
            var after = withAnn().replace(7, 7, MarkedText.of("!"), withAnn().kindsInheritedAt(7));
            assertEquals(List.of(new Mark(3, 7, ANN)), after.marks());
        }

        @Test
        @DisplayName("a slice keeps whole atoms and drops halves")
        void slice() {
            var text = withAnn().mark(8, 13, BOLD);
            var whole = text.slice(3, 13);
            assertEquals("@Ann there", whole.text());
            assertEquals(List.of(new Mark(0, 4, ANN), new Mark(5, 10, BOLD)), whole.marks());

            var half = text.slice(5, 10);
            assertEquals("nn th", half.text());
            assertEquals(List.of(new Mark(3, 5, BOLD)), half.marks());
        }

        @Test
        @DisplayName("unmark cuts a kind out of a range, and takes an atom away whole")
        void unmark() {
            var text = MarkedText.of("abcdef").mark(0, 6, BOLD);
            assertEquals(
                    List.of(new Mark(0, 2, BOLD), new Mark(4, 6, BOLD)),
                    text.unmark(2, 4, BOLD).marks());

            var plain = withAnn().unmark(4, 5, ANN);
            assertEquals("Hi @Ann there", plain.text());
            assertTrue(plain.isPlain());
        }

        @Test
        @DisplayName("clearing formatting leaves the mention")
        void clearFormatting() {
            var text = withAnn().mark(0, 13, BOLD).mark(8, 13, HOME);
            assertEquals(
                    List.of(new Mark(3, 7, ANN)), text.clearFormatting(0, 13).marks());
        }

        @Test
        @DisplayName("a range outside the text is refused")
        void outOfRange() {
            var text = MarkedText.of("ab");
            assertThrows(IndexOutOfBoundsException.class, () -> text.replace(1, 3, MarkedText.EMPTY, Set.of()));
            assertThrows(IndexOutOfBoundsException.class, () -> text.mark(2, 1, BOLD));
        }
    }
}
