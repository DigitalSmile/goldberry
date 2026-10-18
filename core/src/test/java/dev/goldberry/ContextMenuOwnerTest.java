package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.handler.ContextMenuOwner;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.junit.DrivenRuntime;
import dev.goldberry.paint.Box;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A widget with a menu of its own, on the walk a right-click starts.
///
/// The text fields' stock menu depends on three claims about the walk, and this
/// is where they are held without a text field: an owner opens its menu with no
/// application handler at all, a name the application wrote on the same widget
/// wins over it, and an owner inside a named panel wins over the panel.
class ContextMenuOwnerTest {

    /// A node that fills what it is given, may name a menu, and may own one,
    /// recording what happened in `log`.
    private record Plate(
            String name, List<String> log, boolean owns, boolean opens, Attributes attributes, List<Widget> kids)
            implements Widget.Leaf, Styled, Paints, Handles, ContextMenuOwner, Attributed<Plate> {

        static Plate owner(String name, List<String> log, @Nullable String menu, Widget... kids) {
            return new Plate(name, log, true, true, attributes(name, menu), List.of(kids));
        }

        static Plate plain(String name, List<String> log, @Nullable String menu, Widget... kids) {
            return new Plate(name, log, false, false, attributes(name, menu), List.of(kids));
        }

        private static Attributes attributes(String name, @Nullable String menu) {
            return menu == null
                    ? new Attributes(name, Set.of(), name)
                    : new Attributes(name, Set.of(), name, null, menu);
        }

        Plate declining() {
            return new Plate(name, log, owns, false, attributes, kids);
        }

        @Override
        public boolean openContextMenu(LogicalRect anchor) {
            if (!owns) {
                return false;
            }
            log.add("own:" + name + (opens ? "" : " declined"));
            return opens;
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
            return new Plate(name, log, owns, opens, value, kids);
        }

        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public List<Widget> children() {
            return kids;
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
    private DrivenRuntime runtime;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        runtime = DrivenRuntime.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessWindow window() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    /// A right-click at (120, 90) on `root`, after `setUp` ran against the host.
    private void rightClick(Widget root, Consumer<Host> setUp) {
        Goldberry.launch(new TestApp(root, host -> {
            setUp.accept(host);
            runtime.afterTheFirstFrame(host, () -> {
                backend.post(new BackendEvent.PointerMoved(window(), 120, 90, 0));
                backend.post(new BackendEvent.PointerPressed(window(), 120, 90, 3, 1, 0));
                runtime.afterTheNextPump(Goldberry::stop);
            });
        }));
    }

    @Test
    @Timeout(20)
    @DisplayName("an owner opens its own menu with no application handler registered")
    void opensWithoutAHandler() {
        var log = new ArrayList<String>();
        rightClick(Plate.owner("field", log, null), host -> {});

        assertEquals(List.of("own:field"), log);
    }

    @Test
    @Timeout(20)
    @DisplayName("a menu the application named on the same widget wins over the widget's own")
    void aNameOnTheSameWidgetWins() {
        var log = new ArrayList<String>();
        rightClick(
                Plate.owner("field", log, "mine"), host -> host.onContextMenu((menu, at) -> log.add("named:" + menu)));

        assertEquals(List.of("named:mine"), log);
    }

    @Test
    @Timeout(20)
    @DisplayName("an owner inside a named panel opens its own menu, the deepest answer")
    void theDeepestAnswerWins() {
        var log = new ArrayList<String>();
        rightClick(
                Plate.plain("panel", log, "content", Plate.owner("field", log, null)),
                host -> host.onContextMenu((menu, at) -> log.add("named:" + menu)));

        assertEquals(List.of("own:field"), log);
    }

    @Test
    @Timeout(20)
    @DisplayName("an owner that declines lets the walk go on to the panel above")
    void aDeclineFallsThrough() {
        var log = new ArrayList<String>();
        rightClick(
                Plate.plain(
                        "panel", log, "content", Plate.owner("field", log, null).declining()),
                host -> host.onContextMenu((menu, at) -> log.add("named:" + menu)));

        assertEquals(List.of("own:field declined", "named:content"), log);
    }

    @Test
    @Timeout(20)
    @DisplayName("a name with no handler registered opens nothing, and the walk goes on")
    void aNameWithoutAHandler() {
        var log = new ArrayList<String>();
        rightClick(Plate.owner("panel", log, null, Plate.plain("row", log, "rows")), host -> {});

        assertEquals(List.of("own:panel"), log);
    }
}
