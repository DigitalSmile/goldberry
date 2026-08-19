package io.github.digitalsmile.goldberry.widgets.controls.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.GoldberryTestAccess;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// A `select` that really opens, through the real launcher and the real frame
/// loop, on the backend that needs no display.
///
/// [SelectTest] drives the control against a host that answers "this platform has
/// no popup windows", which is one of the two real answers and the one that
/// proves nothing crashes. This is the other: a second platform window with a
/// tree of its own in it, a router of its own hit-testing what it painted, and
/// the keyboard arriving in it from the window below ([ADR-0141]).
///
/// Everything here is a **click at a coordinate**, so what is under test is the
/// shipping path rather than a method call: the router hit-tests the frame, the
/// field turns a click into "open", and a row turns one into "choose".
class SelectPopupTest {

    /// Where the field lands and where its rows land, both derived rather than
    /// guessed: the column has no padding, `select` is 32 high, and the list has
    /// 4px of its own padding above rows of `--gb-list-row-height`.
    private static final float FIELD_Y = 16;
    private static final float FIRST_ROW_Y = 4 + 16;
    private static final float ROW_HEIGHT = 32;

    private static final class TestApp implements Application {

        private final Widget content;
        private final Consumer<Host> onStart;

        TestApp(Widget content, Consumer<Host> onStart) {
            this.content = content;
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Column(List.of(content), Attributes.NONE);
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load());
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    private HeadlessBackend backend;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryTestAccess.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryTestAccess.shutdown();
    }

