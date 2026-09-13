package io.github.digitalsmile.goldberry.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.markdown.model.Block;
import io.github.digitalsmile.goldberry.markdown.model.Item;
import io.github.digitalsmile.goldberry.markdown.model.MarkdownNode;

/// Ticking a box, which is an edit to the author's own text.
///
/// The half of ADR-0300 that is arithmetic: `markdown-view` reports **which** task
/// was pressed and this turns that into a one-character rewrite. Two things have to
/// be true for that to work, and both are asserted here — the rewrite changes one
/// character and nothing else, and this scanner counts the same tasks md4c does.
@DisplayName("Markdown.toggleTask")
class TasksTest {

    private static final String DOCUMENT = """
            # Things

            - [ ] first
            - [x] second
            - an ordinary bullet
            - [ ] third

            ```
            - [ ] not a task, it is a program
            ```

            1. [ ] a numbered one
            """;

    @Test
    @DisplayName("flips the box it was asked for, and only that character")
    void flipsOne() {
        MarkdownRequirement.enforce();

        var after = Markdown.toggleTask(DOCUMENT, 0);

        assertEquals(DOCUMENT.replace("- [ ] first", "- [x] first"), after);
    }

    @Test
    @DisplayName("and back again, because a box is a toggle")
    void flipsBack() {
        var ticked = Markdown.toggleTask(DOCUMENT, 1);

        assertEquals(DOCUMENT.replace("- [x] second", "- [ ] second"), ticked);
        assertEquals(DOCUMENT, Markdown.toggleTask(ticked, 1));
    }

    @Test
    @DisplayName("counts the boxes and not the bullets between them")
    void skipsOrdinaryItems() {
        // The third *task* is the fourth item. An implementation that counted list
        // items would tick the wrong line here, and the document would look fine.
        var after = Markdown.toggleTask(DOCUMENT, 2);

        assertEquals(DOCUMENT.replace("- [ ] third", "- [x] third"), after);
    }

    @Test
    @DisplayName("a marker inside a fence is a program, not a task")
    void skipsFences() {
        // Index 3 is the numbered task *after* the fence. If the fenced line counted,
        // this would edit somebody's code sample.
        var after = Markdown.toggleTask(DOCUMENT, 3);

        assertEquals(DOCUMENT.replace("1. [ ] a numbered one", "1. [x] a numbered one"), after);
        assertEquals(DOCUMENT, Markdown.toggleTask(DOCUMENT, 4), "there is no fifth task");
    }

    @Test
    @DisplayName("an index nothing answers leaves the source exactly as it was")
    void outOfRangeIsUnchanged() {
        // A stale click -- the frame that drew the box is one edit behind the text --
        // should do nothing rather than end the application.
        assertSame(DOCUMENT, Markdown.toggleTask(DOCUMENT, 99));
        assertSame(DOCUMENT, Markdown.toggleTask(DOCUMENT, -1));
        assertEquals("", Markdown.toggleTask("", 0));
    }

    @Test
    @DisplayName("null is a programming error")
    void nullRefused() {
        assertThrows(NullPointerException.class, () -> Markdown.toggleTask(null, 0));
    }

    @Test
    @DisplayName("every other byte of the document survives, spacing and all")
    void everythingElseSurvives() {
        var awkward = "-   [ ]   spaced out\r\n\r\n- [ ] second\n\n\ttabbed\n";

        var after = Markdown.toggleTask(awkward, 1);

        assertEquals(awkward.replace("- [ ] second", "- [x] second"), after);
        assertEquals(
                awkward.length(),
                after.length(),
                "a re-serialisation would have normalised the line endings and the spacing");
    }

    @Nested
    @DisplayName("agrees with the parser")
    class AgreesWithMd4c {

        /// Every task item md4c reports, in document order.
        private List<Item> tasksOf(String markdown) {
            var found = new ArrayList<Item>();
            collect(Markdown.parse(markdown), found);
            return found;
        }

        private void collect(MarkdownNode node, List<Item> found) {
            if (node instanceof Item item && item.task()) {
                found.add(item);
            }
            if (node instanceof Block block) {
                block.children().forEach(child -> collect(child, found));
            }
        }

        @Test
        @DisplayName("about how many tasks a document has")
        void sameCount() {
            MarkdownRequirement.enforce();

            // The load-bearing claim of ADR-0300: the index the renderer hands over is
            // counted by **md4c** walking the model, and the character this rewrites is
            // found by a **regex** over the source. Two counters that disagreed would
            // tick a box the reader did not press, and nothing would report it.
            assertEquals(tasksOf(DOCUMENT).size(), Tasks.count(DOCUMENT));
        }

        @Test
        @DisplayName("and about which of them are done")
        void sameStates() {
            MarkdownRequirement.enforce();

            var tasks = tasksOf(DOCUMENT);
            for (var index = 0; index < tasks.size(); index++) {
                var before = tasks.get(index).done();
                var flipped = tasksOf(Markdown.toggleTask(DOCUMENT, index));

                assertEquals(
                        !before,
                        flipped.get(index).done(),
                        "toggling task " + index + " should be the one the parser sees change");
                for (var other = 0; other < tasks.size(); other++) {
                    if (other != index) {
                        assertEquals(
                                tasks.get(other).done(),
                                flipped.get(other).done(),
                                "and no other box should have moved");
                    }
                }
            }
        }
    }
}
