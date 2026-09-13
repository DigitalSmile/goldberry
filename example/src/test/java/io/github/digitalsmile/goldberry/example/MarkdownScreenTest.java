package io.github.digitalsmile.goldberry.example;

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

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.MarkdownScreen;
import io.github.digitalsmile.goldberry.example.ui.Panes;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.markdown.model.Heading;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownStyles;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownView;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// That the Markdown screen is **live**, which is the one thing about it a picture
/// cannot show.
///
/// [GalleryGoldenTest] draws it and proves the panes are where they should be and
/// the document renders. What it cannot prove is that typing on the left changes
/// what is on the right — a golden of a screen nobody has typed into looks the same
/// whether the binding works or was quietly dropped.
///
/// So this drives the real controls: a `TextEvent` into the editor, a flush, and the
/// preview's own document read back. Nothing in the showcase connects the two, which
/// is the point (ADR-0296).
class MarkdownScreenTest {

    private final Showcase showcase = new Showcase();

    private ShowcaseModel model;
    private Fonts fonts;
    private ElementTree tree;
    private WidgetRenderer renderer;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        model = showcase.models().stream()
                .filter(ShowcaseModel.class::isInstance)
                .map(ShowcaseModel.class::cast)
                .findFirst()
                .orElseThrow();
        fonts = Fonts.bundled();
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(MarkdownStyles.stylesheet());
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
        renderer = new WidgetRenderer(sheets, fonts);
        var inflater = Widgets.inflater(
                model.named(), Icons.lenient(), showcase.models().toArray());
        tree = new ElementTree(new MarkdownScreen(Panes.markdown(inflater)));
        frame();
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
    }

    /// One frame: rebuild whatever is dirty, then render — which is what a window
    /// does and what makes the *next* assertion about the screen rather than about
    /// the widget tree.
    private void frame() {
        tree.flush();
        renderer.render(tree);
    }

    private <T> T first(Class<T> type) {
        var found = new ArrayList<T>();
        collect(tree.root(), type, found);
        assertFalse(found.isEmpty(), "no " + type.getSimpleName() + " on the Markdown screen");
        return found.getFirst();
    }

    /// Every task item under `node`, in document order — the same order the view
    /// numbers them in.
    private static void collectTasks(
            io.github.digitalsmile.goldberry.markdown.model.MarkdownNode node,
            List<io.github.digitalsmile.goldberry.markdown.model.Item> found) {
        if (node instanceof io.github.digitalsmile.goldberry.markdown.model.Item item && item.task()) {
            found.add(item);
        }
        if (node instanceof io.github.digitalsmile.goldberry.markdown.model.Block block) {
            block.children().forEach(child -> collectTasks(child, found));
        }
    }

    private static <T> void collect(Element element, Class<T> type, List<T> found) {
        if (type.isInstance(element.widget())) {
            found.add(type.cast(element.widget()));
        }
        element.children().forEach(child -> collect(child, type, found));
    }

    /// The editor's box, which is the node that hears the keyboard — the `text-area`
    /// widget itself is stateful and unstyled, exactly as `text-input` is.
    private Handles editor() {
        var found = new ArrayList<Handles>();
        collect(tree.root(), Handles.class, found);
        return found.stream()
                .filter(handles -> handles.getClass().getSimpleName().equals("TextAreaBox"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Markdown screen has no editor"));
    }

    @Test
    @DisplayName("types on the left and the document on the right is the next frame")
    void typingChangesThePreview() {
        var before = first(MarkdownView.class).resolved();
        assertTrue(
                before.blocks().getFirst() instanceof Heading heading && heading.level() == 1,
                "the sample opens with a heading");

        // At the end of the sample, because that is where the caret is in an area
        // that has just been given a value -- and a heading, so that what arrives on
        // the other side is a *block* rather than another word in the last
        // paragraph.
        editor().onText(new TextEvent("\n# Typed just now\n", null));
        frame();

        var after = first(MarkdownView.class).resolved();
        assertNotEquals(before, after, "the preview should not be showing the document it was showing");

        // The editor wrote the model -- through `change=`, which is an action --
        var source = String.valueOf(Models.observable(model, "md.source").get());
        assertTrue(source.endsWith("# Typed just now\n"), "the model holds what was typed, at the caret");
        // -- and the preview read it back, through `bind=`. Those two are the whole
        // mechanism; there is no third thing in between.
        assertTrue(
                after.blocks().getLast() instanceof Heading typed
                        && typed.text().equals("Typed just now"),
                () -> "the preview parses what was typed, and ends with "
                        + after.blocks().getLast());
        assertEquals(before.blocks().size() + 1, after.blocks().size(), "one more block than before, and only one");
    }

    @Test
    @DisplayName("ticking a box rewrites the source, and the editor shows the edit")
    void tickingATaskEditsTheSource() {
        var before = String.valueOf(Models.observable(model, "md.source").get());
        var marks = new ArrayList<io.github.digitalsmile.goldberry.input.handler.Handles>();
        collect(tree.root(), io.github.digitalsmile.goldberry.input.handler.Handles.class, marks);
        var box = marks.stream()
                .filter(handles -> handles.getClass().getSimpleName().equals("TaskMark"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the sample has task boxes, and they should be pressable"));

        // A real click, through the router's own event, so what is asserted is what a
        // reader does rather than a method this test found.
        box.onPointer(new io.github.digitalsmile.goldberry.input.event.PointerEvent(
                io.github.digitalsmile.goldberry.input.event.PointerEvent.Kind.CLICKED,
                4f,
                4f,
                io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.PRIMARY,
                1,
                tree.root()));
        frame();

        var after = String.valueOf(Models.observable(model, "md.source").get());
        assertNotEquals(before, after, "the ordinal reached the model and the model rewrote one character");
        assertEquals(before.length(), after.length(), "one character, not a re-serialisation (ADR-0300)");
        // The first task in the sample is `- [x] Parse it natively`, so a press
        // unticks it.
        assertTrue(after.contains("- [ ] Parse it natively"), "the first task should have been unticked");
        assertFalse(after.contains("- [x] Parse it natively"));
        // And the *preview* is the property read back, which is what makes this a
        // round trip rather than two halves that agree by accident.
        var tasks = new ArrayList<io.github.digitalsmile.goldberry.markdown.model.Item>();
        collectTasks(first(MarkdownView.class).resolved(), tasks);
        assertFalse(tasks.isEmpty(), "the sample has task items");
        assertFalse(tasks.getFirst().done(), "the preview parsed the source the tick produced");
    }

    @Test
    @DisplayName("reads and writes one property, which is what makes it live")
    void oneProperty() {
        var preview = first(MarkdownView.class);
        assertNotNull(preview.binding(), "a preview that follows nothing is a screenshot");
        assertSame(Models.observable(model, "md.source"), preview.binding());
    }
}
