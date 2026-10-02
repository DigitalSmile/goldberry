package dev.goldberry.widgets.overlay.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.Overlay;
import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.Property;
import dev.goldberry.css.Theme;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.handler.Measured;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.root.WindowRoot;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.overlay.dialog.DialogAction.Role;
import dev.goldberry.widgets.panel.Described;
import dev.goldberry.widgets.text.Text;

/// A `dialog` on a real overlay layer: a [WindowRoot] over some content, and a
/// host whose `fill` puts the overlay in the root's list the way a window's
/// does.
///
/// `DialogTest` is what a dialog decides about itself. This is what happens
/// **between** a dialog and the window: whose element a new dialog gets, who
/// takes a finished one away, how the application closes one itself, and how
/// tall one may be.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#dialog).
class DialogLayerTest {

    private static final int WIDTH = 460;
    private static final int HEIGHT = 300;

    /// A host whose overlays land in a [WindowRoot], so removing one is a
    /// change the tree sees.
    private static final class LayeredHost extends TestHost {

        final Property<List<Overlay>> overlays = Property.of(List.of());

        @Override
        public Overlay fill(Widget widget) {
            filled.add(widget);
            var holder = new ArrayList<Overlay>(1);
            var overlay = GoldberryTestAccess.attachedFilling(
                    widget,
                    () -> overlays.set(overlays.get().stream()
                            .filter(existing -> existing != holder.getFirst())
                            .toList()));
            holder.add(overlay);
            var next = new ArrayList<>(overlays.get());
            next.add(overlay);
            overlays.set(List.copyOf(next));
            return overlay;
        }
    }

