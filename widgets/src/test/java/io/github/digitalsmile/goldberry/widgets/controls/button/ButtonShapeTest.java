package io.github.digitalsmile.goldberry.widgets.controls.button;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// §3's four remaining button options: `outlined`, `square`, `circle` and
/// `float` ([ADR-0347]). Three are classes and need an image; what a test can
/// check is the one rule with logic in it — an icon-only button is a circle
/// unless told `square` — and the placement one.
class ButtonShapeTest {

    private Icon plus;

    @BeforeEach
    void icon() {
        plus = Icon.bundled("plus", 16);
    }

    @Nested
    @DisplayName("an icon-only button is a circle")
    class CircleByDefault {

        @Test
        @DisplayName("with no label and an icon, `circle` is added")
        void circle() {
            assertTrue(new Button("", plus, () -> {}, false, null).classes().contains("circle"));
        }

        @Test
        @DisplayName("a label, or `square`, or `circle` written already, leaves the classes alone")
        void notAddedOtherwise() {
            assertFalse(new Button("Add", plus, () -> {}, false, null).classes().contains("circle"));
            assertEquals(
                    Set.of("square"),
                    new Button("", plus, () -> {}, false, Attributes.NONE.classes("square")).classes());
            assertEquals(
                    Set.of("circle", "primary"),
                    new Button("", plus, () -> {}, false, Attributes.NONE.classes("circle", "primary")).classes());
        }
    }

    @Nested
    @DisplayName("float")
    class Floating {

        private TestHost host;

        @BeforeEach
        void setUp() {
            host = new TestHost();
        }

        @Test
        @DisplayName("the button goes to the overlay layer and the tree gets nothing")
        void floatsInTheOverlay() {
            var tree = new ElementTree(new Floated(new Button("", plus, () -> {}, false, null)), host);
            tree.flush();

            var state = (FloatedState) tree.root().state().orElseThrow();
            var overlay = state.overlay();
            assertNotNull(overlay, "the host was handed an overlay");
            assertEquals(Corner.BOTTOM_END, overlay.corner());
            var floated = ((FloatSlot) overlay.widget()).button();
            assertTrue(
                    floated.classes().containsAll(Set.of("float", "circle")),
                    floated.classes().toString());
            assertTrue(tree.root().children().getFirst().children().isEmpty(), "nothing is built in place");
        }

        @Test
        @DisplayName("unmounting takes it down, and a rebuild with the same button leaves it up")
        void unmountRemoves() {
            var tree = new ElementTree(new Floated(new Button("", plus, () -> {}, false, null)), host);
            tree.flush();
            var state = (FloatedState) tree.root().state().orElseThrow();
            var overlay = state.overlay();
            assertNotNull(overlay);

            tree.update(new Floated(new Button("", plus, () -> {}, false, null)));
            tree.flush();
            assertTrue(overlay == state.overlay(), "an equal button must not be taken down and put back");

            var slot = (FloatSlot) overlay.widget();
            tree.unmount();

            assertNull(state.overlay(), "the state lets go at once");
            assertTrue(slot.isLeaving(), "and the button is sent out, to be removed when its exit has played");
        }

        @Test
        @DisplayName("a leaving button carries `leaving`, ignores a press, and is removed after --gb-motion-fast")
        void leavesThenGoes() {
            var presses = new int[1];
            var tree = new ElementTree(new Floated(new Button("", plus, () -> presses[0]++, false, null)), host);
            tree.flush();
            var state = (FloatedState) tree.root().state().orElseThrow();
            var slot = (FloatSlot) state.overlay().widget();
            slot.button().onPress().run();
            assertEquals(1, presses[0]);

            tree.unmount();

            var leaving = (Button) slot.build(null);
            assertTrue(
                    leaving.classes().contains(FloatSlot.LEAVING),
                    leaving.classes().toString());
            slot.button().onPress().run();
            assertEquals(1, presses[0], "a press on a button on its way out is a ghost click");
            assertEquals(
                    Duration.ofMillis((long) FloatedState.EXIT_FALLBACK_MILLIS),
                    host.scheduledDelays().getLast(),
                    "no stylesheet here, so fast's specified 100 ms");
            assertTrue(host.hasPendingTimer(), "the overlay is removed by the timer, not at once");
        }

        @Test
        @DisplayName("float=#true and corner= inflate to the wrapper")
        void inflates() {
            var wiring = new Wiring(
                    io.github.digitalsmile.goldberry.bind.registry.ActionRegistry.none(),
                    Icons.lenient().bind("plus", plus),
                    io.github.digitalsmile.goldberry.bind.registry.BindingRegistry.none());
            var widget = Widgets.inflater(wiring)
                    .inflate(KdlParser.parse("button float=#true corner=\"top-start\" icon=\"plus\" name=\"New\"")
                            .getFirst());

            var floated = assertInstanceOf(Floated.class, widget);
            assertEquals(Corner.TOP_START, floated.corner());
            assertEquals("", floated.button().label());

            var plain = Widgets.inflater(wiring)
                    .inflate(
                            KdlParser.parse("button icon=\"plus\" name=\"New\"").getFirst());
            assertInstanceOf(Button.class, plain);
            assertNull(Floated.corner("nowhere") == Corner.BOTTOM_END ? null : "x", "an unknown corner is the default");
        }
    }
}
