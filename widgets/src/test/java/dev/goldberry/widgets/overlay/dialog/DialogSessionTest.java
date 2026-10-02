package dev.goldberry.widgets.overlay.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.Overlay;
import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.key.Key;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// A dialog shown the way an application shows one — `Dialogs.show` on the
/// host its widget was built with — and answered the way a user answers it.
///
/// `DialogTest` covers the panel against a recording host, and the goldens
/// draw a dialog as a child of the scene, because neither had a window to put
/// one over. A session has the overlay layer a window has, so this is the
/// whole route: a button opens the dialog, the scrim takes the window, a
/// press on the dialog's own button starts the exit, and the answer arrives
/// when the exit is over.
///
/// One dialog per test, on purpose.
///
/// Read more:
/// [Driving input](https://goldberry.dev/docs/guide/testing.html#driving-input).
@DisplayName("a dialog in a session")
class DialogSessionTest {

    private static final String SCENE = "#window { width: 480px; height: 320px; padding: 16px }";

    /// What the dialog was answered with, or null while it is open.
    private final AtomicReference<@Nullable String> answer = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A window with one button, which asks before it deletes.
    private record Screen(AtomicReference<@Nullable String> answer) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new State<Screen>() {

                @Override
                public Widget build(BuildContext context) {
                    var host = context.host().orElseThrow();
                    var open = new AtomicReference<@Nullable Overlay>();
                    Runnable ask = () -> open.set(Dialogs.show(
                            host,
                            new Dialog(
                                    "Delete this file?",
                                    new Text("It cannot be brought back."),
                                    new DialogAction("Keep", DialogAction.Role.DISMISSIVE, () -> {
                                        widget().answer().set("kept");
                                        close(open);
                                    }),
                                    new DialogAction("Delete", DialogAction.Role.AFFIRMATIVE, () -> {
                                        widget().answer().set("deleted");
                                        close(open);
                                    }))));
                    return new Column(
                            List.of(new Button("Remove…", ask).withAttributes(Attributes.NONE.id("remove"))),
                            Attributes.NONE.id("window"));
                }
            };
        }

        private static void close(AtomicReference<@Nullable Overlay> open) {
            var overlay = open.get();
            if (overlay != null) {
                overlay.remove();
            }
        }
    }

    private Session open() {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, SCENE));
        return Offscreen.of(480, 320).stylesheets(sheets).session(new Screen(answer));
    }

    @Test
    @DisplayName("is drawn over the window and answered by a click on its own button")
    void answeredByAClick() {
        try (var session = open()) {
            var before = session.frame();
            session.click("remove");
            assertEquals(1, session.overlays().size(), "Dialogs.show put it on the window's overlay layer");
            session.advance(Duration.ofMillis(300));
            assertNotEquals(before.argb(240, 160), session.frame().argb(240, 160), "the scrim dims the window");

            session.click(session.byRole(Role.BUTTON, "Delete").orElseThrow());
            assertNull(answer.get(), "the handler waits for the exit");
            session.advance(Duration.ofMillis(300));

            assertEquals("deleted", answer.get());
            assertEquals(List.of(), session.overlays(), "and the handler's remove() took it off");
        }
    }

    @Test
    @DisplayName("is answered by Escape, which is the dismissive button")
    void answeredByEscape() {
        try (var session = open()) {
            session.click("remove");
            session.advance(Duration.ofMillis(300));
            session.key(Key.ESCAPE);
            session.advance(Duration.ofMillis(300));
            assertEquals("kept", answer.get());
        }
    }

    @Test
    @DisplayName("takes the keyboard when it opens")
    void takesFocus() {
        try (var session = open()) {
            session.click("remove");
            var focused = session.focused().orElseThrow();
            var dialog = session.byId(Dialogs.DEFAULT_ID).orElseThrow();
            assertTrue(inside(focused, dialog), focused + " is not inside the dialog");
        }
    }

    private static boolean inside(Element node, Element ancestor) {
        for (var at = node; at != null; at = at.parent() instanceof Element parent ? parent : null) {
            if (at == ancestor) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("and while it is open, a click on the window under it is refused")
    void isModal() {
        try (var session = open()) {
            session.click("remove");
            session.advance(Duration.ofMillis(300));
            var refused = assertThrows(IllegalStateException.class, () -> session.click("remove"));
            assertTrue(refused.getMessage().contains("dialog"), refused.getMessage());
            assertTrue(session.host().isModal());
        }
    }
}
