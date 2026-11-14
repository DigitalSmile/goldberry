package dev.goldberry.text.mark;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static dev.goldberry.text.mark.MarkKind.Style.CODE;
import static dev.goldberry.text.mark.MarkKind.Style.ITALIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.edit.EditHistory;
import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;

/// The caret over marked text: typing, toggles, atoms and undo.
class MarkedEditTest {

    private static final Mention ANN = new Mention("user:1");

    /// "Hi @Ann there" with the caret at `caret`; the mention is chars 3 to 7.
    private static MarkedEdit withAnn(int caret) {
        return new MarkedEdit(MarkedText.of("Hi @Ann there").mark(3, 7, ANN), caret, caret, null);
    }

    private static MarkedEdit type(MarkedEdit from, String text) {
        var edit = from;
        for (var i = 0; i < text.length(); i++) {
            edit = edit.insert(text.substring(i, i + 1));
        }
        return edit;
    }

    @Nested
    @DisplayName("typing")
    class Typing {

        @Test
        @DisplayName("letters typed after a bold word are bold")
        void extendsBold() {
            var edit = MarkedEdit.of(MarkedText.of("Ship").mark(0, 4, BOLD));
            edit = type(edit, "ped");
            assertEquals(List.of(new Mark(0, 7, BOLD)), edit.value().marks());
        }

        @Test
        @DisplayName("letters typed after a link are not part of it")
        void linkDoesNotGrow() {
            var link = new Link(URI.create("https://goldberry.dev"));
            var edit = MarkedEdit.of(MarkedText.of("site").mark(0, 4, link));
            edit = type(edit, " now");
            assertEquals(List.of(new Mark(0, 4, link)), edit.value().marks());
        }

        @Test
        @DisplayName("a toggle with nothing selected styles what is typed next, then any move forgets it")
        void toggleForTyping() {
            var edit = MarkedEdit.of(MarkedText.of("Say "));
            edit = edit.toggle(BOLD);
            assertEquals(Set.of(BOLD), edit.activeKinds());
            edit = type(edit, "hi");
            assertEquals(List.of(new Mark(4, 6, BOLD)), edit.value().marks());

            edit = edit.toggle(BOLD);
            edit = type(edit, "!");
            assertEquals(List.of(new Mark(4, 6, BOLD)), edit.value().marks(), "toggled off, the ! is plain");

            var moved = edit.toggle(ITALIC).left(false);
            assertNull(moved.typing());
        }

        @Test
        @DisplayName("turning code on for typing turns bold off")
        void codeForTypingDropsBold() {
            var edit = MarkedEdit.of(MarkedText.of("x").mark(0, 1, BOLD));
            assertEquals(Set.of(BOLD), edit.activeKinds());
            assertEquals(Set.of(CODE), edit.toggle(CODE).activeKinds());
        }

        @Test
        @DisplayName("typing over a selection takes the look of its first char")
        void replacingSelection() {
            var edit = new MarkedEdit(MarkedText.of("abcdef").mark(1, 3, ITALIC), 1, 5, null);
            edit = edit.insert("X");
            assertEquals("aXf", edit.text());
            assertEquals(List.of(new Mark(1, 2, ITALIC)), edit.value().marks());
        }

        @Test
        @DisplayName("a paste keeps its own marks and takes nothing from around it")
        void paste() {
            var edit = new MarkedEdit(MarkedText.of("abcd").mark(0, 4, BOLD), 2, 2, null);
            edit = edit.insert(MarkedText.of("XY").mark(0, 1, ITALIC));
            assertEquals(
                    List.of(new Mark(0, 2, BOLD), new Mark(2, 3, ITALIC), new Mark(4, 6, BOLD)),
                    edit.value().marks());
            assertEquals(4, edit.caret());
        }
    }

    @Nested
    @DisplayName("toggling a selection")
    class Toggling {

        @Test
        @DisplayName("turns a kind on, and off again when all of it has it")
        void onAndOff() {
            var edit = new MarkedEdit(MarkedText.of("abcdef").mark(0, 2, BOLD), 0, 4, null);
            var on = edit.toggle(BOLD);
            assertEquals(List.of(new Mark(0, 4, BOLD)), on.value().marks());
            var off = on.toggle(BOLD);
            assertTrue(off.value().isPlain());
            assertEquals(0, off.anchor());
            assertEquals(4, off.caret());
        }

        @Test
        @DisplayName("over a mention, formats around it and still turns off")
        void aroundAtom() {
            var edit = withAnn(0).selectAll().toggle(BOLD);
            assertEquals(Set.of(BOLD), edit.activeKinds());
            assertTrue(edit.toggle(BOLD).value().kindsOver(0, 13).isEmpty());
        }

