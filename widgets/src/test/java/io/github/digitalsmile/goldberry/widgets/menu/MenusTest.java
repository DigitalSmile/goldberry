package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.GoldberryTestAccess;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;

/// [Menus] — the half of §8 that is a window rather than a tree, driven through
/// the real launcher and the real frame loop.
///
/// Clicks are posted at coordinates inside the popup's own window, so what is
/// being tested is the shipping path: the router hit-tests the frame the popup
/// painted, an `item` turns a click into its command, and `Menus` turns that into
/// "and close the stack".
class MenusTest {

    private static final class TestApp implements Application {

        private final Consumer<Host> onStart;
        private final Widget root;

        TestApp(Consumer<Host> onStart) {
            this(new Column(List.of(), io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE), onStart);
        }

        TestApp(Widget root, Consumer<Host> onStart) {
            this.root = root;
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return root;
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

    /// A click at `(x, y)` in `window`'s own coordinates: press, then release,
    /// which is what the router turns into a `CLICKED`.
    private void click(HeadlessWindow window, float x, float y) {
        backend.post(new BackendEvent.PointerMoved(window, x, y, 0));
        backend.post(new BackendEvent.PointerPressed(window, x, y, 1, 1, 0));
        backend.post(new BackendEvent.PointerReleased(window, x, y, 1, 1, 0));
    }

    /// Runs `action` on the UI thread after `millis` — long enough for the popup
    /// to have painted, which is what gives its router something to hit-test.
    private static void later(long millis, Runnable action) {
        Goldberry.async(() -> {
                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                })
                .thenRun(action);
    }

    private static final LogicalRect ANCHOR = LogicalRect.of(10, 10, 80, 24);

    /// Measure, fit, open — end to end, and the only place the second
    /// measurement really happens ([ADR-0179]).
    ///
    /// `Menus` used to decide this from an **estimate**: rows times an assumed
    /// 34px, because nothing reported what a menu measured. The estimate rounded
    /// up on purpose, so it erred towards giving a viewport to a menu that would
    /// have fitted — invisible, but a thumb and a wheel handler that had no
    /// business being there. The popup facility says what it measured now, so the
    /// menu is capped against its real height.
    ///
    /// The work area is set short rather than the menu made enormous: twenty rows
    /// is a real menu, and 240px is a real laptop with a real dock on it.
    @Test
    @Timeout(20)
    @DisplayName("a menu longer than the work area opens at the work area's height")
    void aLongMenuScrolls() {
        backend.workArea(LogicalRect.of(0, 0, 800, 240));
        var height = new float[1];
        Goldberry.launch(new TestApp(host -> {
            var items = new ArrayList<Widget>();
            for (var index = 0; index < 20; index++) {
                var label = "Command " + index;
                items.add(new Item(label, () -> {}));
            }
            Menus.open(host, ANCHOR, new Menu(items, io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE))
                    .orElseThrow();

            later(200, () -> {
                height[0] = popups().getFirst().size().height();
                Goldberry.stop();
            });
        }));

        // The room, and not the work area: a menu flush against both edges of the
        // screen looks cut off even when it is not.
        assertTrue(
                height[0] <= 240 - 2 * 8 + 0.5f,
                "a menu of twenty rows opened " + height[0] + " tall into 240px of screen,"
                        + " so its last commands are about to be clamped away");
        assertTrue(height[0] > 100, "it opened at " + height[0] + ", which is not a menu that was measured");
    }

    /// Which is nearly every menu, and the half the estimate got wrong: nothing
    /// is wrapped, so an ordinary menu has no viewport, no thumb and nothing that
    /// takes the wheel.
    @Test
    @Timeout(20)
    @DisplayName("a menu that fits opens at its own height, with nothing around it")
    void aShortMenuIsUntouched() {
        backend.workArea(LogicalRect.of(0, 0, 800, 1040));
        var height = new float[1];
        Goldberry.launch(new TestApp(host -> {
            Menus.open(host, ANCHOR, new Menu(new Item("First", () -> {}), new Item("Second", () -> {})))
                    .orElseThrow();

            later(200, () -> {
                height[0] = popups().getFirst().size().height();
                Goldberry.stop();
            });
        }));

        assertTrue(height[0] > 0 && height[0] < 200, "two rows opened " + height[0] + " tall");
    }

    /// Choosing a command runs it **and** closes the menu, which is what choosing
    /// a command does everywhere — and which `Menus` arranges so that an
    /// application cannot forget it on one row out of nine.
    @Test
    @Timeout(20)
    @DisplayName("clicking an item runs its command and closes the menu")
    void chooseACommand() {
        var chosen = new ArrayList<String>();
        var openAfter = new boolean[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(
                    new Item("First", () -> chosen.add("first")), new Item("Second", () -> chosen.add("second")));
            var popup = Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                // The first row: 4px of the menu's padding, then half a row down.
                click((HeadlessWindow) popups().getFirst(), 40, 20);
                later(200, () -> {
                    openAfter[0] = popup.isOpen();
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(List.of("first"), chosen);
        assertFalse(openAfter[0], "a chosen command closes the menu it was chosen from");
    }

    /// Hovering a row with children opens its submenu **beside** it, and the
    /// submenu is a second popup rather than something drawn inside the first.
    @Test
    @Timeout(20)
    @DisplayName("hovering an item with children opens a submenu beside it")
    void openASubmenu() {
        var count = new int[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(new Item("Plain", () -> {}), new Item("More").submenu(new Item("Inner", () -> {})));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                // The second row, which is the one with children.
                var first = (HeadlessWindow) popups().getFirst();
                backend.post(new BackendEvent.PointerMoved(first, 40, 52, 0));
                later(300, () -> {
                    count[0] = popups().size();
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(2, count[0], "the submenu is a window of its own, beside its item");
    }

    /// And choosing from the submenu closes both, because a command closes the
    /// whole stack rather than one level of it.
    @Test
    @Timeout(20)
    @DisplayName("a command in a submenu closes the whole stack")
    void chooseFromASubmenu() {
        var chosen = new ArrayList<String>();
        var left = new int[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(
                    new Item("Plain", () -> {}),
                    new Item("More").submenu(new Item("Inner", () -> chosen.add("inner"))));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                backend.post(new BackendEvent.PointerMoved((HeadlessWindow) popups().getFirst(), 40, 52, 0));
                later(300, () -> {
                    var submenu = popups().get(1);
                    click((HeadlessWindow) submenu, 40, 20);
                    later(200, () -> {
                        left[0] = (int)
                                popups().stream().filter(HeadlessPopup::isOpen).count();
                        Goldberry.stop();
                    });
                });
            });
        }));

        assertEquals(List.of("inner"), chosen);
        assertEquals(0, left[0], "choosing a command leaves nothing on screen");
    }

    /// The one a pointer user notices immediately: a submenu that will not go
    /// away. Moving to a sibling closes it, and most siblings have no submenu of
    /// their own — which is why *every* row reports the pointer arriving on it and
    /// not only the ones with children ([ADR-0112]).
    @Test
    @Timeout(20)
    @DisplayName("moving to a row without a submenu closes the one that is open")
    void submenuClosesOnASibling() {
        var openWhileHovering = new int[1];
        var openAfterMovingAway = new int[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(new Item("Plain", () -> {}), new Item("More").submenu(new Item("Inner", () -> {})));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                var first = (HeadlessWindow) popups().getFirst();
                // Onto the row with children, and wait for it to open.
                backend.post(new BackendEvent.PointerMoved(first, 40, 52, 0));
                later(300, () -> {
                    openWhileHovering[0] = (int)
                            popups().stream().filter(HeadlessPopup::isOpen).count();
                    // And back up to the row without one.
                    backend.post(new BackendEvent.PointerMoved(first, 40, 20, 0));
                    later(300, () -> {
                        openAfterMovingAway[0] = (int)
                                popups().stream().filter(HeadlessPopup::isOpen).count();
                        Goldberry.stop();
                    });
                });
            });
        }));

