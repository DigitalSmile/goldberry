package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A popup that wants **no** keys — `docs/gaps.md` G29, ADR-0319.
///
/// [PopupLifecycleTest] covers the menu: ADR-0104's forwarding rule says the
/// keyboard belongs to whatever is open over the window, and that is right for a
/// menu, which is up for as long as the user is choosing from it. A *panel* — a
/// bar of buttons floating over a canvas somebody is typing into — is up the whole
/// time something is selected, and every key it takes is a key the canvas did not
/// get.
///
/// Every test here is the same shape: a focusable node in the **window**, a popup
/// over it with two focusable rows, and one key or one press. What changes is
/// whether the popup asked for the keyboard.
class PanelPopupTest {

    /// The window's content: focusable, and it writes down every key it is given.
    private record Typist(List<Key> keys) implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public String cssType() {
            return "typist";
        }

        @Override
        public String id() {
            return "typist";
        }

        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public void onKey(KeyEvent event) {
            if (event.kind() == KeyEvent.Kind.PRESSED) {
                keys.add(event.key());
            }
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    /// A row in the popup — a swatch on a selection bar, an item in a menu.
    private record Item(String name, List<String> focused) implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public void onFocusChanged(boolean gained, boolean fromKeyboard) {
            if (gained) {
                focused.add(name);
            }
        }

        @Override
        public String cssType() {
            return "item";
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }

    /// What the popup holds: a scope with rows in it, so `Down` would mean
    /// something if the popup were listening.
    private record Bar(List<Widget> items) implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public FocusScope focusScope() {
            return FocusScope.VERTICAL;
        }

        @Override
        public String cssType() {
            return "bar";
        }

        @Override
        public List<Widget> children() {
            return items;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    private static final class TestApp implements Application {

        private final Widget root;
        private final Consumer<Host> onStart;

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
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    typist { flex-grow: 1; background: #204060 }
                    bar { background: #eceff4; flex-direction: column }
                    item { width: 100px; height: 24px }
                    """));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }

        @Override
        public void stop() {}
    }

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessWindow ownerWindow() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    private HeadlessPopup popupWindow() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no popup was opened"));
    }

    /// Runs `action` after `turns` more turns of the event loop — the helper
    /// [PopupLifecycleTest] carries, for its reason: a run bounded by `--frames`
    /// finishes in whatever wall-clock time the machine takes, so a zero-delay
    /// timer on the loop is the only delay that cannot outlive it.
    private static void afterTurns(Host host, int turns, Runnable action) {
        if (turns <= 0) {
            action.run();
            return;
        }
        host.after(Duration.ZERO, () -> afterTurns(host, turns - 1, action));
    }

    /// What every test here does: focus the window's content, open a popup over it
    /// as a panel or as a menu, then let `input` happen and stop.
    private Run run(boolean panel, Consumer<Host> input) {
        var result = new Run(new ArrayList<>(), new ArrayList<>(), new Popup[1]);
        Goldberry.launch(
                new TestApp(new Typist(result.keys()), host -> {
                    var popup = host.attachedPopup(
                                    new Bar(List.of(
                                            new Item("one", result.focused()), new Item("two", result.focused()))),
                                    LogicalRect.of(10, 10, 100, 30),
                                    Placement.BELOW,
                                    0,
                                    null)
                            .orElseThrow();
                    result.popup()[0] = popup;
                    if (panel) {
                        popup.keyboard(false);
                    }
                    // Three turns, because focus inside a popup is placed after its
                    // first frame: the tree traversal walks elements, and there are
                    // none until it has been painted once.
                    afterTurns(host, 3, () -> {
                        // After a frame, because focus by id walks the element tree
                        // and there is none until the window has painted once.
                        host.focus("typist", true);
                        input.accept(host);
                        afterTurns(host, 3, Goldberry::stop);
                    });
                }),
                new String[] {"--frames=400"});
        return result;
    }

    /// @param keys    what the window's content was told
    /// @param focused what took focus inside the popup, in order
    /// @param popup   the popup itself, so a test can ask whether it is still open
    private record Run(List<Key> keys, List<String> focused, Popup[] popup) {}

    @Test
    @Timeout(20)
    @DisplayName("a panel focuses nothing when it opens")
    void aPanelOpensWithNothingFocused() {
        var run = run(true, host -> {});

        assertEquals(List.of(), run.focused(), "a panel took the keyboard the moment it appeared");
    }

    @Test
    @Timeout(20)
    @DisplayName("a menu still focuses its first row, as it always did")
    void aMenuStillTakesTheKeyboard() {
        var run = run(false, host -> {});

        assertEquals(List.of("one"), run.focused());
    }

    /// The reported fault, and the whole of it: `Enter` pressed a swatch instead of
    /// breaking a line in the text underneath.
    @Test
    @Timeout(20)
    @DisplayName("Enter goes to the window under a panel")
    void keysReachTheWindowUnderAPanel() {
        var run = run(
                true,
                host -> backend.post(new BackendEvent.KeyPressed(ownerWindow(), Key.ENTER.sdlKeycode(), 0, false)));

        assertEquals(List.of(Key.ENTER), run.keys(), "the panel swallowed a key that was never its business");
        assertEquals(List.of(), run.focused());
    }

    @Test
    @Timeout(20)
    @DisplayName("and is taken by a menu, which is ADR-0104's rule unchanged")
    void keysStopAtAMenu() {
        var run = run(
                false,
                host -> backend.post(new BackendEvent.KeyPressed(ownerWindow(), Key.DOWN.sdlKeycode(), 0, false)));

        assertEquals(List.of(), run.keys(), "an arrow moved a selection in the window underneath a menu");
        assertEquals(List.of("one", "two"), run.focused(), "and it moved between the menu's own rows");
    }

    /// `takesFocus(false)` settles the opening and nothing else, which is what the
    /// gap says: a press inside the popup still focused what it landed on, so
    /// clicking a swatch and then pressing `Enter` pressed that swatch again.
    @Test
    @Timeout(20)
    @DisplayName("a press inside a panel focuses nothing, so the next Enter still misses it")
    void aPressInAPanelFocusesNothing() {
        var run = run(true, host -> {
            backend.post(new BackendEvent.PointerPressed(popupWindow(), 5, 5, 1, 1, 0));
            backend.post(new BackendEvent.KeyPressed(ownerWindow(), Key.ENTER.sdlKeycode(), 0, false));
        });

        assertEquals(List.of(), run.focused(), "the press moved the keyboard into a panel that declined it");
        assertEquals(List.of(Key.ENTER), run.keys(), "and the Enter after it went to the swatch instead of the text");
    }

    @Test
    @Timeout(20)
    @DisplayName("a press inside a menu does focus what it landed on")
    void aPressInAMenuFocuses() {
        var run = run(false, host -> backend.post(new BackendEvent.PointerPressed(popupWindow(), 5, 5, 1, 1, 0)));

        assertEquals(
                List.of("one"),
                run.focused(),
                "the row was focused when the menu opened and the press on it changed nothing");
    }

    /// Declining keys is not refusing to close: `Escape` is
    /// [Popup#lightDismiss(boolean)]'s business, and a panel that must survive one
    /// says so there.
    @Test
    @Timeout(20)
    @DisplayName("Escape still dismisses a panel")
    void escapeStillCloses() {
        var run = run(
                true,
                host -> backend.post(new BackendEvent.KeyPressed(ownerWindow(), Key.ESCAPE.sdlKeycode(), 0, false)));

        assertFalse(run.popup()[0].isOpen(), "a panel that may be dismissed by input was not");
        assertEquals(List.of(), run.keys(), "and the Escape that closed it was not also delivered to the window");
    }

    @Test
    @Timeout(20)
    @DisplayName("a panel that refuses light dismissal lets Escape through instead")
    void escapeReachesTheWindowWhenThePanelStays() {
        var result = new ArrayList<Key>();
        var focused = new ArrayList<String>();
        var held = new Popup[1];
        Goldberry.launch(
                new TestApp(new Typist(result), host -> {
                    held[0] = host.attachedPopup(
                                    new Bar(List.of(new Item("one", focused))),
                                    LogicalRect.of(10, 10, 100, 30),
                                    Placement.BELOW,
                                    0,
                                    null)
                            .orElseThrow()
                            .keyboard(false)
                            .lightDismiss(false);
                    afterTurns(host, 3, () -> {
                        host.focus("typist", true);
                        backend.post(new BackendEvent.KeyPressed(ownerWindow(), Key.ESCAPE.sdlKeycode(), 0, false));
                        afterTurns(host, 3, () -> {
                            held[0].close();
                            Goldberry.stop();
                        });
                    });
                }),
                new String[] {"--frames=400"});

        assertEquals(List.of(Key.ESCAPE), result, "a panel that takes no keys and cannot be dismissed kept one anyway");
    }
}