        @Test
        @DisplayName("an atom cannot be toggled")
        void atomRefused() {
            assertThrows(
                    IllegalArgumentException.class, () -> withAnn(0).selectAll().toggle(ANN));
        }

        @Test
        @DisplayName("clear formatting keeps the mention")
        void clear() {
            var edit = withAnn(0).selectAll().toggle(BOLD).clearFormatting();
            assertEquals(List.of(new Mark(3, 7, ANN)), edit.value().marks());
        }
    }

    @Nested
    @DisplayName("atoms")
    class Atoms {

        @Test
        @DisplayName("Right from before a mention lands after it")
        void rightStepsOver() {
            assertEquals(7, withAnn(3).right(false).caret());
        }

        @Test
        @DisplayName("Left from after a mention lands before it")
        void leftStepsOver() {
            assertEquals(3, withAnn(7).left(false).caret());
        }

        @Test
        @DisplayName("a click inside lands at the nearer end")
        void clickSnaps() {
            assertEquals(3, withAnn(0).caretTo(4, false).caret());
            assertEquals(7, withAnn(0).caretTo(6, false).caret());
        }

        @Test
        @DisplayName("a selection dragged into a mention covers it")
        void selectionGrows() {
            var forward = withAnn(0).caretTo(5, true);
            assertEquals(0, forward.anchor());
            assertEquals(7, forward.caret());
            var backward = withAnn(13).caretTo(5, true);
            assertEquals(3, backward.caret());
        }

        @Test
        @DisplayName("a double-click on a mention selects all of it")
        void wordAt() {
            var edit = withAnn(0).wordAt(5);
            assertEquals(3, edit.start());
            assertEquals(7, edit.end());
        }

        @Test
        @DisplayName("Backspace after a mention removes all of it; Delete before it too")
        void deletedWhole() {
            var back = withAnn(7).backspace();
            assertEquals("Hi  there", back.text());
            assertEquals(3, back.caret());
            assertTrue(back.value().isPlain());

            var forward = withAnn(3).delete();
            assertEquals("Hi  there", forward.text());
            assertEquals(3, forward.caret());
        }

        @Test
        @DisplayName("Ctrl+Backspace over a mention removes it whole")
        void wordDelete() {
            var edit = withAnn(7).deleteWordBefore();
            assertEquals("Hi  there", edit.text());
        }

        @Test
        @DisplayName("a query is replaced by a mention, and the caret goes after it")
        void replaceQuery() {
            var edit = MarkedEdit.of(MarkedText.of("Hi @an"));
            var bob = new Mention("user:2");
            edit = edit.replace(3, 6, MarkedText.of("@Bob").mark(0, 4, bob));
            assertEquals("Hi @Bob", edit.text());
            assertEquals(List.of(new Mark(3, 7, bob)), edit.value().marks());
            assertEquals(7, edit.caret());
            edit = type(edit, " ok");
            assertEquals(List.of(new Mark(3, 7, bob)), edit.value().marks());
        }

        @Test
        @DisplayName("a copy of a mention is a mention")
        void selected() {
            var copied = withAnn(0).selectAll().selected();
            assertEquals(List.of(new Mark(3, 7, ANN)), copied.marks());
        }
    }

    @Nested
    @DisplayName("undo")
    class Undo {

        @Test
        @DisplayName("brings the formatting back with the text")
        void keepsFormatting() {
            var history = new EditHistory<MarkedEdit>();
            var start = new MarkedEdit(MarkedText.of("Goldberry"), 0, 4, null);

            var bold = start.toggle(BOLD);
            history.record(start, bold, EditHistory.Kind.FORMATTING);
            var typed = bold.insert("Old");
            history.record(bold, typed, EditHistory.Kind.TYPING);

            assertEquals("Oldberry", typed.text());
            assertEquals(List.of(new Mark(0, 3, BOLD)), typed.value().marks());

            var undone = history.undo(typed);
            assertEquals(bold, undone);
            assertEquals(List.of(new Mark(0, 4, BOLD)), undone.value().marks());
            assertEquals(start, history.undo(undone));
        }
    }

    @Test
    @DisplayName("a new value keeps the caret where it fits, outside any atom")
    void withValue() {
        var edit = MarkedEdit.of(MarkedText.of("Hi @Ann there"));
        var next = edit.withValue(MarkedText.of("Hi @Ann").mark(3, 7, ANN));
        assertEquals(7, next.caret());
        var inside = new MarkedEdit(MarkedText.of("Hi @Ann"), 5, 5, null)
                .withValue(MarkedText.of("Hi @Ann").mark(3, 7, ANN));
        assertEquals(3, inside.caret());
        assertSame(edit, edit.withValue(MarkedText.of("Hi @Ann there")));
    }
}
