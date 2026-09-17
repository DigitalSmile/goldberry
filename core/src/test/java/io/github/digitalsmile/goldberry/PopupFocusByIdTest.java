package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// [Host#focus] reaches a node inside an open popup, not only the window's
/// ([ADR-0368]) — which is how a tree in a `select`'s list moves its typeahead.
class PopupFocusByIdTest {

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

    private record Column(List<Widget> children) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "column";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    private record TestApp(Widget root, Consumer<Host> onStart) implements Application {

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    column { flex-direction: column }
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

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        GoldberryRuntime.install(new HeadlessBackend());
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    private static void afterTurns(Host host, int turns, Runnable action) {
        if (turns <= 0) {
            action.run();
            return;
        }
        host.after(Duration.ZERO, () -> afterTurns(host, turns - 1, action));
    }

    @Test
    @Timeout(20)
    @DisplayName("a node in an open popup is focused by name, and the answer says so")
    void focusesIntoThePopup() {
        var focused = new ArrayList<String>();
        var answered = new boolean[1];
        Goldberry.launch(
                new TestApp(new Item("window-item", focused), host -> {
                    host.popup(
                                    new Column(List.of(new Item("one", focused), new Item("two", focused))),
                                    LogicalRect.of(10, 10, 100, 30),
                                    Placement.BELOW)
                            .orElseThrow();
                    afterTurns(host, 3, () -> {
                        answered[0] = host.focus("two", true);
                        afterTurns(host, 3, Goldberry::stop);
                    });
                }),
                new String[] {"--frames=400"});

        assertTrue(answered[0], "focus by name found nothing in the popup");
        assertEquals("two", focused.getLast());
    }
}
