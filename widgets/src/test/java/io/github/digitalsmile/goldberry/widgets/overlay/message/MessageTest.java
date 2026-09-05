package io.github.digitalsmile.goldberry.widgets.overlay.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.bind.registry.ActionRegistry;
import io.github.digitalsmile.goldberry.bind.registry.BindingRegistry;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.panel.Described;

/// `message` — §7's inline banner.
///
/// Three things are worth asserting here and none of them is the drawing, which
/// is `MessageGoldenTest`'s job:
///
///   - **the kind reaches both the glyph and the class**, because §1.2 forbids
///     colour as the only carrier of meaning and a kind that set only the hue
///     would be exactly that mistake;
///   - **the way out exists only when somebody is listening**, and answers the
///     keyboard as well as the pointer — a banner with a mouse-only dismiss is
///     a banner a keyboard user cannot close;
///   - **the error summary is absent rather than empty** when nothing is wrong.
class MessageTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Nested
    @DisplayName("the kind")
    class Kinds {

        /// The assertion §1.2 asks for: four kinds, four *different* glyphs. A
        /// mapping that returned the same mark for two of them would leave the
        /// colour carrying the difference alone.
        @Test
        @DisplayName("every kind draws a glyph of its own")
        void everyKindHasItsOwnGlyph() {
            var glyphs = java.util.Arrays.stream(Message.Kind.values())
                    .map(Message.Kind::glyph)
                    .distinct()
                    .toList();

            assertEquals(
                    Message.Kind.values().length,
                    glyphs.size(),
                    "two kinds share a glyph, so the hue is carrying the difference: " + glyphs);
            assertEquals(
                    Box.Mark.Kind.TRIANGLE_ALERT,
                    Message.Kind.WARNING.glyph(),
                    "warning is the one that is not a circle, which is what tells it"
                            + " from danger with the colour removed");
        }

        /// So `message.danger` selects without the document writing the class.
        @Test
        @DisplayName("the kind puts its own class on the node")
        void theKindIsAlsoAClass() {
            var tree = new ElementTree(new Message(Message.Kind.DANGER, "Nope"));
            var box = Described.first(tree, MessageBox.class);

            assertTrue(box.classes().contains("danger"), "classes were " + box.classes());
            assertEquals("message", box.cssType());
        }

        /// A document that says nothing still builds; one that says something
        /// wrong does not, because that is a document meaning something it
        /// cannot have.
        @Test
        @DisplayName("an absent kind is info and a misspelt one is refused")
        void absentAndMisspelt() {
            assertEquals(Message.Kind.INFO, Message.Kind.of(null));
            assertEquals(Message.Kind.INFO, Message.Kind.of("  "));
            assertEquals(Message.Kind.WARNING, Message.Kind.of("Warning"));

            var thrown = assertThrows(IllegalArgumentException.class, () -> Message.Kind.of("dangerous"));
            assertTrue(thrown.getMessage().contains("dangerous"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("the parts")
    class Parts {

        @Test
        @DisplayName("a plain banner is a glyph and its words, and nothing else")
        void plain() {
            var tree = new ElementTree(new Message(Message.Kind.INFO, "Trial ends Friday."));

            assertEquals(1, Described.counting(tree, "message-icon"));
            assertEquals(1, Described.counting(tree, "message-body"));
            assertEquals(
                    0,
                    Described.counting(tree, "message-actions"),
                    "an action row with nothing in it is a gap nobody asked for");
            assertEquals(
                    0,
                    Described.counting(tree, "message-dismiss"),
                    "a × that tells nobody anything is an affordance that lies");
        }

        /// The author's own widgets, not a node this widget invents: §7 says
        /// "action links" and `button class="ghost"` already is one.
        @Test
        @DisplayName("actions are the author's widgets, in a row of their own")
        void actions() {
            var tree =
                    new ElementTree(new Message(Message.Kind.WARNING, "Session ending.").actions(new Button("Stay")));

            assertEquals(1, Described.counting(tree, "message-actions"));
            assertEquals(1, Described.of(tree, Button.class).size());
        }

        @Test
        @DisplayName("the × is there only when somebody is listening")
        void dismissIsOptIn() {
            assertFalse(new Message(Message.Kind.INFO, "x").isDismissable());
            assertTrue(new Message(Message.Kind.INFO, "x").dismiss(() -> {}).isDismissable());

            var tree = new ElementTree(new Message(Message.Kind.INFO, "x").dismiss(() -> {}));
            assertEquals(1, Described.counting(tree, "message-dismiss"));
        }
    }

    @Nested
    @DisplayName("the way out")
    class Dismissing {

        private MessageDismiss dismissIn(Runnable listener) {
            var tree = new ElementTree(new Message(Message.Kind.DANGER, "x").dismiss(listener));
            return Described.first(tree, MessageDismiss.class);
        }

        @Test
        @DisplayName("a click dismisses, and is consumed so nothing behind it also fires")
        void click() {
            var told = new boolean[1];
            var event = new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, null);

            dismissIn(() -> told[0] = true).onPointer(event);

            assertTrue(told[0]);
            assertTrue(event.isConsumed());
        }

        /// The whole reason this part is focusable where `tab-close` is not: a
        /// banner is not a focus scope, so without a key here there is no way to
        /// dismiss one at all.
        @Test
        @DisplayName("Space and Enter dismiss, and the × is a tab stop")
        void keyboard() {
            assertTrue(dismissIn(() -> {}).isFocusable());

            for (var key : List.of(Key.SPACE, Key.ENTER)) {
                var told = new boolean[1];
                var event = new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);

                dismissIn(() -> told[0] = true).onKey(event);

                assertTrue(told[0], key + " did not dismiss");
                assertTrue(event.isConsumed(), key + " was not consumed");
            }
        }

        @Test
        @DisplayName("a repeat is not a second dismissal")
        void repeatsAreIgnored() {
            var told = new boolean[1];

            dismissIn(() -> told[0] = true)
                    .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, true, null));

            assertFalse(told[0], "holding Space down dismissed the banner twice");
        }
    }

    /// §3's "out: `opacity` fast", and the order that makes it possible at all.
    ///
    /// A banner has no owner holding it, so there is nothing to keep drawing one
    /// the application has already dropped. The way round it is that the × does
    /// not tell the application and hope: it fades **first** and tells it after,
    /// while the description is still in the tree because nobody has asked for it
    /// to go ([ADR-0175]).
    @Nested
    @DisplayName("the departure")
    class Departing {

        private TestHost host;

        @BeforeEach
        void withAWindow() {
            host = new TestHost();
        }

        private MessageDismiss dismissIn(ElementTree tree) {
            return Described.first(tree, MessageDismiss.class);
        }

        private ElementTree banner(Runnable onDismiss) {
            return new ElementTree(new Message(Message.Kind.INFO, "Going").dismiss(onDismiss), host);
        }

        @Test
        @DisplayName("the × fades the banner and does not tell the application yet")
        void fadesBeforeTelling() {
            var told = new boolean[1];
            var tree = banner(() -> told[0] = true);

            dismissIn(tree).onPointer(click());
            tree.flush();

            assertFalse(told[0], "the application was told before the fade had run");
            assertTrue(host.hasPendingTimer(), "nothing is going to end the fade");
            assertEquals(
                    1,
                    Described.counting(tree, "message"),
                    "the banner stopped being described, so there was nothing to fade");
        }

        /// 100ms, which is §1.7's `fast` — the duration §3 names for this exit.
        /// The arrival is `base`, and a dismissal that took as long as an arrival
        /// reads as the control arguing.
        @Test
        @DisplayName("the fade is `fast`, not `base`")
        void fadesFast() {
            dismissIn(banner(() -> {})).onPointer(click());

            assertEquals(List.of(java.time.Duration.ofMillis(100)), host.scheduledDelays());
        }

        @Test
        @DisplayName("when the fade ends the application is told, and the banner is gone")
        void tellsWhenTheFadeEnds() {
            var told = new boolean[1];
            var tree = banner(() -> told[0] = true);
            dismissIn(tree).onPointer(click());
            tree.flush();

            host.tick();
            tree.flush();

            assertTrue(told[0], "the fade ended and nobody was told");
            // The application is what removes the banner, and this test's
            // application does not -- so what is left is the case the record is
            // explicit about: it stays gone rather than springing back.
            assertEquals(
                    0, Described.counting(tree, "message-dismiss"), "a departed banner still has a × in the hit test");
        }

        @Test
        @DisplayName("a second press during the fade is not a second dismissal")
        void pressingTwice() {
            var told = new int[1];
            var tree = banner(() -> told[0]++);
            var cross = dismissIn(tree);

            cross.onPointer(click());
            tree.flush();
            cross.onPointer(click());
            tree.flush();
            host.tick();
            host.tick();

            assertEquals(1, told[0], "two timers, so the application was told twice");
        }

        /// A banner taken away by something *else* while it was fading — a
        /// "Clear all" button, a screen change, a window closing — would leave a
        /// timer pointing at a state whose tree is gone.
        @Test
        @DisplayName("unmounting mid-fade cancels the timer")
        void unmountingCancels() {
            var tree = banner(() -> {});
            dismissIn(tree).onPointer(click());
            tree.flush();

            tree.unmount();

            assertTrue(host.allTimersCancelled(), "a timer outlived the banner that made it");
        }

        /// With no window there is nothing to schedule against, which is every
        /// widget test that does not ask for one and every golden image. A banner
        /// that could not animate its exit must still have one.
        @Test
        @DisplayName("no window means an instant dismissal rather than none")
        void noHostDismissesAtOnce() {
            var told = new boolean[1];
            var tree = new ElementTree(new Message(Message.Kind.INFO, "Going").dismiss(() -> told[0] = true));

            Described.first(tree, MessageDismiss.class).onPointer(click());

            assertTrue(told[0]);
        }

        /// §1.7: a reader who has asked not to be animated at gets no fade —
        /// **and no wait either**. Drawing the banner at full strength for a
        /// hundred milliseconds and then removing it would be the animation's
        /// delay with the animation taken out, which is the worst of both.
        ///
        /// The preference is a property of a *frame* and the code that acts on it
        /// runs in a pointer handler, so the value comes back from `render` — the
        /// arrangement `carousel` uses for the same reason.
        @Test
        @DisplayName("reduced motion dismisses at once rather than waiting out a fade")
        void reducedMotionSkipsTheFade() {
            var told = new boolean[1];
            var tree = banner(() -> told[0] = true);
            var renderer =
                    new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
            renderer.reducedMotion(true);
            // The frame that tells the widget what the preference is.
            renderer.render(tree);

            dismissIn(tree).onPointer(click());

            assertTrue(told[0], "the application was not told");
            assertFalse(host.hasPendingTimer(), "a fade was scheduled under reduced motion");
        }

        private PointerEvent click() {
            return new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, null);
        }
    }

    @Nested
    @DisplayName("the error summary")
    class Summary {

        /// §4 asks for the summary; ADR-0169 built the register and nothing drew
        /// it. This is the drawing, and it is a factory rather than something
        /// `form` emits because a form does not know where its summary belongs.
        @Test
        @DisplayName("nothing wrong is no banner, not an empty one")
        void empty() {
            assertTrue(Message.summary(List.of()).isEmpty());
            assertTrue(
                    Message.summary(java.util.Arrays.asList("", "   ")).isEmpty(),
                    "a validator that returned blank has said nothing");
        }

        @Test
        @DisplayName("every failure is a line of one danger banner")
        void oneBannerManyLines() {
            var summary = Message.summary(List.of("Name is required", "Port must be a number"))
                    .orElseThrow();

            assertEquals(Message.Kind.DANGER, summary.kind());
            assertEquals(
                    "Name is required\nPort must be a number",
                    summary.text(),
                    "four failures should be four lines of one banner, not four banners");
        }
    }

    @Nested
    @DisplayName("bound to a value")
    class Bound {

        /// §9's `bind=`, and the reason it took until [ADR-0227]: a banner bound
        /// to an empty string would have been *present and empty* — a bordered
        /// box with 12px of padding saying nothing — and §8's subset has no
        /// `display`, so no widget could take itself out of a layout. The element
        /// tree has the word now.
        @Test
        @DisplayName("a bound banner says what the value says")
        void readsTheValue() {
            var error = Property.of("The port is in use.");
            var tree = new ElementTree(new Message(Message.Kind.DANGER, "").bound(error), new TestHost());

            assertEquals(
                    "The port is in use.",
                    Described.first(tree, MessageBox.class).text());
        }

        @Test
        @DisplayName("a bound banner with nothing to say is not there at all")
        void emptyIsAbsent() {
            var error = Property.of("");
            var tree = new ElementTree(new Message(Message.Kind.DANGER, "").bound(error), new TestHost());

            assertTrue(
                    Described.of(tree, MessageBox.class).isEmpty(),
                    "an empty banner is a bordered box with 12px of padding saying nothing");
        }

        /// The whole point of doing this with a widget rather than by describing
        /// the banner away from outside: the element is still there, so the value
        /// coming back is a value change and not a node being rebuilt.
        @Test
        @DisplayName("it comes back when the value does")
        void returnsWithTheValue() {
            var error = Property.of("");
            var tree = new ElementTree(new Message(Message.Kind.DANGER, "").bound(error), new TestHost());
            assertTrue(Described.of(tree, MessageBox.class).isEmpty());

            error.set("Could not save.");
            tree.flush();

            assertEquals(
                    "Could not save.", Described.first(tree, MessageBox.class).text());
        }

        /// **Not** a fallback for a blank value, only for no binding at all. An
        /// application whose error property is empty means "there is no error",
        /// and showing the document's placeholder words instead would be a banner
        /// reporting a problem that has gone away.
        @Test
        @DisplayName("the words in the document are the fallback for no binding, not for a blank one")
        void textIsNotAFallbackForBlank() {
            var unbound = new Message(Message.Kind.INFO, "Written in the document");
            assertEquals("Written in the document", unbound.resolved());

            var bound = unbound.bound(Property.of(""));
            assertEquals("", bound.resolved());
        }

        @Test
        @DisplayName("the element follows the binding, exactly as every other bound widget does")
        void subscribes() {
            var error = Property.of("x");
            new ElementTree(new Message(Message.Kind.DANGER, "").bound(error), new TestHost());

            assertEquals(1, error.listenerCount());
        }

        @Test
        @DisplayName("a nothing is what it describes, so the tree still holds the element")
        void describesNothing() {
            var tree = new ElementTree(new Message(Message.Kind.DANGER, "").bound(Property.of("")), new TestHost());

            assertTrue(
                    Described.of(tree, MessageBox.class).isEmpty(),
                    "the banner drew itself when it had nothing to say");
            assertSame(
                    Widget.nothing(),
                    tree.root().children().getFirst().widget(),
                    "the element under the message should describe nothing");
        }

        @Test
        @DisplayName("markup names a path and the registry resolves it")
        void fromMarkup() {
            var error = Property.of("Port in use.");
            var bindings = BindingRegistry.strict().bind("form.error", error);

            var message = assertInstanceOf(
                    Message.class,
                    Widgets.inflater(ActionRegistry.none(), Icons.none(), bindings)
                            .inflateAll(KdlParser.parse("message kind=\"danger\" bind=\"form.error\""))
                            .getFirst());

            assertEquals("Port in use.", message.resolved());
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a message inflates with its kind, its words and its links")
        void inflates() {
            var widget = Widgets.inflater()
                    .inflate(KdlParser.parse("message kind=\"warning\" \"Session ending.\" {"
                                    + " button class=\"ghost\" \"Stay\" }")
                            .getFirst());
            var message = assertInstanceOf(Message.class, widget);

            assertEquals(Message.Kind.WARNING, message.kind());
            assertEquals("Session ending.", message.text());
            assertEquals(1, message.actions().size(), "the children are the action links");
            assertFalse(message.isDismissable(), "nothing named an action to tell");
        }

        /// `dismiss=` names an action rather than being a boolean, because
        /// nothing in a document could take the banner away: what put it there
        /// is the application's own state.
        @Test
        @DisplayName("dismiss= names who to tell")
        void dismissNamesAnAction() {
            var told = new boolean[1];
            var actions = ActionRegistry.strict().bind("clear", () -> told[0] = true);

            var message = assertInstanceOf(
                    Message.class,
                    Widgets.inflater(actions)
                            .inflate(KdlParser.parse("message kind=\"danger\" dismiss=\"clear\" \"Could not save.\"")
                                    .getFirst()));

            assertTrue(message.isDismissable());
            message.onDismiss().run();
            assertTrue(told[0], "the × was wired to nothing");
        }
    }
}
