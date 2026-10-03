package dev.goldberry.example.ui.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.markdown.model.Block;
import dev.goldberry.markdown.model.Heading;
import dev.goldberry.markdown.model.Item;
import dev.goldberry.markdown.model.MarkdownNode;
import dev.goldberry.markdown.view.MarkdownView;

/// That the Markdown screen is **live**, which is the one thing about it a picture
/// cannot show: typing on the left changes what is on the right, and ticking a box
/// on the right edits the source on the left.
///
/// This drives the real controls: a `TextEvent` into the editor, a flush, and the
/// preview's own document read back. Nothing in the showcase connects the two,
/// which is the point: a preview is a binding, not a callback.
class MarkdownChapterTest {

    private ContentFixture screen;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        screen = new ContentFixture(MarkdownChapter::new);
    }

    @AfterEach
    void tearDown() {
        if (screen != null) {
            screen.close();
        }
    }

    private Object source() {
        return Models.observable(screen.model, "md.source").get();
    }

    /// The editor's box, which is the node that hears the keyboard.
    private Handles editor() {
        return screen.all(Handles.class).stream()
                .filter(handles -> handles.getClass().getSimpleName().equals("TextAreaBox"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Markdown screen has no editor"));
    }

    /// Every task item under `node`, in document order, which is the order the view
    /// numbers them in.
    private static void collectTasks(MarkdownNode node, List<Item> found) {
        if (node instanceof Item item && item.task()) {
            found.add(item);
        }
        if (node instanceof Block block) {
            block.children().forEach(child -> collectTasks(child, found));
        }
    }

    @Test
    @DisplayName("types on the left and the document on the right is the next frame")
    void typingChangesThePreview() {
        var before = screen.first(MarkdownView.class).resolved();
        assertTrue(
                before.blocks().getFirst() instanceof Heading heading && heading.level() == 1,
                "the sample opens with a heading");

        // At the end of the sample, where the caret is in an area that has just
        // been given a value; and a heading, so what arrives is a block.
        editor().onText(new TextEvent("\n# Typed just now\n", null));
        screen.frame();

        var after = screen.first(MarkdownView.class).resolved();
        assertNotEquals(before, after, "the preview should not be showing the document it was showing");
        assertTrue(String.valueOf(source()).endsWith("# Typed just now\n"), "the model holds what was typed");
        assertTrue(
                after.blocks().getLast() instanceof Heading typed
                        && typed.text().equals("Typed just now"),
                () -> "the preview parses what was typed, and ends with "
                        + after.blocks().getLast());
        assertEquals(before.blocks().size() + 1, after.blocks().size(), "one more block than before, and only one");
    }

    @Test
    @DisplayName("ticking a box rewrites the source, and the preview shows the edit")
    void tickingATaskEditsTheSource() {
        var before = String.valueOf(source());
        var box = screen.all(Handles.class).stream()
                .filter(handles -> handles.getClass().getSimpleName().equals("TaskMark"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the sample has task boxes, and they should be pressable"));

        box.onPointer(new PointerEvent(
                PointerEvent.Kind.CLICKED, 4f, 4f, PointerEvent.Button.PRIMARY, 1, screen.tree.root()));
        screen.frame();

        var after = String.valueOf(source());
        assertNotEquals(before, after, "the ordinal reached the model and the model rewrote one character");
        assertEquals(before.length(), after.length(), "one character rewritten, not the document re-serialised");
        // The first task in the sample is `- [x] Parse it natively`, so a press
        // unticks it.
        assertTrue(after.contains("- [ ] Parse it natively"), "the first task should have been unticked");
        assertFalse(after.contains("- [x] Parse it natively"));
        var tasks = new ArrayList<Item>();
        collectTasks(screen.first(MarkdownView.class).resolved(), tasks);
        assertFalse(tasks.isEmpty(), "the sample has task items");
        assertFalse(tasks.getFirst().done(), "the preview parsed the source the tick produced");
    }

    @Test
    @DisplayName("reads and writes one property, which is what makes it live")
    void oneProperty() {
        var preview = screen.first(MarkdownView.class);
        assertNotNull(preview.binding(), "a preview that follows nothing is a screenshot");
        assertSame(Models.observable(screen.model, "md.source"), preview.binding());
    }
}
