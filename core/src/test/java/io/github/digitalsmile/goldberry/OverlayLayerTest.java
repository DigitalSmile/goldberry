package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.widget.Corner;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.WindowRoot;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [WindowRoot] and the in-window overlay layer — `docs/core-widgets.md` §7's
/// half that needs no platform window.
///
/// The invariants worth a test are all about *not disturbing the application*: an
/// overlay must not take space from the content, must not re-parent it when it
/// comes and goes, and must be painted after it.
class OverlayLayerTest {

    /// Something to float, and something to float it over.
    private record Marker(String name) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        if (font != null) {
            font.close();
        }
    }

    private WidgetRenderer renderer(String css) {
        return new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), font);
    }

    private static Overlay attach(Property<List<Overlay>> overlays, Widget widget, Corner corner) {
        // What Launcher.overlay does, without a window under it.
        var entry = Overlay.of(widget, corner);
        var next = new java.util.ArrayList<>(overlays.get());
        next.add(entry);
        overlays.set(List.copyOf(next));
        entry.attached(() -> overlays.set(overlays.get().stream()
                .filter(existing -> existing != entry)
                .toList()));
        return entry;
    }

    @Test
    @DisplayName("a window with nothing floating is its content and one box around it")
    void empty() {
        var renderer = renderer("app { width: 100px }");
        var box = renderer.render(new ElementTree(WindowRoot.of(new Marker("app"))));

        assertEquals(1, box.children().size());
        assertEquals(PositionType.RELATIVE, box.children().getFirst().position(),
                "the content is in flow; only overlays are taken out of it");
        assertEquals(1.0, box.children().getFirst().flexGrow(),
                "and it grows, because the root node is laid out at the window's size"
                        + " and a window is what its content fills");
    }

    @Test
    @DisplayName("an overlay is out of flow, so the content is exactly the size it was")
    void overlayTakesNoSpace() {
        var overlays = Property.<List<Overlay>>of(List.of());
        var root = new WindowRoot(new Marker("app"), overlays);
        var renderer = renderer("app { width: 100px } hud { width: 40px }");
        var tree = new ElementTree(root);

        var before = renderer.render(tree);
        attach(overlays, new Marker("hud"), Corner.BOTTOM_END);
        tree.flush();
        var after = renderer.render(tree);

        assertEquals(1, before.children().size());
        assertEquals(2, after.children().size());
        assertEquals(before.children().getFirst().width(), after.children().getFirst().width(),
                "an overlay that changed the content's box would be a layout, not an overlay");
        assertEquals(1.0, after.children().getFirst().flexGrow());
        assertEquals(PositionType.ABSOLUTE, after.children().get(1).position());
    }

    @Test
    @DisplayName("the content is painted first, whatever is floating over it")
    void contentIsFirst() {
        var overlays = Property.<List<Overlay>>of(List.of());
        var root = new WindowRoot(new Marker("app"), overlays);
        attach(overlays, new Marker("hud"), Corner.TOP_START);

        var children = root.children();

        assertEquals(2, children.size());
        assertEquals("app", ((Styled) children.getFirst()).cssType(),
                "a box tree has no z-order beyond document order (ADR-0053),"
                        + " so being painted last is being listed last");
        assertEquals("hud", ((Styled) children.get(1)).cssType());
    }

    @Test
    @DisplayName("adding an overlay does not re-parent the application")
    void addingKeepsTheContentsElement() {
        var overlays = Property.<List<Overlay>>of(List.of());
        var tree = new ElementTree(new WindowRoot(new Marker("app"), overlays));
        var renderer = renderer("app { width: 100px }");
        renderer.render(tree);
        var content = tree.root().children().getFirst();

        attach(overlays, new Marker("hud"), Corner.BOTTOM_END);
        tree.flush();
        renderer.render(tree);

        assertSame(content, tree.root().children().getFirst(),
                "the same element, so its state, its focus and any animation in flight survive"
                        + " — which is the whole reason the layer is there from the first frame");
    }

    @Test
    @DisplayName("removing takes the widget away and leaves the rest")
    void removing() {
        var overlays = Property.<List<Overlay>>of(List.of());
        var root = new WindowRoot(new Marker("app"), overlays);
        var first = attach(overlays, new Marker("hud"), Corner.TOP_END);
        var second = attach(overlays, new Marker("hud"), Corner.BOTTOM_END);

        assertTrue(first.isAttached());
        first.remove();

        assertFalse(first.isAttached());
        assertEquals(List.of(second), overlays.get(),
                "two equal widgets in two corners are two overlays; removing is by identity");
        assertEquals(2, root.children().size());

        // Idempotent: removing twice is what shutdown looks like when two things
        // both think they own it.
        first.remove();
        assertEquals(List.of(second), overlays.get());
    }

    @Test
    @DisplayName("the layer is watched, not captured, so a change rebuilds the root")
    void overlaysAreABinding() {
        var overlays = Property.<List<Overlay>>of(List.of());
        var tree = new ElementTree(new WindowRoot(new Marker("app"), overlays));
        var renderer = renderer("app { width: 100px }");
        renderer.render(tree);

        assertFalse(tree.needsBuild());
        attach(overlays, new Marker("hud"), Corner.TOP_START);

        assertTrue(tree.needsBuild(),
                "the root element subscribes to the list for as long as it lives (ADR-0062):"
                        + " nothing else invalidates it, because the root widget of a tree"
                        + " cannot be swapped");
    }

    @Test
    @DisplayName("each corner pins the two edges it touches and leaves the other two alone")
    void cornerInsets() {
        var margin = 12f;

        var topStart = Corner.TOP_START.insets(margin);
        assertEquals(StyleLength.points(margin), topStart.top());
        assertEquals(StyleLength.points(margin), topStart.left());
        assertEquals(StyleLength.UNDEFINED, topStart.right());
        assertEquals(StyleLength.UNDEFINED, topStart.bottom(),
                "an inset of zero on the other edges would stretch the overlay across the window;"
                        + " undefined leaves it its own size");

        var bottomEnd = Corner.BOTTOM_END.insets(margin);
        assertEquals(StyleLength.points(margin), bottomEnd.bottom());
        assertEquals(StyleLength.points(margin), bottomEnd.right());
        assertEquals(StyleLength.UNDEFINED, bottomEnd.top());
        assertEquals(StyleLength.UNDEFINED, bottomEnd.left());
    }

    @Test
    @DisplayName("a corner is written the way a stylesheet writes it")
    void cornerNames() {
        assertEquals("bottom-end", Corner.BOTTOM_END.cssName());
        assertEquals(Corner.BOTTOM_END, Corner.parse("bottom-end"));
        assertTrue(Corner.TOP_START.isTop());
        assertTrue(Corner.TOP_START.isStart());
        assertFalse(Corner.BOTTOM_END.isTop());
        assertFalse(Corner.BOTTOM_END.isStart());

        // `bottom-right` is the guess someone who knows CSS will make, and the
        // message has to say what this vocabulary takes instead.
        var refused = assertThrows(IllegalArgumentException.class, () -> Corner.parse("bottom-right"));
        assertTrue(refused.getMessage().contains("bottom-end"));
    }

    @Test
    @DisplayName("a margin has to be a distance")
    void refusesANonsenseMargin() {
        assertThrows(IllegalArgumentException.class,
                () -> Overlay.of(new Marker("hud"), Corner.TOP_END, -1));
        assertThrows(IllegalArgumentException.class,
                () -> Overlay.of(new Marker("hud"), Corner.TOP_END, Float.NaN));
    }
}
