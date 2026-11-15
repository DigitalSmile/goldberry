package dev.goldberry.text.mark;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static dev.goldberry.text.mark.MarkKind.Style.CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.text.edit.EditHistory;
import dev.goldberry.text.mark.MarkKind.Mention;

/// Finding a mention query at the caret, and the atom that replaces it.
class MentionQueryTest {

    private static final Set<String> AT = Set.of("@");

    private static final Mention ANN = new Mention("user:1");

    private static Optional<MentionQuery> queryIn(String text, int caret) {
        return MentionQuery.at(new MarkedEdit(MarkedText.of(text), caret, caret, null), AT);
    }

    private static MarkedEdit type(MarkedEdit from, String text) {
        var edit = from;
        for (var i = 0; i < text.length(); i++) {
            edit = edit.insert(text.substring(i, i + 1));
        }
        return edit;
    }

    @Nested
    @DisplayName("opening")
    class Opening {

        @Test
        @DisplayName("a trigger at a word start opens a query of what follows it")
        void atWordStart() {
            assertEquals(Optional.of(new MentionQuery("@", "an", 4, 7)), queryIn("Ask @an", 7));
            assertEquals(Optional.of(new MentionQuery("@", "", 0, 1)), queryIn("@", 1), "the trigger alone");
        }

        @Test
        @DisplayName("a trigger inside a word opens none")
        void notInsideAWord() {
            assertTrue(queryIn("mail@example", 12).isEmpty());
            assertTrue(queryIn("@@ann", 5).isEmpty(), "the second trigger follows the first, not a word start");
        }

        @Test
        @DisplayName("after an opening bracket or quote is a word start")
        void afterAnOpener() {
            assertEquals("bo", queryIn("(@bo", 4).orElseThrow().query());
            assertEquals("bo", queryIn("«@bo", 4).orElseThrow().query());
            assertEquals("bo", queryIn("\"@bo", 4).orElseThrow().query());
        }

        @Test
        @DisplayName("the query follows typing, and a space closes it")
        void followsTyping() {
            var edit = type(MarkedEdit.of(MarkedText.of("Hi ")), "@a");
            assertEquals("a", MentionQuery.at(edit, AT).orElseThrow().query());
            edit = type(edit, "nn");
            assertEquals("ann", MentionQuery.at(edit, AT).orElseThrow().query());
            edit = type(edit, " ");
            assertTrue(MentionQuery.at(edit, AT).isEmpty());
        }

        @Test
        @DisplayName("punctuation in the word closes it")
        void punctuationCloses() {
            assertTrue(queryIn("@ann,", 5).isEmpty());
            assertEquals("ann_lee", queryIn("@ann_lee", 8).orElseThrow().query(), "an underscore is part of a name");
        }

        @Test
        @DisplayName("letters of any script are part of the query, combining marks included")
        void anyScript() {
            assertEquals("Zoë", queryIn("@Zoë", 4).orElseThrow().query());
            var decomposed = "@Zoë";
            assertEquals(
                    "Zoë",
                    queryIn(decomposed, decomposed.length()).orElseThrow().query());
            assertEquals("田中", queryIn("@田中", 3).orElseThrow().query());
        }

        @Test
        @DisplayName("a selection opens none")
        void notWithASelection() {
            var edit = new MarkedEdit(MarkedText.of("Ask @an"), 5, 7, null);
            assertTrue(MentionQuery.at(edit, AT).isEmpty());
        }

        @Test
        @DisplayName("a query longer than the limit is not one")
        void tooLong() {
            var name = "a".repeat(MentionQuery.MAX_LENGTH);
            assertTrue(queryIn("@" + name, name.length() + 1).isPresent());
            assertTrue(queryIn("@" + name + "a", name.length() + 2).isEmpty());
        }

        @Test
        @DisplayName("each of several triggers opens its own query")
        void severalTriggers() {
            var edit = MarkedEdit.of(MarkedText.of("tag #rel"));
            var query = MentionQuery.at(edit, Set.of("@", "#", "/")).orElseThrow();
            assertEquals("#", query.trigger());
            assertEquals("rel", query.query());
            assertTrue(MentionQuery.at(edit, AT).isEmpty(), "# is not a trigger here");
        }

        @Test
        @DisplayName("a trigger outside the basic plane counts as one character")
        void astralTrigger() {
            var trigger = "\uD83D\uDC64"; // a bust in silhouette, two chars
            var edit = MarkedEdit.of(MarkedText.of("hi " + trigger + "an"));
            var query = MentionQuery.at(edit, Set.of(trigger)).orElseThrow();
            assertEquals(3, query.start());
            assertEquals("an", query.query());
        }
    }

    @Nested
    @DisplayName("where it opens none")
    class Covered {

        @Test
        @DisplayName("inside code")
        void notInCode() {
            var edit = MarkedEdit.of(MarkedText.of("run @x").mark(4, 6, CODE));
            assertTrue(MentionQuery.at(edit, AT).isEmpty());
        }

