package dev.goldberry.example.ui.sheet;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.Host;
import dev.goldberry.Overlay;
import dev.goldberry.bind.Subscription;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.dialog.FileDialogs;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.stats.FrameStats;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Corner;

/// A host for a tree a test builds by hand: it remembers every widget put over
/// the window, hands out the clipboard it was given, and answers everything else
/// the way a window with no desktop under it would.
///
/// A proxy rather than thirty methods written out, so a method added to [Host]
/// does not break every test that builds a tree. A default method runs its own
/// body; an `Optional` is empty; a call with no sensible answer, such as
/// `window()`, throws.
public final class RecordingTestHost {

    /// What was filled over the window, oldest first.
    private final List<Widget> filled = new ArrayList<>();

    private final Clipboard clipboard;

    private final Host host;

    /// A host with no clipboard.
    public RecordingTestHost() {
        this(Clipboard.none());
    }

    /// A host whose clipboard is `clipboard`, for a test about copying and
    /// pasting.
    public RecordingTestHost(Clipboard clipboard) {
        this.clipboard = clipboard;
        this.host =
                (Host) Proxy.newProxyInstance(Host.class.getClassLoader(), new Class<?>[] {Host.class}, new Answers());
    }

    /// The host to build a tree with.
    public Host host() {
        return host;
    }

    /// The last widget filled over the window, or a failure saying nothing was.
    public Widget last() {
        if (filled.isEmpty()) {
            throw new AssertionError("nothing was put over the window");
        }
        return filled.getLast();
    }

    private final class Answers implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "RecordingTestHost";
                };
            }
            var type = method.getReturnType();
            return switch (method.getName()) {
                case "fill" -> {
                    var widget = (Widget) args[0];
                    filled.add(widget);
                    yield Overlay.filling(widget);
                }
                case "overlay" ->
                    args.length == 3
                            ? Overlay.of((Widget) args[0], (Corner) args[1], (Float) args[2])
                            : Overlay.of((Widget) args[0], (Corner) args[1]);
                case "clipboard" -> clipboard;
                case "fileDialogs" -> FileDialogs.none();
                case "frames" -> FrameStats.none();
                case "placeableArea" -> LogicalRect.of(0, 0, 900, 560);
                default -> {
                    if (type == Optional.class) {
                        yield Optional.empty();
                    }
                    if (type == Subscription.class) {
                        yield (Subscription) () -> {};
                    }
                    if (method.isDefault()) {
                        yield InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    if (type == void.class) {
                        yield null;
                    }
                    if (type == boolean.class) {
                        yield false;
                    }
                    throw new UnsupportedOperationException(method.getName() + " is not something a test host has");
                }
            };
        }
    }
}
