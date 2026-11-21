package dev.goldberry.natives.desktop.dragout;

import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongPredicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// A drag out on X11, spoken in XDND on SDL's own `Display`.
///
/// Its window's replies (`XdndStatus`, `XdndFinished`, `SelectionRequest`)
/// arrive through SDL's event loop, so the drag listens through
/// [X11EventHook] for as long as it lasts: each event is offered to the
/// protocol, and the pointer is read again after it. Motion arrives as XInput2
/// events, which are not parsed: every event is a reason to look at the
/// pointer, and the pointer's own query says where it is and whether the
/// button is still held.
///
/// The listener is called from inside SDL's event pump. A caller that runs
/// application code on what it hears should hand it to the UI thread's queue
/// rather than run it there.
public final class X11DragSource implements AutoCloseable {

    private static final Logger LOG = Logs.of(X11DragSource.class);

    /// How long a target has to answer a drop before the drag is taken as
    /// delivered anyway.
    public static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(5);

    private final XlibXdnd transport;
    private final XdndSource source;
    private final X11EventHook hook;
    private final X11EventHook.Handler handler = this::onEvent;
    private long waitingSince;
    private boolean closed;

    private X11DragSource(XlibXdnd transport, XdndSource source, X11EventHook hook) {
        this.transport = transport;
        this.source = source;
        this.hook = hook;
    }

    /// Starts a drag on `display`, or answers empty where libX11 will not load.
    ///
    /// @param display  the address of SDL's `Display*`, from the window the drag
    ///                 began in (`SdlVideo.x11Display`)
    /// @param own      whether an X window id is one of the application's own
    /// @param data     what the drag carries
    /// @param actions  what a target may do with it
    /// @param listener told where the pointer is over own windows and how it ended
    public static Optional<X11DragSource> start(
            long display, LongPredicate own, DragData data, Set<DragAction> actions, DragSourceListener listener) {
        Objects.requireNonNull(listener, "listener");
        var opened = XlibXdnd.open(MemorySegment.ofAddress(display), own);
        if (opened.isEmpty()) {
            return Optional.empty();
        }
        var transport = opened.get();
        var drag = new X11DragSource[1];
        var source = new XdndSource(transport, data, actions, new Ending(listener, () -> drag[0].close()));
        drag[0] = new X11DragSource(transport, source, X11EventHook.get());
        drag[0].hook.handle(drag[0].handler);
        try {
            source.start();
        } catch (RuntimeException e) {
            drag[0].close();
            throw e;
        }
        return Optional.of(drag[0]);
    }

    /// Ends a drag whose target has not answered for [#ANSWER_TIMEOUT]. Called
    /// from a timer; harmless at any other time.
    public void tick() {
        if (closed || !source.isWaiting()) {
            waitingSince = 0;
            return;
        }
        var now = System.nanoTime();
        if (waitingSince == 0) {
            waitingSince = now;
        } else if (now - waitingSince > ANSWER_TIMEOUT.toNanos()) {
            LOG.debug("an XDND target did not answer for {}; the drag is over", ANSWER_TIMEOUT);
            source.timeOut();
        }
    }

    /// Whether the drag is over and its window gone.
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        hook.stop(handler);
        transport.close();
    }

    private boolean onEvent(MemorySegment event) {
        if (closed) {
            return true;
        }
        var taken = source.handle(XlibXdnd.decode(event));
        source.poll();
        return !taken;
    }

    /// Hears the ending, then closes the drag: after its ending, nothing is
    /// left to ask the source window for.
    private record Ending(DragSourceListener listener, Runnable close) implements DragSourceListener {

        @Override
        public void over(long window, int x, int y) {
            listener.over(window, x, y);
        }

        @Override
        public void outside() {
            listener.outside();
        }

        @Override
        public void delivered(@Nullable DragAction action) {
            try {
                listener.delivered(action);
            } finally {
                close.run();
            }
        }

        @Override
        public void releasedOver(long window, int x, int y) {
            try {
                listener.releasedOver(window, x, y);
            } finally {
                close.run();
            }
        }

        @Override
        public void cancelled() {
            try {
                listener.cancelled();
            } finally {
                close.run();
            }
        }
    }
}