        @Test
        @DisplayName("just after a mention, whose text starts with the trigger")
        void notAfterAnAtom() {
            var edit = MarkedEdit.of(MarkedText.of("Hi @Ann").mark(3, 7, ANN));
            assertTrue(MentionQuery.at(edit, AT).isEmpty());
        }

        @Test
        @DisplayName("bold does not stop one")
        void boldIsFine() {
            var edit = MarkedEdit.of(MarkedText.of("Hi @an").mark(3, 6, BOLD));
            assertEquals("an", MentionQuery.at(edit, AT).orElseThrow().query());
        }

        @Test
        @DisplayName("the word stops before an atom that follows it")
        void stopsBeforeAnAtom() {
            var value = MarkedText.of("@bo@Ann").mark(3, 7, ANN);
            var query = MentionQuery.at(new MarkedEdit(value, 2, 2, null), AT).orElseThrow();
            assertEquals(3, query.end());
        }
    }

    @Nested
    @DisplayName("its range")
    class Range {

        @Test
        @DisplayName("runs past the caret to the end of the word")
        void wholeWord() {
            var query = queryIn("Ask @annie now", 7).orElseThrow();
            assertEquals("an", query.query());
            assertEquals(4, query.start());
            assertEquals(10, query.end());
            assertEquals("@an", query.typed());
        }
    }

    @Nested
    @DisplayName("completing")
    class Completing {

        @Test
        @DisplayName("puts the atom in with a space after it, the caret past both")
        void atomAndSpace() {
            var edit = MarkedEdit.of(MarkedText.of("Ask @an"));
            var query = MentionQuery.at(edit, AT).orElseThrow();
            edit = edit.replace(query.start(), query.end(), query.completion("@Ann Lee", ANN, edit.value()));
            assertEquals("Ask @Ann Lee ", edit.text());
            assertEquals(List.of(new Mark(4, 12, ANN)), edit.value().marks());
            assertEquals(13, edit.caret());
            assertTrue(MentionQuery.at(edit, AT).isEmpty(), "the query is over");
        }

        @Test
        @DisplayName("adds no space before whitespace already there, and replaces the whole word")
        void noDoubleSpace() {
            var edit = new MarkedEdit(MarkedText.of("Ask @annie now"), 7, 7, null);
            var query = MentionQuery.at(edit, AT).orElseThrow();
            edit = edit.replace(query.start(), query.end(), query.completion("@Ann Lee", ANN, edit.value()));
            assertEquals("Ask @Ann Lee now", edit.text());
            assertEquals(12, edit.caret());
        }

        @Test
        @DisplayName("with no kind puts plain text in, such as an emoji for a shortcode")
        void plain() {
            var edit = MarkedEdit.of(MarkedText.of("yes :thu"));
            var query = MentionQuery.at(edit, Set.of(":")).orElseThrow();
            edit = edit.replace(query.start(), query.end(), query.completion("👍", null, edit.value()));
            assertEquals("yes 👍 ", edit.text());
            assertTrue(edit.value().marks().isEmpty());
        }

        @Test
        @DisplayName("the atom is stepped over and deleted whole")
        void anAtom() {
            var edit = MarkedEdit.of(MarkedText.of("@an"));
            var query = MentionQuery.at(edit, AT).orElseThrow();
            edit = edit.replace(query.start(), query.end(), query.completion("@Ann Lee", ANN, edit.value()));
            edit = edit.left(false).left(false);
            assertEquals(0, edit.caret(), "one step back over the space, one over the atom");
            edit = edit.toEnd(false).left(false).backspace();
            assertEquals(" ", edit.text());
            assertTrue(edit.value().marks().isEmpty());
        }

        @Test
        @DisplayName("survives undo and redo")
        void undoRedo() {
            var history = new EditHistory<MarkedEdit>();
            var edit = type(MarkedEdit.of(MarkedText.EMPTY), "@an");
            history.record(MarkedEdit.EMPTY, edit, EditHistory.Kind.TYPING);
            var query = MentionQuery.at(edit, AT).orElseThrow();
            var done = edit.replace(query.start(), query.end(), query.completion("@Ann Lee", ANN, edit.value()));
            history.record(edit, done, EditHistory.Kind.OTHER);
            var undone = history.undo(done);
            assertEquals("@an", undone.text());
            var redone = history.redo(undone);
            assertEquals(List.of(new Mark(0, 8, ANN)), redone.value().marks());
        }

        @Test
        @DisplayName("refuses nothing to put in")
        void refusesEmpty() {
            var query = queryIn("@a", 2).orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> query.completion("", ANN, MarkedText.of("@a")));
        }
    }

    @Nested
    @DisplayName("triggers")
    class Triggers {

        @ParameterizedTest
        @ValueSource(strings = {"", "ab", "a", "7", " ", "_"})
        @DisplayName("must be one character that cannot be part of a word")
        void refused(String trigger) {
            assertThrows(IllegalArgumentException.class, () -> MentionQuery.requireTriggers(Set.of(trigger)));
        }

        @Test
        @DisplayName("none opens nothing")
        void none() {
            assertTrue(MentionQuery.at(MarkedEdit.of(MarkedText.of("@a")), Set.of())
                    .isEmpty());
        }
    }
}
