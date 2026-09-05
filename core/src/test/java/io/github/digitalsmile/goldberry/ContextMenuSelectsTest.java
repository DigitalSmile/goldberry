package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Selects;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A right-click selects what it is over before the menu opens ([ADR-0224]).
///
/// The toolkit still has no notion of what "select" means for an arbitrary
/// widget. What it has is a walk — the one that finds the menu's name — and a
/// widget on that walk that knows. So every claim here is about the *walk*: who
/// is asked, when, and in what order relative to the menu opening. What a list's
/// row does when asked belongs to the catalog and is tested there.
///
/// Driven through the real launcher, because the walk is the launcher's and the
/// press that starts it never reaches a router at all.
class ContextMenuSelectsTest {

    /// A node that fills what it is given, records being asked to select, and
    /// carries whatever attributes the test gave it — so one type covers "names a
    /// menu", "names none", and "is inside another one".
    private record Plate(String name, List<String> asked, Attributes attributes, List<Widget> kids)
            implements Widget.Leaf, Styled, Paints, Handles, Selects, Attributed<Plate> {

        Plate(String name, List<String> asked, String menu, Widget... kids) {
            this(
                    name,
                    asked,
                    menu == null
                            ? new Attributes(name, Set.of(), name)
                            : new Attributes(name, Set.of(), name, null, menu),
                    List.of(kids));
        }

        @Override
        public String cssType() {
            return "plate";
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
        public Plate withAttributes(Attributes value) {
            return new Plate(name, asked, value, kids);
        }

        /// Focusable, so the keyboard's half of the walk has somewhere to start.
        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public void selectForContextMenu() {
            asked.add(name);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1).children(children.toArray(Box[]::new));
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
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "plate { background: #204060 }"));
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
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessWindow window() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    private long popups() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .count();
    }

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

    /// A right-click at (120, 90) on `root`, with `handler` registered.
    ///
    /// The handler opens nothing by default — this backend has no popup windows
    /// (ADR-0102) — so what a test reads is the record of who was asked and what
    /// the handler saw, in the order they happened.
    private void rightClick(Widget root, Consumer<Host> handler) {
        Goldberry.launch(new TestApp(root, host -> {
            handler.accept(host);
            later(150, () -> {
                backend.post(new BackendEvent.PointerMoved(window(), 120, 90, 0));
                backend.post(new BackendEvent.PointerPressed(window(), 120, 90, 3, 1, 0));
                later(200, Goldberry::stop);
            });
        }));
    }

    @Test
    @Timeout(20)
    @DisplayName("a right-click asks what it is over to select itself")
    void asksTheWidgetUnderThePointer() {
        var asked = new ArrayList<String>();
        rightClick(new Plate("row", asked, "rows"), host -> host.onContextMenu((menu, at) -> {}));

        assertEquals(List.of("row"), asked);
    }

    /// The ordering claim, and the one an application depends on: a menu built
    /// from the application's own selection has to read the **new** one, and a
    /// row has to be drawn selected in the frame the menu opens over.
    @Test
    @Timeout(20)
    @DisplayName("it is asked before the menu opens, not after")
    void beforeTheMenu() {
        var order = new ArrayList<String>();
        rightClick(new Plate("row", order, "rows"), host -> host.onContextMenu((menu, at) -> order.add("menu")));

        assertEquals(List.of("row", "menu"), order);
    }

    /// A selection that changed with no menu to show for it is a gesture with no
    /// visible cause — so nothing is asked when nothing names a menu.
    @Test
    @Timeout(20)
    @DisplayName("a right-click that opens no menu selects nothing")
    void noMenuNoSelection() {
        var asked = new ArrayList<String>();
        rightClick(new Plate("row", asked, null), host -> host.onContextMenu((menu, at) -> {}));

        assertTrue(asked.isEmpty(), "asked anyway: " + asked);
    }

    /// The same rule as "a right-click on a button's label is a right-click on
    /// the button", read the other way round: the name is found on the ancestor
    /// and the *subject* is the deepest thing that has one.
    @Test
    @Timeout(20)
    @DisplayName("the deepest widget on the walk is the subject, not the one that named the menu")
    void deepestWins() {
        var asked = new ArrayList<String>();
        var inner = new Plate("inner", asked, null);
        rightClick(new Plate("outer", asked, "rows", inner), host -> host.onContextMenu((menu, at) -> {}));

        assertEquals(List.of("inner"), asked, "the outer plate named the menu; the inner one is what was clicked");
    }

    /// Only once, however many ancestors could have answered — a right-click is
    /// one gesture and has one subject.
    @Test
    @Timeout(20)
    @DisplayName("only one widget is asked, however many could answer")
    void onlyOne() {
        var asked = new ArrayList<String>();
        var inner = new Plate("inner", asked, null);
        var middle = new Plate("middle", asked, null, inner);
        rightClick(new Plate("outer", asked, "rows", middle), host -> host.onContextMenu((menu, at) -> {}));

        assertEquals(List.of("inner"), asked);
    }

    /// The keyboard's half shares the walk, so it shares this: the menu key on a
    /// focused-but-unselected row selects it, exactly as a right-click does
    /// (ADR-0208).
    @Test
    @Timeout(20)
    @DisplayName("the menu key selects what it opens over, like the pointer")
    void theMenuKeyToo() {
        var asked = new ArrayList<String>();
        var opened = new long[1];
        Goldberry.launch(new TestApp(new Plate("row", asked, "rows"), host -> {
            host.onContextMenu((menu, at) -> {});
            later(150, () -> {
                host.focus("row", true);
                later(150, () -> {
                    // SDL's `SDLK_APPLICATION` — the key between AltGr and Ctrl.
                    backend.post(new BackendEvent.KeyPressed(window(), 0x40000065, 0, false));
                    later(200, () -> {
                        opened[0] = popups();
                        Goldberry.stop();
                    });
                });
            });
        }));

        assertEquals(List.of("row"), asked);
        assertEquals(0, opened[0], "this backend has no popup windows, which is not what is being asserted");
    }
}
