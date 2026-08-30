package io.github.digitalsmile.goldberry.widgets.overlay.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.registry.ActionRegistry;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.DialogAction.Role;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `dialog` — §7's modal.
///
/// The focus trap is **not** here: it is the router's and is tested in `:core`'s
/// `FocusTrapTest` against a real element tree, because the mechanism has to hold
/// for whatever declares itself modal next. What is here is everything a dialog
/// decides for itself — the roles, the order they land in, the two keys, and the
/// closing animation that runs before the application is told ([ADR-0176]).
class DialogTest {

    private TestHost host;
    private List<String> pressed;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        host = new TestHost();
        pressed = new ArrayList<>();
    }

    private DialogAction action(String label, Role role) {
        return new DialogAction(label, role, () -> pressed.add(label));
    }

    private ElementTree open(DialogAction... actions) {
        var children = new ArrayList<io.github.digitalsmile.goldberry.widget.Widget>();
        children.add(new Text("Your draft has not been saved."));
        children.addAll(List.of(actions));
        return new ElementTree(
                new Dialog(
                        "Unsaved changes",
                        children,
                        io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE.id("unsaved")),
                host);
    }

    private static PointerEvent click() {
        return new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, null);
    }

    private static KeyEvent press(Key key) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
    }

    @Nested
    @DisplayName("the panel")
    class Panel {

        @Test
        @DisplayName("a dialog is a scrim, a panel, and three parts inside it")
        void structure() {
            var tree = open(action("Cancel", Role.DISMISSIVE));

            assertEquals(1, Described.counting(tree, "dialog-scrim"));
            assertEquals(1, Described.counting(tree, "dialog"));
            assertEquals(1, Described.counting(tree, "dialog-title"));
            assertEquals(1, Described.counting(tree, "dialog-body"));
            assertEquals(1, Described.counting(tree, "dialog-actions"));
        }

        /// A titleless dialog is a panel with no heading rather than one with an
        /// empty heading — `group-box`'s rule, and for its reason: a rule across
        /// the top with nothing above it is a line nobody asked for.
        @Test
        @DisplayName("no title means no title node, and no actions means no bar")
        void absentParts() {
            var tree = new ElementTree(new Dialog(null, new Text("Just this.")), host);

            assertEquals(0, Described.counting(tree, "dialog-title"));
            assertEquals(0, Described.counting(tree, "dialog-actions"));
            assertEquals(1, Described.counting(tree, "dialog-body"));
        }

        /// The trap, from the widget's side. That it *works* is `FocusTrapTest`'s.
        @Test
        @DisplayName("the panel declares itself modal and takes no focus of its own")
        void modality() {
            var tree = open();
            var panel = Described.first(tree, DialogPanel.class);

            assertTrue(panel.isModal(), "nothing traps the keyboard");
            assertFalse(panel.isFocusable(), "the panel is a Tab stop with nothing to do on it");
        }

        @Test
        @DisplayName("the actions are not in the body")
        void actionsAreNotContent() {
            var dialog = new Dialog("t", List.of(new Text("body"), action("Cancel", Role.DISMISSIVE)), null);

            assertEquals(1, dialog.content().size());
            assertEquals(1, dialog.actions().size());
            assertEquals(2, dialog.children().size(), "children is what the document wrote");
        }
    }

    @Nested
    @DisplayName("the action bar")
    class Actions {

        /// §7's "platform button order … applied by the dialog's action bar
        /// automatically". The canonical order ships affirmative-right; a theme
        /// reverses the bar.
        @Test
        @DisplayName("neutral, then dismissive, then affirmative — whatever order they were written")
        void canonicalOrder() {
            var tree = open(
                    action("Save", Role.AFFIRMATIVE),
                    action("Don't save", Role.NEUTRAL),
                    action("Cancel", Role.DISMISSIVE));

            var labels =
                    Described.of(tree, Button.class).stream().map(Button::label).toList();
            assertEquals(List.of("Don't save", "Cancel", "Save"), labels);
        }

        /// The class follows from the role, which is the point of the role being
        /// a value: the affirmative button looks the same in every dialog in the
        /// application and nobody has to remember to say so.
        @Test
        @DisplayName("the role decides the class, not the author")
        void roleDecidesTheClass() {
            var tree = open(
                    action("Save", Role.AFFIRMATIVE),
                    action("Don't save", Role.NEUTRAL),
                    action("Cancel", Role.DISMISSIVE));
            var buttons = Described.of(tree, Button.class);

            assertEquals(List.of("ghost"), List.copyOf(buttons.get(0).classes()));
            assertTrue(buttons.get(1).classes().isEmpty(), "a dismissive button is a button");
            assertEquals(List.of("primary"), List.copyOf(buttons.get(2).classes()));
        }

        /// Refused where every other document error is refused — when the thing
        /// is built — rather than on the frame somebody presses Enter.
        @Test
        @DisplayName("two default buttons is a coin toss and is refused")
        void twoAffirmatives() {
            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Dialog("t", action("Save", Role.AFFIRMATIVE), action("Save as", Role.AFFIRMATIVE)));

            assertTrue(thrown.getMessage().contains("Enter"), thrown.getMessage());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Dialog("t", action("No", Role.DISMISSIVE), action("Cancel", Role.DISMISSIVE)));
        }

        @Test
        @DisplayName("a misspelt role is refused, and an absent one is neutral")
        void roleParsing() {
            assertEquals(Role.NEUTRAL, Role.of(null));
            assertEquals(Role.AFFIRMATIVE, Role.of("Affirmative"));
            assertThrows(IllegalArgumentException.class, () -> Role.of("default"));
        }
    }

    @Nested
    @DisplayName("the two keys")
    class Keys {

        @Test
        @DisplayName("Escape presses the dismissive button and Enter the affirmative")
        void escapeAndEnter() {
            var tree = open(action("Save", Role.AFFIRMATIVE), action("Cancel", Role.DISMISSIVE));
            var panel = Described.first(tree, DialogPanel.class);

            panel.onKey(press(Key.ESCAPE));
            host.tick();
            assertEquals(List.of("Cancel"), pressed);

            panel.onKey(press(Key.ENTER));
            host.tick();
            // The dialog is already closing, so the second key does nothing --
            // which is the same rule that stops two presses being two answers.
            assertEquals(List.of("Cancel"), pressed);
        }

        /// A dialog that must be answered has no dismissive action, and then
        /// neither `Esc` nor the scrim can answer it for the user.
        @Test
        @DisplayName("with no dismissive action, Escape does nothing")
        void noWayOut() {
            var tree = open(action("Delete", Role.AFFIRMATIVE));

            Described.first(tree, DialogPanel.class).onKey(press(Key.ESCAPE));

            // The only timer is the zero-delay one that asks for focus; nothing
            // scheduled the 160ms exit.
            assertFalse(
                    host.scheduledDelays().contains(java.time.Duration.ofMillis(160)),
                    "something started closing: " + host.scheduledDelays());
            assertTrue(pressed.isEmpty());
        }

        /// The keys are on the **bubble** phase, so a control inside that means
        /// something by one keeps it. This is the assertion that the dialog is
        /// not reaching for `onKeyCapture`.
        @Test
        @DisplayName("a repeat and a modified key are left alone")
        void notEveryKey() {
            var tree = open(action("Cancel", Role.DISMISSIVE));
            var panel = Described.first(tree, DialogPanel.class);

            panel.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, true, null));
            panel.onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED,
                    Key.ENTER,
                    Modifiers.of(io.github.digitalsmile.goldberry.input.key.Mod.CTRL),
                    false,
                    null));

            assertTrue(pressed.isEmpty(), "a repeat or a Ctrl+Enter answered the dialog");
        }
    }

    @Nested
    @DisplayName("the scrim")
    class Scrim {

        @Test
        @DisplayName("a press on the veil is Escape")
        void pressIsEscape() {
            var tree = open(action("Cancel", Role.DISMISSIVE));

            Described.first(tree, DialogScrim.class).onPointer(click());
            host.tick();

            assertEquals(List.of("Cancel"), pressed);
        }

        /// The scrim is the panel's parent and dispatch bubbles, so without this
        /// every click on a dialog's own content would also read as a click
        /// outside it.
        @Test
        @DisplayName("a press on the panel is not a press on the veil")
        void panelSwallowsItsOwnPresses() {
            var tree = open(action("Cancel", Role.DISMISSIVE));
            var event = click();

            Described.first(tree, DialogPanel.class).onPointer(event);

            assertTrue(event.isConsumed(), "the press reached the scrim behind the panel");
            assertTrue(pressed.isEmpty());
        }
    }

    @Nested
    @DisplayName("closing")
    class Closing {

        /// §1.7: "the element stays mounted through `closing` … removal fires on
        /// animation end". The application's handler is what removes the overlay,
        /// so calling it late is what buys the animation.
        @Test
        @DisplayName("the panel fades before the application is told")
        void fadesFirst() {
            var tree = open(action("Cancel", Role.DISMISSIVE));

            Described.first(tree, DialogPanel.class).onKey(press(Key.ESCAPE));
            tree.flush();

            assertTrue(pressed.isEmpty(), "told before the fade had run");
            assertTrue(host.hasPendingTimer(), "nothing is going to end the fade");
            assertEquals(1, Described.counting(tree, "dialog"), "it stopped being described");

            host.tick();
            assertEquals(List.of("Cancel"), pressed);
        }

        /// **The bug this test exists for.** `isAnimating` answered
        /// `!closing && phase.isRunning()`, so the instant a close started the
        /// dialog stopped asking for frames — and a widget nobody asks to repaint
        /// does not fade: it sits still for 160ms and then vanishes.
        ///
        /// It passed every golden, because a golden drives `render` by hand and
        /// never asks whether the frame loop would have. Two flags were doing one
        /// job: `closing` means *input is off*, and only a separate "there is
        /// nothing left to draw" may switch the animation off.
        @Test
        @DisplayName("the panel and the veil keep asking for frames while they close")
        void closingIsAnimated() {
            var tree = open(action("Cancel", Role.DISMISSIVE));
            Described.first(tree, DialogPanel.class).onKey(press(Key.ESCAPE));
            tree.flush();

            assertTrue(
                    Described.first(tree, DialogPanel.class).isAnimating(),
                    "the panel stopped asking for frames, so the fade never draws");
            assertTrue(Described.first(tree, DialogScrim.class).isAnimating(), "the veil stopped asking for frames");
        }

        /// And it stops when the fade is over, even if the application does not
        /// remove the overlay — `message`'s rule, for `message`'s reason: a
        /// `LEAVING` phase never settles itself, so something has to end it or the
        /// loop spins for ever.
        @Test
        @DisplayName("and stop when it is over, whatever the application does")
        void closedStopsAsking() {
            var tree = open(action("Cancel", Role.DISMISSIVE));
            Described.first(tree, DialogPanel.class).onKey(press(Key.ESCAPE));
            tree.flush();

            host.tick();
            tree.flush();

            assertFalse(
                    Described.first(tree, DialogScrim.class).isAnimating(),
                    "the veil is still asking for frames after it has gone");
            assertEquals(0, Described.counting(tree, "dialog"), "a dialog that has closed is still describing a panel");
        }

        /// §3: "out: base, reverse" — 160ms, against 240 to open.
        @Test
        @DisplayName("the exit is `base` where the entrance is `overlay`")
        void exitIsShorter() {
            open(action("Cancel", Role.DISMISSIVE));
            var tree = open(action("Cancel", Role.DISMISSIVE));
            Described.first(tree, DialogPanel.class).onKey(press(Key.ESCAPE));

            assertTrue(
                    host.scheduledDelays().contains(java.time.Duration.ofMillis(160)),
                    "the delays were " + host.scheduledDelays());
        }

        /// §1.7: "input is disabled the instant closing starts (no ghost clicks)".
        @Test
        @DisplayName("a second answer during the fade is not a second answer")
        void oneAnswerOnly() {
            var tree = open(action("Save", Role.AFFIRMATIVE), action("Cancel", Role.DISMISSIVE));
            var panel = Described.first(tree, DialogPanel.class);

            panel.onKey(press(Key.ENTER));
            tree.flush();
            Described.first(tree, DialogScrim.class).onPointer(click());
            host.tick();
            host.tick();

            assertEquals(List.of("Save"), pressed, "the dialog was answered twice");
        }

        @Test
        @DisplayName("unmounting mid-fade cancels the timer")
        void unmountingCancels() {
            var tree = open(action("Cancel", Role.DISMISSIVE));
            Described.first(tree, DialogPanel.class).onKey(press(Key.ESCAPE));
            tree.flush();

            tree.unmount();

            assertTrue(host.allTimersCancelled(), "a timer outlived the dialog that made it");
        }
    }

    @Nested
    @DisplayName("opening")
    class Opening {

        /// A dialog that opened without the keyboard would make a keyboard user
        /// Tab in from wherever they were — through a window they cannot reach,
        /// because the trap redirects every attempt back in.
        @Test
        @DisplayName("it asks the window to put the keyboard in it, once")
        void asksForFocus() {
            var tree = open(action("Cancel", Role.DISMISSIVE));

            // Scheduled rather than called: the elements it is asking about are
            // being described as it asks.
            assertTrue(host.hasPendingTimer());
            host.tick();
            assertEquals(List.of("unsaved"), host.focusRequests());

            tree.flush();
            assertEquals(1, host.focusRequests().size(), "it asked again on a rebuild");
        }

        @Test
        @DisplayName("Dialogs.show names an unnamed dialog, so it can be focused")
        void showNamesIt() {
            var overlay = Dialogs.show(host, new Dialog("Delete this?", new Text("x")));

            assertInstanceOf(Dialog.class, overlay.widget());
            assertEquals(
                    Dialogs.DEFAULT_ID, ((Dialog) overlay.widget()).attributes().id());
            assertTrue(overlay.isFilling(), "a dialog that does not cover the window is not modal");
        }

        @Test
        @DisplayName("a dialog that names itself keeps its name")
        void showKeepsAName() {
            var overlay = Dialogs.show(host, new Dialog("Delete this?", new Text("x")).id("confirm"));

            assertEquals("confirm", ((Dialog) overlay.widget()).attributes().id());
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a dialog inflates with its title, its content and its roles")
        void inflates() {
            var actions = ActionRegistry.strict()
                    .bind("app.stay", () -> pressed.add("stay"))
                    .bind("app.discard", () -> pressed.add("discard"));

            var widget = Widgets.inflater(actions).inflate(KdlParser.parse("""
                    dialog id="unsaved" title="Unsaved changes" {
                        text "Your draft has not been saved."
                        action role="dismissive" press="app.stay" "Keep editing"
                        action role="affirmative" press="app.discard" "Discard"
                    }
                    """).getFirst());
            var dialog = assertInstanceOf(Dialog.class, widget);

            assertEquals("Unsaved changes", dialog.title());
            assertEquals(1, dialog.content().size());
            assertEquals(2, dialog.actions().size());
            assertEquals(Role.DISMISSIVE, dialog.actions().get(0).role());
            assertEquals(Role.AFFIRMATIVE, dialog.actions().get(1).role());

            dialog.actions().get(1).onPress().run();
            assertEquals(List.of("discard"), pressed);
        }
    }
}