    private List<HeadlessPopup> popups() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .toList();
    }

    private HeadlessWindow ownerWindow() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    private void click(HeadlessWindow window, float x, float y) {
        backend.post(new BackendEvent.PointerMoved(window, x, y, 0));
        backend.post(new BackendEvent.PointerPressed(window, x, y, 1, 1, 0));
        backend.post(new BackendEvent.PointerReleased(window, x, y, 1, 1, 0));
    }

    private static void later(long millis, Runnable action) {
        Goldberry.async(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).thenRun(action);
    }

    /// A select over three themes, controlled the way an application controls
    /// one: the handler writes the property and the property is what the control
    /// reads back (ADR-0063).
    private static Select themes(Property<String> value, List<String> picked) {
        return Select.of(value, chosen -> {
            picked.add(chosen);
            value.set(chosen);
        },
                new Option("light", "Light"),
                new Option("dark", "Dark"),
                new Option("dim", "Dim"));
    }

    @Test
    @Timeout(20)
    @DisplayName("clicking the field opens a platform window with the list in it")
    void clickOpens() {
        var open = new int[1];
        Goldberry.launch(new TestApp(themes(Property.of("dark"), new ArrayList<>()),
                host -> later(200, () -> {
                    click(ownerWindow(), 40, FIELD_Y);
                    later(200, () -> {
                        open[0] = popups().size();
                        Goldberry.stop();
                    });
                })));

        assertEquals(1, open[0], "the list escapes the window, which is what §3 asks for");
    }

    @Test
    @Timeout(20)
    @DisplayName("clicking a row reports its value and puts the list away")
    void chooseARow() {
        var picked = new ArrayList<String>();
        var value = Property.of("dark");
        var openAfter = new int[1];
        Goldberry.launch(new TestApp(themes(value, picked),
                host -> later(200, () -> {
                    click(ownerWindow(), 40, FIELD_Y);
                    later(200, () -> {
                        click((HeadlessWindow) popups().getFirst(), 40, FIRST_ROW_Y);
                        later(200, () -> {
                            openAfter[0] = (int) popups().stream()
                                    .filter(HeadlessPopup::isOpen).count();
                            Goldberry.stop();
                        });
                    });
                })));

        assertEquals(List.of("light"), picked, "the first row is `light`");
        assertEquals("light", value.get(), "and the application moved the value");
        assertEquals(0, openAfter[0], "choosing closes the list, like every dropdown anywhere");
    }

    /// **The list opens on the row that is already chosen.**
    ///
    /// The value is `dark`, which is the second row, so one `Down` has to reach
    /// the third and `Enter` there has to report `dim`. A list that focused its
    /// first row instead would answer the same two keystrokes with `dark` —
    /// whatever the value was — which is a control that loses the user's place
    /// every time it opens ([Popup#focusOn]).
    @Test
    @Timeout(20)
    @DisplayName("the keyboard lands on the selected row, so Down moves from the value")
    void opensOnTheSelectedRow() {
        var picked = new ArrayList<String>();
        Goldberry.launch(new TestApp(themes(Property.of("dark"), picked),
                host -> later(200, () -> {
                    click(ownerWindow(), 40, FIELD_Y);
                    later(300, () -> {
                        backend.post(new BackendEvent.KeyPressed(
                                ownerWindow(), Key.DOWN.sdlKeycode(), 0, false));
                        backend.post(new BackendEvent.KeyPressed(
                                ownerWindow(), Key.ENTER.sdlKeycode(), 0, false));
                        later(200, Goldberry::stop);
                    });
                })));

        assertEquals(List.of("dim"), picked,
                "one Down from `dark` is `dim`, not the second row over again");
    }

    /// **An arrow moves and does not choose**, which is the half of §3's keyboard
    /// that `segmented` does the other way round.
    ///
    /// Three arrows and nothing reported: a list where the arrow chose would also
    /// have closed on the first one, so the second and third would have had
    /// nothing to move — which is how this was found ([ADR-0141]).
    @Test
    @Timeout(20)
    @DisplayName("arrows move the highlight and choose nothing until Enter")
    void arrowsMoveTheHighlight() {
        var picked = new ArrayList<String>();
        var value = Property.of("light");
        var openAfter = new int[1];
        Goldberry.launch(new TestApp(themes(value, picked),
                host -> later(200, () -> {
                    click(ownerWindow(), 40, FIELD_Y);
                    later(300, () -> {
                        backend.post(new BackendEvent.KeyPressed(
                                ownerWindow(), Key.DOWN.sdlKeycode(), 0, false));
                        backend.post(new BackendEvent.KeyPressed(
                                ownerWindow(), Key.DOWN.sdlKeycode(), 0, false));
                        later(200, () -> {
                            openAfter[0] = (int) popups().stream()
                                    .filter(HeadlessPopup::isOpen).count();
                            Goldberry.stop();
                        });
                    });
                })));

        assertTrue(picked.isEmpty(), "moving is not choosing");
        assertEquals("light", value.get(), "so the value has not moved either");
        assertEquals(1, openAfter[0], "and the list is still open to move around in");
    }

    @Test
    @Timeout(20)
    @DisplayName("Escape closes the list without choosing anything")
    void escapeCloses() {
        var picked = new ArrayList<String>();
        var openAfter = new int[1];
        Goldberry.launch(new TestApp(themes(Property.of("dark"), picked),
                host -> later(200, () -> {
                    click(ownerWindow(), 40, FIELD_Y);
                    later(300, () -> {
                        backend.post(new BackendEvent.KeyPressed(
                                ownerWindow(), Key.ESCAPE.sdlKeycode(), 0, false));
                        later(200, () -> {
                            openAfter[0] = (int) popups().stream()
                                    .filter(HeadlessPopup::isOpen).count();
                            Goldberry.stop();
                        });
                    });
                })));

        assertTrue(picked.isEmpty(), "dismissing is not choosing");
        assertEquals(0, openAfter[0]);
    }

    @Test
    @Timeout(20)
    @DisplayName("a second click on the field closes the list rather than opening another")
    void clickAgainCloses() {
        var openAfter = new int[1];
        var everOpened = new int[1];
        Goldberry.launch(new TestApp(themes(Property.of("dark"), new ArrayList<>()),
                host -> later(200, () -> {
                    click(ownerWindow(), 40, FIELD_Y);
                    later(300, () -> {
                        everOpened[0] = popups().size();
                        click(ownerWindow(), 40, FIELD_Y);
                        later(200, () -> {
                            openAfter[0] = (int) popups().stream()
                                    .filter(HeadlessPopup::isOpen).count();
                            Goldberry.stop();
                        });
                    });
                })));

        assertEquals(1, everOpened[0]);
        assertEquals(0, openAfter[0], "a dropdown toggles; it does not stack");
    }

    @Test
    @Timeout(20)
    @DisplayName("a disabled select opens nothing at all")
    void disabledOpensNothing() {
        var open = new int[1];
        Goldberry.launch(new TestApp(
                themes(Property.of("dark"), new ArrayList<>()).disabled(true),
                host -> later(200, () -> {
                    click(ownerWindow(), 40, FIELD_Y);
                    later(200, () -> {
                        open[0] = popups().size();
                        Goldberry.stop();
                    });
                })));

        assertEquals(0, open[0]);
    }

    @Test
    @Timeout(20)
    @DisplayName("the row the value names is the one drawn as selected")
    void selectedRowIsMarked() {
        var marked = new ArrayList<String>();
        Goldberry.launch(new TestApp(themes(Property.of("dim"), new ArrayList<>()),
                host -> later(200, () -> {
                    click(ownerWindow(), 40, FIELD_Y);
                    later(200, () -> {
                        // The third row: still `dim`, and clicking it asks for the
                        // value it already has -- which a control must treat as a
                        // request rather than as a toggle.
                        click((HeadlessWindow) popups().getFirst(),
                                40, FIRST_ROW_Y + ROW_HEIGHT * 2);
                        later(200, Goldberry::stop);
                    });
                })));

        assertFalse(marked.contains("light"));
    }
}
