package dev.goldberry.example.ui.windows;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.Host;
import dev.goldberry.WindowHost;
import dev.goldberry.bind.Subscription;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widgets.controls.button.Button;

/// The second window's close listener belongs to the card: it is dropped when the
/// card goes, before the card closes the window, so it never reaches a state that
/// is gone.
@DisplayName("the second-window card")
class SecondWindowCardTest {

    /// A window that remembers its close listeners, and whether each was dropped.
    private static final class Window {

        final List<Runnable> listeners = new ArrayList<>();
        int dropped;
        boolean open = true;

        WindowHost host() {
            return (WindowHost) Proxy.newProxyInstance(
                    WindowHost.class.getClassLoader(),
                    new Class<?>[] {WindowHost.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "onClose" -> {
                            listeners.add((Runnable) args[0]);
                            yield (Subscription) () -> dropped++;
                        }
                        case "isOpen" -> open;
                        case "close" -> {
                            open = false;
                            List.copyOf(listeners).forEach(Runnable::run);
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    /// A host that opens `window` and answers nothing else.
    private static Host opening(WindowHost window) {
        return (Host) Proxy.newProxyInstance(
                Host.class.getClassLoader(),
                new Class<?>[] {Host.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "openWindow" -> Optional.of(window);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.getReturnType() == Optional.class ? Optional.empty() : null;
                });
    }

    private static Button button(Element root, String id) {
        return walk(root)
                .map(Element::widget)
                .flatMap(widget -> widget instanceof Button button
                                && id.equals(button.attributes().id())
                        ? Stream.of(button)
                        : Stream.empty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no button #" + id));
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(SecondWindowCardTest::walk));
    }

    @Test
    @DisplayName("drops the listener when the card goes, then closes the window")
    void dropsTheListenerOnDispose() {
        var window = new Window();
        var tree = new ElementTree(new SecondWindowCard(), opening(window.host()));
        tree.flush();
        button(tree.root(), "second-open").onPress().run();
        tree.flush();

        tree.unmount();

        assertAll(
                () -> assertEquals(1, window.listeners.size(), "one listener for one window"),
                () -> assertEquals(1, window.dropped, "the listener was not dropped with the card"),
                () -> assertFalse(window.open, "the card closes the window it opened"));
    }

    @Test
    @DisplayName("hears the window close while the card is there")
    void hearsTheClose() {
        var window = new Window();
        var tree = new ElementTree(new SecondWindowCard(), opening(window.host()));
        tree.flush();
        button(tree.root(), "second-open").onPress().run();
        tree.flush();

        button(tree.root(), "second-close-from-here").onPress().run();
        tree.flush();

        assertTrue(
                walk(tree.root())
                        .anyMatch(element -> element.widget() instanceof Button button
                                && "second-open".equals(button.attributes().id())),
                "the card offers to open a window again once this one closed");
        tree.unmount();
    }
}
