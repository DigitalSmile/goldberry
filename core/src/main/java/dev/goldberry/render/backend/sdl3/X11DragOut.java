package dev.goldberry.render.backend.sdl3;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.X11DragSource;
import dev.goldberry.render.dragout.DragRequest;
import dev.goldberry.render.dragout.DragSession;
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
        var own = DragReports.ownWindows(backend, NativeHandle.Kind.X11);
        var offer = DragOutData.withImageFile(request.offer());
        var started = X11DragSource.start(
                display.getAsLong(),
                own::containsKey,
                DragOutData.data(offer),
                DragOutData.actions(offer),
                new DragReports(backend, own, session));
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
}