    private LayeredHost host;
    private ElementTree tree;
    private Clock.Virtual clock;
    private List<String> pressed;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        host = new LayeredHost();
        clock = Clock.virtual();
        pressed = new ArrayList<>();
        tree = new ElementTree(new WindowRoot(new Text("The window behind the dialog."), host.overlays), host);
    }

    private Dialog dialog(String title, Widget... body) {
        var children = new ArrayList<Widget>(List.of(body));
        children.add(new DialogAction("Cancel", Role.DISMISSIVE, () -> pressed.add(title)));
        return new Dialog(title, children, null);
    }

    private Overlay show(Dialog dialog) {
        var overlay = Dialogs.show(host, dialog);
        tree.flush();
        return overlay;
    }

    private void escape() {
        Described.first(tree, DialogPanel.class)
                .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null));
        tree.flush();
    }

    private WidgetRenderer renderer() {
        return new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                .clock(clock);
    }

    /// The tree laid out at the window's size, opened past its entrance, and
    /// the rectangles a pointer router would read.
    private List<HitTest.Region> regions() {
        var renderer = renderer();
        renderer.render(tree);
        clock.advance(300);
        renderer.render(tree);
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f, 0);
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            return HitTest.capture(render);
        }
    }

    private static HitTest.@Nullable Region regionOf(List<HitTest.Region> regions, String cssType) {
        for (var region : regions) {
            if (region.owner() instanceof Element element
                    && element.widget() instanceof Styled styled
                    && styled.cssType().equals(cssType)) {
                return region;
            }
        }
        return null;
    }

    /// The panel the element is inside, or null.
    private static @Nullable DialogPanel panelAbove(Element element) {
        for (Element node = element; node != null; node = node.parent() instanceof Element up ? up : null) {
            if (node.widget() instanceof DialogPanel panel) {
                return panel;
            }
        }
        return null;
    }

    @Nested
    @DisplayName("one dialog after another")
    class OneAfterAnother {

        /// **The blocker.** Every anonymous dialog is called `dialog`, an id
        /// is a key, and overlays used to be matched like any other children:
        /// by class and key. So "Get started" removing the welcome dialog and
        /// showing the sign-in one in the same turn handed the sign-in dialog
        /// the welcome dialog's element, and with it a state whose fade was
        /// already over. It drew nothing, and its scrim took every click.
        @Test
        @DisplayName("removing one and showing another in the same turn shows the new one")
        void sameTurn() {
            var holder = new ArrayList<Overlay>(1);
            var welcome = new Dialog(
                    "Welcome",
                    List.of(new Text("Hello."), new DialogAction("Get started", Role.AFFIRMATIVE, () -> {
                        holder.getFirst().remove();
                        Dialogs.show(host, dialog("Sign in", new Text("Who are you?")));
                    })),
                    null);
            holder.add(show(welcome));

            Described.first(tree, DialogPanel.class)
                    .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, null));
            tree.flush();
            host.tick();
            tree.flush();

            assertEquals(1, host.overlays.get().size());
            assertEquals(
                    1, Described.counting(tree, "dialog"), "the new dialog has no panel: it inherited a closed one");
            assertEquals("Sign in", Described.first(tree, DialogPanel.class).title());

            var centre = HitTest.at(regions(), WIDTH / 2f, HEIGHT / 2f).orElseThrow();
            var panel = panelAbove((Element) centre);
            assertNotNull(panel, "the centre of the window is " + centre + ", not a dialog");
            assertEquals("Sign in", panel.title());
        }

        /// The same rule for every overlay, not only for dialogs: two of the
        /// same widget are two nodes, because each is keyed by its handle.
        @Test
        @DisplayName("two equal dialogs at once are two nodes")
        void twoEqual() {
            show(dialog("Same"));
            show(dialog("Same"));

            assertEquals(2, Described.counting(tree, "dialog"));
        }
    }

    @Nested
    @DisplayName("when the fade is over")
    class Over {

        /// A handler that forgets `remove()` used to leave the scrim on the
        /// window, drawing nothing and taking every press.
        @Test
        @DisplayName("the dialog takes itself off the window")
        void removesItself() {
            var overlay = show(dialog("Unsaved"));

            escape();
            assertTrue(overlay.isAttached(), "gone before it had faded");
            host.tick();
            tree.flush();

            assertEquals(List.of("Unsaved"), pressed);
            assertFalse(overlay.isAttached(), "the overlay outlived its dialog");
            assertEquals(0, Described.counting(tree, "dialog-scrim"));
        }

        @Test
        @DisplayName("and does even when the handler throws")
        void handlerThrows() {
            var overlay = show(new Dialog(
                    "Broken",
                    List.of(new DialogAction("Cancel", Role.DISMISSIVE, () -> {
                        throw new IllegalStateException("the handler's own bug");
                    })),
                    null));

            escape();
            assertThrows(IllegalStateException.class, host::tick);

            assertFalse(overlay.isAttached(), "a throwing handler left a scrim over the window");
        }

        @Test
        @DisplayName("and a handler that removes it as well is harmless")
        void handlerRemovesToo() {
            var holder = new ArrayList<Overlay>(1);
            holder.add(show(new Dialog(
                    "Tidy",
                    List.of(new DialogAction(
                            "Cancel", Role.DISMISSIVE, () -> holder.getFirst().remove())),
                    null)));
            show(dialog("Other"));

            Described.first(tree, DialogPanel.class)
                    .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null));
            host.tick();
            tree.flush();

            assertEquals(1, host.overlays.get().size(), "removing twice took the other dialog with it");
        }
    }

    @Nested
    @DisplayName("closed by the application")
    class Dismissed {

        @Test
        @DisplayName("dismiss() fades the dialog and then removes it, pressing nothing")
        void fades() {
            var overlay = show(dialog("Signing in"));

            overlay.dismiss();
            tree.flush();

            assertTrue(overlay.isAttached(), "removed at once, so it never faded");
            assertEquals(1, Described.counting(tree, "dialog"), "the panel stopped being drawn before its fade");
            assertTrue(host.scheduledDelays().contains(Duration.ofMillis(160)), "" + host.scheduledDelays());

            host.tick();
            tree.flush();

            assertFalse(overlay.isAttached());
            assertTrue(pressed.isEmpty(), "dismissing pressed " + pressed);
        }

        @Test
        @DisplayName("dismissing twice is one exit")
        void twice() {
            var overlay = show(dialog("Signing in"));

            overlay.dismiss();
            overlay.dismiss();

            assertEquals(
                    1,
                    host.scheduledDelays().stream()
                            .filter(Duration.ofMillis(160)::equals)
                            .count());
        }

        /// Nothing has been drawn, so there is nothing to fade.
        @Test
        @DisplayName("a dialog dismissed before it was ever built goes at once")
        void beforeBuilt() {
            var overlay = Dialogs.show(host, dialog("Too quick"));

            overlay.dismiss();

            assertFalse(overlay.isAttached());
        }

        @Test
        @DisplayName("remove() still takes it away at once")
        void removeIsImmediate() {
            var overlay = show(dialog("Gone"));

            overlay.remove();
            tree.flush();

            assertFalse(overlay.isAttached());
            assertEquals(0, Described.counting(tree, "dialog"));
        }
    }

    @Nested
    @DisplayName("a dialog taller than the window")
    class Tall {

        private Dialog tall() {
            var lines = new ArrayList<Widget>();
            for (var i = 0; i < 40; i++) {
                lines.add(new Text("Line " + i + " of a dialog with far too much to say."));
            }
            return dialog("Terms", lines.toArray(Widget[]::new));
        }

        /// The scrim's padding is the margin: 24 above and 24 below.
        @Test
        @DisplayName("is no taller than the window less its margins, and keeps its buttons on screen")
        void capped() {
            show(tall());

            var regions = regions();
            var panel = regionOf(regions, "dialog");
            var actions = regionOf(regions, "dialog-actions");
            assertNotNull(panel);
            assertNotNull(actions);

            assertTrue(panel.height() <= HEIGHT - 48 + 0.5f, "the panel is " + panel.height() + " tall");
            assertTrue(panel.top() >= 24 - 0.5f, "the panel starts at " + panel.top());
            assertTrue(
                    actions.top() + actions.height() <= HEIGHT - 24 + 0.5f,
                    "the action bar ends at " + (actions.top() + actions.height()) + ", off the window");
        }

        @Test
        @DisplayName("scrolls its body, which is shorter than what it holds")
        void bodyScrolls() {
            show(tall());

            var regions = regions();
            var viewport = regionOf(regions, "scroll");
            var body = regionOf(regions, "dialog-body");
            assertNotNull(viewport);
            assertNotNull(body);

            assertTrue(
                    viewport.height() < body.height(),
                    "the viewport is " + viewport.height() + " and the body " + body.height());
        }

        /// The body's viewport is a Tab stop only while there is something
        /// to scroll, which a router learns after a frame and this test says
        /// by hand.
        @Test
        @DisplayName("its body is a Tab stop while it overflows, and not while it fits")
        void tabStopFollowsOverflow() {
            show(dialog("Short", new Text("Fits.")));

            measureViewport(new Extent(300, 100), new Extent(300, 100));
            assertFalse(viewport().isFocusable(), "a body that fits is a Tab stop");

            measureViewport(new Extent(300, 100), new Extent(300, 400));
            assertTrue(viewport().isFocusable(), "a body that scrolls cannot be reached from the keyboard");
        }

        private Handles viewport() {
            return (Handles) Described.of(tree, Widget.class).stream()
                    .filter(widget ->
                            widget instanceof Styled styled && styled.cssType().equals("scroll"))
                    .findFirst()
                    .orElseThrow();
        }

        private void measureViewport(Extent bounds, Extent content) {
            ((Measured) viewport()).measured(bounds, content);
            tree.flush();
        }
    }
}
