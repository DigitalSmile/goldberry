package dev.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Styled;

/// A click of any button, and `onClick` on any node: the secondary and middle
/// buttons click by the primary's rule, as their own kind, and a node that is
/// not a control can be given a click handler as an attribute.
@DisplayName("clicks of every button")
class ClickHookTest {

    /// A node that implements no input interface: the attribute has to reach it.
    private record Plain(String name, List<Widget> kids, Attributes attributes)
            implements Widget.Leaf, Attributed<Plain>, Styled {

        Plain(String name, Widget... kids) {
            this(name, List.of(kids), Attributes.NONE.id(name));
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Plain withAttributes(Attributes value) {
            return new Plain(name, kids, value);
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }
    }

    /// A control that logs every pointer event it is given and consumes clicks
    /// when told to.
    private record Control(String name, List<String> log, boolean consumes, Attributes attributes)
            implements Widget.Leaf, Attributed<Control>, Styled, Handles {

        @Override
        public Control withAttributes(Attributes value) {
            return new Control(name, log, consumes, value);
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED || event.kind() == PointerEvent.Kind.AUX_CLICKED) {
                log.add(name + ":" + event.kind() + ":" + event.button());
                if (consumes) {
                    event.consume();
                }
            }
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }
    }

    private final List<String> log = new ArrayList<>();
    private PointerRouter router;
    private ElementTree tree;

    private static Element find(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// The window is 100x100. `panel` is its left half, and `inner` the
    /// panel's top-left quarter; the right half is the window alone.
    private void mount(Widget root) {
        tree = new ElementTree(root);
        router = new PointerRouter();
        var regions = new ArrayList<HitTest.Region>();
        regions.add(HitTest.Region.of(find(tree.root(), "window"), 0, 0, 100, 100));
        regions.add(HitTest.Region.of(find(tree.root(), "panel"), 0, 0, 50, 100));
        var inner = find(tree.root(), "inner");
        if (inner != null) {
            regions.add(HitTest.Region.of(inner, 0, 0, 25, 50));
        }
        router.updateRegions(regions);
    }

    private void click(float x, float y, PointerEvent.Button button) {
        router.pointerPressed(x, y, button, 1);
        router.pointerReleased(x, y, button, 1);
    }

    @Test
    @DisplayName("a secondary and a middle click arrive as AUX_CLICKED, and a primary one as CLICKED")
    void auxClicks() {
        mount(new Plain("window", new Control("panel", log, true, Attributes.NONE.id("panel"))));

        click(10, 10, PointerEvent.Button.PRIMARY);
        click(10, 10, PointerEvent.Button.SECONDARY);
        click(10, 10, PointerEvent.Button.MIDDLE);

        assertEquals(List.of("panel:CLICKED:PRIMARY", "panel:AUX_CLICKED:SECONDARY", "panel:AUX_CLICKED:MIDDLE"), log);
    }

    @Test
    @DisplayName("a right press dragged off the node and let go is no click")
    void draggedOff() {
        mount(new Plain("window", new Control("panel", log, true, Attributes.NONE.id("panel"))));

        router.pointerPressed(10, 10, PointerEvent.Button.SECONDARY, 1);
        router.pointerMoved(80, 10);
        router.pointerReleased(80, 10, PointerEvent.Button.SECONDARY, 1);

        assertTrue(log.isEmpty(), "released over the window, not the panel: " + log);
    }

    @Test
    @DisplayName("onClick reaches a plain node with the button and the position in its own box")
    void onClick() {
        var heard = new ArrayList<String>();
        mount(new Plain(
                "window",
                new Plain("panel")
                        .onClick(event ->
                                heard.add(event.button() + " " + event.local().fractionX() + ","
                                        + event.local().fractionY()))));

        click(25, 50, PointerEvent.Button.PRIMARY);
        click(25, 50, PointerEvent.Button.SECONDARY);
        click(80, 50, PointerEvent.Button.PRIMARY);

        assertEquals(List.of("PRIMARY 0.5,0.5", "SECONDARY 0.5,0.5"), heard, "not the click off the panel");
    }

    @Test
    @DisplayName("onClick consumes, so an ancestor's does not run, and runs after the node's own handler")
    void bubbling() {
        var heard = new ArrayList<String>();
        mount(new Plain(
                        "window",
                        new Control("panel", log, false, Attributes.NONE.id("panel"))
                                .onClick(event -> heard.add("panel")))
                .onClick(event -> heard.add("window")));

        click(10, 10, PointerEvent.Button.PRIMARY);

        assertEquals(List.of("panel:CLICKED:PRIMARY"), log, "the node's own handler first");
        assertEquals(List.of("panel"), heard, "and the window's onClick never ran");
    }

    @Test
    @DisplayName("a control inside the node that takes the click keeps it")
    void innerControlKeepsIt() {
        var heard = new ArrayList<String>();
        mount(new Plain(
                "window",
                new Plain("panel", new Control("inner", log, true, Attributes.NONE.id("inner")))
                        .onClick(event -> heard.add("panel"))));

        click(10, 10, PointerEvent.Button.PRIMARY);
        click(40, 80, PointerEvent.Button.PRIMARY);

        assertEquals(List.of("inner:CLICKED:PRIMARY"), log);
        assertEquals(List.of("panel"), heard, "only the click beside the inner control");
    }
}
