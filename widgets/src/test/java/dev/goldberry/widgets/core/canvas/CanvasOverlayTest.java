package dev.goldberry.widgets.core.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.Observable;
import dev.goldberry.bind.Property;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Row;

/// Real widgets over a `canvas`, placed where the painter put things:
/// [Canvas#overlay].
///
/// What is asserted is what a painted rectangle could not do: be placed by the
/// painter's own arithmetic and follow the canvas when it changes size, take
/// the keyboard in the order it was listed, take a press before the canvas
/// under it, and be read out by name.
@DisplayName("a canvas overlay")
class CanvasOverlayTest {

    /// A canvas 300 or 400 wide with 10px of padding, so its content box starts
    /// ten in and is 280 or 380 wide.
    private static final String SHEET = """
            canvas { width: 300px; height: 200px; padding: 10px; }
            canvas.wide { width: 400px; }
            """;

    private final List<String> log = new ArrayList<>();

    private final List<PointerEvent> heard = new ArrayList<>();

    private final Property<Boolean> wide = Property.of(false);

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// The canvas, rebuilt whenever [#wide] changes.
    private record Board(Property<Boolean> wide, Canvas canvas) implements Widget.Stateless {

        @Override
        public Observable<?> binding() {
            return wide;
        }

        @Override
        public Widget build(BuildContext context) {
            return new Row(canvas.styled(Boolean.TRUE.equals(wide.get()) ? "wide" : "plain"));
        }
    }

    private Widget block(String name) {
        return new Pressable(name, () -> log.add("pressed:" + name)).id(name);
    }

    /// `second` first in the list and on the right, running 20 past the content
    /// box; `first` on the left, half the width.
    private List<Positioned> layout(LogicalSize size) {
        return List.of(
                new Positioned(block("second"), LogicalRect.of(size.width() / 2, 20, size.width() / 2 + 20, 40)),
                new Positioned(block("first"), LogicalRect.of(0, 0, size.width() / 2, 40)));
    }

    private Session open() {
        var canvas = new Canvas(null, new Input() {
                    @Override
                    public void onPointer(PointerEvent event) {
                        heard.add(event);
                    }

                    @Override
                    public boolean focusable() {
                        return false;
                    }
                })
                .id("canvas")
                .overlay(this::layout);
        return Offscreen.of(500, 240)
                .stylesheets(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET)))
                .session(new Board(wide, canvas));
    }

    private static Element byId(Session session, String id) {
        return session.byId(id).orElseThrow(() -> new AssertionError("no #" + id));
    }

    private static LogicalRect painted(Session session, String id) {
        var element = byId(session, id);
        return session.regions().stream()
                .filter(region -> region.owner() == element)
                .findFirst()
                .orElseThrow(() -> new AssertionError("#" + id + " was not painted"))
                .painted();
    }

    private static LogicalRect content(Session session) {
        var canvas = byId(session, "canvas");
        return session.regions().stream()
                .filter(region -> region.owner() == canvas)
                .findFirst()
                .orElseThrow()
                .content();
    }

    private static void assertRect(LogicalRect expected, LogicalRect actual) {
        assertEquals(expected.left(), actual.left(), 0.01, () -> expected + " vs " + actual);
        assertEquals(expected.top(), actual.top(), 0.01, () -> expected + " vs " + actual);
        assertEquals(expected.width(), actual.width(), 0.01, () -> expected + " vs " + actual);
        assertEquals(expected.height(), actual.height(), 0.01, () -> expected + " vs " + actual);
    }

    @Test
    @DisplayName("places each widget at its rectangle, from the corner of the content box")
    void placed() {
        try (var session = open()) {
            var content = content(session);
            assertEquals(280, content.width(), 0.01);

            assertRect(LogicalRect.of(content.left(), content.top(), 140, 40), painted(session, "first"));
            assertRect(LogicalRect.of(content.left() + 140, content.top() + 20, 160, 40), painted(session, "second"));
        }
    }

    @Test
    @DisplayName("places them again when the canvas changes size, and a focused one keeps its element")
    void replaced() {
        try (var session = open()) {
            assertTrue(session.focus("first"));
            var before = byId(session, "first");

            wide.set(true);
            session.frame();

            var content = content(session);
            assertEquals(380, content.width(), 0.01);
            assertRect(LogicalRect.of(content.left(), content.top(), 190, 40), painted(session, "first"));
            assertRect(LogicalRect.of(content.left() + 190, content.top() + 20, 210, 40), painted(session, "second"));
            assertSame(before, byId(session, "first"), "keyed, so the same element");
            assertSame(before, session.focused().orElseThrow(), "and it still has the keyboard");
        }
    }

    @Test
    @DisplayName("takes the keyboard in the order of the list")
    void focusOrder() {
        try (var session = open()) {
            session.key(Key.TAB);
            assertSame(byId(session, "second"), session.focused().orElseThrow());
            session.key(Key.TAB);
            assertSame(byId(session, "first"), session.focused().orElseThrow());
        }
    }

    @Test
    @DisplayName("a press on a widget reaches it and not the canvas")
    void pressOnChild() {
        try (var session = open()) {
            session.click("first");

            assertEquals(List.of("pressed:first"), log);
            assertFalse(
                    heard.stream().anyMatch(event -> event.kind() == PointerEvent.Kind.PRESSED),
                    () -> "the canvas heard "
                            + heard.stream().map(PointerEvent::kind).toList());
        }
    }

    @Test
    @DisplayName("a press beside the widgets reaches the canvas, in the painter's coordinates")
    void pressBeside() {
        try (var session = open()) {
            var content = content(session);
            session.click(content.left() + 30, content.top() + 150);

            assertEquals(List.of(), log);
            var press = heard.stream()
                    .filter(event -> event.kind() == PointerEvent.Kind.PRESSED)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the canvas heard no press"));
            assertEquals(30, press.content().x(), 0.01);
            assertEquals(150, press.content().y(), 0.01);
        }
    }

    @Test
    @DisplayName("clips a widget to the content box, for the pointer as for the eye")
    void clipped() {
        try (var session = open()) {
            var content = content(session);
            var second = painted(session, "second");
            // The right-hand block was laid out 20 past the content box, into
            // the padding; what of it is there is cut off, and a point there is
            // the canvas's.
            assertEquals(content.right() + 20, second.right(), 0.01);
            var under =
                    session.elementAt(content.right() + 5, second.top() + 10).orElseThrow();
            assertSame(byId(session, "canvas"), under);
            var inside =
                    session.elementAt(content.right() - 5, second.top() + 10).orElseThrow();
            assertSame(byId(session, "second"), inside);
        }
    }

    @Test
    @DisplayName("lists the widgets in the semantics tree, by name")
    void semantics() {
        try (var session = open()) {
            var names = session.byRole(Role.BUTTON).stream()
                    .map(Element::widget)
                    .map(widget -> ((Pressable) widget).accessibleName())
                    .toList();
            assertEquals(List.of("second", "first"), names);
            assertTrue(session.byRole(Role.BUTTON, "first").isPresent());
        }
    }

    @Test
    @DisplayName("a canvas with no overlay has no children, as before")
    void noOverlay() {
        assertEquals(List.of(), new Canvas((frame, size) -> {}).children());
        assertEquals(List.of(), new Canvas(null, null, Attributes.NONE, null).children());
    }
}