        assertEquals(2, openWhileHovering[0], "the submenu opened");
        assertEquals(
                1,
                openAfterMovingAway[0],
                "and closed again when the pointer moved to a sibling — the menu itself stays");
    }

    /// The window the popups belong to — where keys are posted, because the owner
    /// forwards them to whatever popup is open ([ADR-0104]).
    private HeadlessWindow ownerWindow() {
        return backend.windows().stream()
                .filter(window -> !(window instanceof HeadlessPopup))
                .map(HeadlessWindow.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private void press(Key key) {
        backend.post(new BackendEvent.KeyPressed(ownerWindow(), key.sdlKeycode(), 0, false));
    }

    /// A **tap** of a bare modifier: down, then up, with nothing in between.
    ///
    /// Posted as two backend events rather than driven through a detector,
    /// because the whole claim is that the shipping path recognises them —
    /// `Window` reads the raw keycode, arms, and fires on the release
    /// ([ADR-0223]).
    private void tap(io.github.digitalsmile.goldberry.input.tap.ModifierKey modifier) {
        backend.post(new BackendEvent.KeyPressed(
                ownerWindow(), modifier.leftKeycode(), modifier.modifier().bit(), false));
        backend.post(new BackendEvent.KeyReleased(ownerWindow(), modifier.leftKeycode(), 0));
    }

    private static long openCount(List<HeadlessPopup> popups) {
        return popups.stream().filter(HeadlessPopup::isOpen).count();
    }

    /// One pixel of a window's last painted frame, as `0xAARRGGBB`-ish — the
    /// packing does not matter, because every use of this compares two of them.
    private static int pixel(HeadlessWindow window, int x, int y) {
        var frame = window.lastFrame().orElseThrow();
        return frame.pixels().getInt(y * frame.stride() + x * 4);
    }

    /// §8's hover-intent delay is for a **pointer** travelling past three rows on
    /// its way somewhere. A keypress has travelled past nothing, and waiting the
    /// same 150 ms made `Right` feel broken.
    ///
    /// The assertion is the timing: 100 ms after the key, which is inside the
    /// delay the old code would still have been waiting out ([ADR-0219]).
    @Test
    @Timeout(20)
    @DisplayName("a keyboard Right opens a submenu at once, not after the pointer's delay")
    void rightOpensAtOnce() {
        var openSoonAfter = new long[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(new Item("Plain", () -> {}), new Item("More").submenu(new Item("Inner", () -> {})));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(300, () -> {
                // The menu focuses its first row when it opens, so one Down is
                // the row with children.
                press(Key.DOWN);
                press(Key.RIGHT);
                later(100, () -> {
                    openSoonAfter[0] = openCount(popups());
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(2, openSoonAfter[0], "the submenu was still waiting out a delay meant for the pointer");
    }

    /// The arrow that opens a submenu had no opposite: `Left` did nothing, so a
    /// keyboard user who opened a branch could not leave it.
    @Test
    @Timeout(20)
    @DisplayName("Left closes a submenu and leaves the menu it came from")
    void leftClosesASubmenu() {
        var afterOpening = new long[1];
        var afterBack = new long[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(new Item("Plain", () -> {}), new Item("More").submenu(new Item("Inner", () -> {})));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(300, () -> {
                press(Key.DOWN);
                press(Key.RIGHT);
                later(300, () -> {
                    afterOpening[0] = openCount(popups());
                    press(Key.LEFT);
                    later(300, () -> {
                        afterBack[0] = openCount(popups());
                        Goldberry.stop();
                    });
                });
            });
        }));

        assertEquals(2, afterOpening[0], "the submenu opened");
        assertEquals(1, afterBack[0], "and Left put it away without taking its menu with it");
    }

    /// `Left` at the **root** of a context menu has nowhere to go, and a menu that
    /// vanished on an arrow key would be a menu nobody could navigate. A bar's
    /// root menu answers it differently, which is `Siblings`.
    @Test
    @Timeout(20)
    @DisplayName("Left at the root of a context menu does nothing")
    void leftAtTheRootDoesNothing() {
        var stillOpen = new long[1];
        Goldberry.launch(new TestApp(host -> {
            Menus.open(host, ANCHOR, new Menu(new Item("Plain", () -> {}), new Item("Other", () -> {})))
                    .orElseThrow();

            later(300, () -> {
                press(Key.LEFT);
                later(200, () -> {
                    stillOpen[0] = openCount(popups());
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(1, stillOpen[0]);
    }

    /// Nothing said which branch was open: a row is `:focus-visible` when the
    /// keyboard is on it and `:hover` when the pointer is, and neither means "this
    /// is the one that is down" — the pointer is usually three rows away, *in the
    /// submenu*, by the time it matters.
    ///
    /// Measured in pixels, at a point on the row where no label is drawn, before
    /// and after — with the pointer moved out of this window in between, so the
    /// only thing that can have changed the row is the mark ([ADR-0219]).
    @Test
    @Timeout(20)
    @DisplayName("the row whose submenu is showing is marked, once the pointer has left it")
    void theOpenBranchIsMarked() {
        var before = new int[1];
        var after = new int[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(new Item("Plain", () -> {}), new Item("More").submenu(new Item("Inner", () -> {})));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(300, () -> {
                var first = (HeadlessWindow) popups().getFirst();
                before[0] = pixel(first, (int) first.size().width() - 30, 52);
                backend.post(new BackendEvent.PointerMoved(first, 40, 52, 0));
                later(400, () -> {
                    // Into the submenu, which is where a pointer goes next — and
                    // out of the menu that opened it, so the row keeps nothing
                    // but the mark.
                    backend.post(new BackendEvent.PointerExited(first));
                    backend.post(new BackendEvent.PointerMoved((HeadlessWindow) popups().get(1), 20, 20, 0));
                    later(400, () -> {
                        after[0] = pixel(first, (int) first.size().width() - 30, 52);
                        Goldberry.stop();
                    });
                });
            });
        }));

        assertNotEquals(
                before[0], after[0], "the row whose submenu is showing looks exactly like the rows that are not");
    }

    /// §8's bar: with a menu down, `Right` from a row that leads nowhere goes to
    /// the next menu, exactly as running along the bar with the pointer does.
    ///
    /// Which menu is showing is read from the popup's **height**: `File` has three
    /// rows and `Edit` has one, and a menu is as tall as its rows.
    @Test
    @Timeout(20)
    @DisplayName("Right and Left move between a menu bar's menus while one is showing")
    void arrowsMoveAlongTheBar() {
        var bar = new MenuBar(
                List.of(
                        new Item("File")
                                .submenu(
                                        new Item("New", () -> {}),
                                        new Item("Open", () -> {}),
                                        new Item("Save", () -> {})),
                        new Item("Edit").submenu(new Item("Undo", () -> {}))),
                io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);
        var fileHeight = new float[1];
        var editHeight = new float[1];
        var backAgain = new float[1];
        Goldberry.launch(new TestApp(
                bar,
                host -> later(400, () -> {
                    // F10 is the keyboard's way into the bar (ADR-0163), and it opens the
                    // first heading that can open.
                    press(Key.F10);
                    later(400, () -> {
                        fileHeight[0] = openPopup().size().height();
                        press(Key.RIGHT);
                        later(400, () -> {
                            editHeight[0] = openPopup().size().height();
                            press(Key.LEFT);
                            later(400, () -> {
                                backAgain[0] = openPopup().size().height();
                                Goldberry.stop();
                            });
                        });
                    });
                })));

        assertTrue(
                fileHeight[0] > editHeight[0],
                "Right did not move to the one-row Edit menu: " + fileHeight[0] + " then " + editHeight[0]);
        assertEquals(fileHeight[0], backAgain[0], "and Left came back to File");
    }

    /// The one popup that is open, which is how "which menu is showing" is asked
    /// of a bar that only ever has one down.
    private HeadlessPopup openPopup() {
        return popups().stream()
                .filter(HeadlessPopup::isOpen)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    /// §8's "`Alt`-style keyboard activation", through the real window, the real
    /// keycodes and the real popup — the half a widget test cannot reach, because
    /// what recognises a tap lives below the widget tree entirely ([ADR-0223]).
    ///
    /// The second tap **closes**, which is what every desktop bar does with the
    /// same key and what makes the gesture safe to bind: a user who tapped `Alt`
    /// by accident taps it again rather than hunting for `Escape`.
    @Test
    @Timeout(20)
    @DisplayName("a bare Alt tap opens a menu bar, and a second one puts it away")
    void altTapOpensAndCloses() {
        var bar = new MenuBar(
                List.of(new Item("File").submenu(new Item("New", () -> {}), new Item("Open", () -> {}))),
                io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);
        var afterFirst = new long[1];
        var afterSecond = new long[1];
        var afterShortcut = new long[1];
        Goldberry.launch(new TestApp(
                bar,
                host -> later(400, () -> {
                    tap(io.github.digitalsmile.goldberry.input.tap.ModifierKey.ALT);
                    later(400, () -> {
                        afterFirst[0] = openCount(popups());
                        tap(io.github.digitalsmile.goldberry.input.tap.ModifierKey.ALT);
                        later(400, () -> {
                            afterSecond[0] = openCount(popups());
                            // And `Alt+F` is a shortcut rather than a tap, so it
                            // must leave the bar exactly as it found it.
                            backend.post(new BackendEvent.KeyPressed(
                                    ownerWindow(),
                                    io.github.digitalsmile.goldberry.input.tap.ModifierKey.ALT.leftKeycode(),
                                    io.github.digitalsmile.goldberry.input.key.Mod.ALT.bit(),
                                    false));
                            backend.post(new BackendEvent.KeyPressed(
                                    ownerWindow(),
                                    Key.F.sdlKeycode(),
                                    io.github.digitalsmile.goldberry.input.key.Mod.ALT.bit(),
                                    false));
                            backend.post(new BackendEvent.KeyReleased(
                                    ownerWindow(),
                                    Key.F.sdlKeycode(),
                                    io.github.digitalsmile.goldberry.input.key.Mod.ALT.bit()));
                            backend.post(new BackendEvent.KeyReleased(
                                    ownerWindow(),
                                    io.github.digitalsmile.goldberry.input.tap.ModifierKey.ALT.leftKeycode(),
                                    0));
                            later(400, () -> {
                                afterShortcut[0] = openCount(popups());
                                Goldberry.stop();
                            });
                        });
                    });
                })));

        assertEquals(1, afterFirst[0], "a bare Alt tap did not open the bar");
        assertEquals(0, afterSecond[0], "a second tap did not put it away");
        assertEquals(0, afterShortcut[0], "Alt+F is a shortcut and must not be read as a tap of Alt");
    }

    /// `Escape` steps **out of one menu**, not out of the whole chain
    /// ([ADR-0233]).
    ///
    /// The launcher's light dismissal closes every popup at once, which is right
    /// for the press that lands somewhere else — the user pointed at something
    /// other than the menu — and wrong for `Escape`, which is how a reader backs
    /// out of a submenu they opened by mistake. Closing the stack there loses the
    /// menu that opened it, and there is nothing to reopen it with but the mouse.
    @Test
    @Timeout(20)
    @DisplayName("Escape closes the submenu and leaves the menu that opened it")
    void escapeClosesOneMenu() {
        var afterOpen = new long[1];
        var afterFirst = new long[1];
        var afterSecond = new long[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(new Item("Plain", () -> {}), new Item("More").submenu(new Item("Inner", () -> {})));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(300, () -> {
                // Onto the row that leads somewhere, which is what opens it —
                // §8's hover-opens-a-submenu, and the shortest way to a chain.
                backend.post(new BackendEvent.PointerMoved((HeadlessWindow) popups().getFirst(), 40, 52, 0));
                later(400, () -> {
                    afterOpen[0] = openCount(popups());
                    press(Key.ESCAPE);
                    later(300, () -> {
                        afterFirst[0] = openCount(popups());
                        press(Key.ESCAPE);
                        later(300, () -> {
                            afterSecond[0] = openCount(popups());
                            Goldberry.stop();
                        });
                    });
                });
            });
        }));

        assertEquals(2, afterOpen[0], "the submenu never opened, so there is no chain to escape from");
        assertEquals(1, afterFirst[0], "Escape took the whole chain rather than the menu it was in");
        assertEquals(0, afterSecond[0], "and a second Escape should have closed the root menu");
    }

    @Test
    @Timeout(20)
    @DisplayName("a disabled item does nothing at all")
    void disabledDoesNothing() {
        var chosen = new ArrayList<String>();
        var stillOpen = new boolean[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(new Item("Nope", () -> chosen.add("nope")).disabled(true));
            var popup = Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                click((HeadlessWindow) popups().getFirst(), 40, 20);
                later(200, () -> {
                    stillOpen[0] = popup.isOpen();
                    Goldberry.stop();
                });
            });
        }));

        assertTrue(chosen.isEmpty(), "a disabled command does not run");
        assertTrue(stillOpen[0], "and does not close the menu either");
    }
}
