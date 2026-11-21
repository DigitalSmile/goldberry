package dev.goldberry.render.backend.sdl3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.DragAction;
import dev.goldberry.natives.desktop.dragout.DragSourceListener;
import dev.goldberry.natives.desktop.dragout.X11DragSource;
import dev.goldberry.render.dragout.DragEnd;
import dev.goldberry.render.dragout.DragRequest;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.NativeHandle;

/// A drag out on X11: the offer as XDND types, the source's reports as the
/// session's, and the application's own windows by their X ids.
///
/// The reports are heard inside SDL's event pump, from its X11 hook, and are
/// handed to the next pump to deliver, so that no application code (a drop
/// target's `onDrop`) runs inside SDL.
///
/// No picture yet: the platform's default cursor is all that follows the
/// pointer on X11.
final class X11DragOut implements PlatformDragOut {

    private static final Logger LOG = Logs.of(X11DragOut.class);

    private final Sdl3Backend backend;
    private final X11DragSource source;

    private X11DragOut(Sdl3Backend backend, X11DragSource source) {
        this.backend = backend;
        this.source = source;
    }

    /// Starts `request` from `window`, or answers null where it is not on X11.
    static @Nullable X11DragOut start(
            Sdl3Backend backend, Sdl3Window window, DragRequest request, DragSession session) {
        var display = backend.video().x11Display(window.handle());
        if (display.isEmpty()) {
            return null;
        }
        var own = new HashMap<Long, Sdl3Window>();
        for (var open : backend.windows()) {
            if (open instanceof Sdl3Window sdl) {
                sdl.nativeHandle()
                        .filter(handle -> handle.kind() == NativeHandle.Kind.X11)
                        .ifPresent(handle -> own.put(handle.value(), sdl));
            }
        }
        var offer = DragOutData.withImageFile(request.offer());
        var started = X11DragSource.start(
                display.getAsLong(),
                own::containsKey,
                DragOutData.data(offer),
                DragOutData.actions(offer),
                new Reports(backend, own, session));
        return started.map(drag -> new X11DragOut(backend, drag)).orElse(null);
    }

    @Override
    public void tick() {
        source.tick();
    }

    @Override
    public boolean isClosed() {
        return source.isClosed();
    }

    @Override
    public void close() {
        source.close();
        LOG.debug("a drag out was closed with its backend {}", backend);
    }

    /// The source's reports, as the session hears them, on the next pump.
    private record Reports(Sdl3Backend backend, Map<Long, Sdl3Window> own, DragSession session)
            implements DragSourceListener {

        Reports {
            Objects.requireNonNull(backend, "backend");
            own = Map.copyOf(own);
            Objects.requireNonNull(session, "session");
        }

        @Override
        public void over(long window, int x, int y) {
            var target = own.get(window);
            if (target != null) {
                var at = logical(target, x, y);
                backend.later(() -> session.over(target, at));
            }
        }

        @Override
        public void outside() {
            backend.later(session::outside);
        }

        @Override
        public void delivered(@Nullable DragAction action) {
            var operation = DragOutData.operation(action);
            backend.later(() -> session.ended(new DragEnd.Delivered(operation)));
        }

        @Override
        public void releasedOver(long window, int x, int y) {
            var target = own.get(window);
            if (target == null) {
                backend.later(() -> session.ended(new DragEnd.Cancelled()));
                return;
            }
            var at = logical(target, x, y);
            backend.later(() -> session.ended(new DragEnd.ReleasedOver(target, at, false)));
        }

        @Override
        public void cancelled() {
            backend.later(() -> session.ended(new DragEnd.Cancelled()));
        }

        /// A point in `window`'s physical pixels, in its logical ones.
        private static LogicalPoint logical(Sdl3Window window, int x, int y) {
            var logical = window.size();
            var physical = window.physicalSize();
            var sx = physical.width() == 0 ? 1f : logical.width() / physical.width();
            var sy = physical.height() == 0 ? 1f : logical.height() / physical.height();
            return new LogicalPoint(x * sx, y * sy);
        }
    }
}
