package dev.goldberry.render.backend.sdl3;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.input.dragout.DragPicture;
import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.DragIcon;
import dev.goldberry.natives.desktop.dragout.WaylandDragSource;
import dev.goldberry.natives.sdl.event.SdlEventType;
import dev.goldberry.render.dragout.DragRequest;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalPoint;

/// A drag out on Wayland: the offer on a `wl_data_source` of the toolkit's
/// own, dragged with SDL's data device, and where it is judged from SDL's own
/// drop events over the application's windows ([WaylandDropTracker]).
///
/// SDL's drop events are this drag's while it lasts: SDL's data device is the
/// target over the application's own windows, so the position it reports is
/// the drag's, and the drop it would deliver is the application's own offer
/// coming back. They are taken here and never reach a window as a
/// `FileDrop`. Each is handed to the next pump, like every other report.
final class WaylandDragOut implements PlatformDragOut {

    private static final Logger LOG = Logs.of(WaylandDragOut.class);

    private final Sdl3Backend backend;
    private final WaylandDragSource source;
    private final WaylandDropTracker tracker;

    private WaylandDragOut(Sdl3Backend backend, WaylandDragSource source, WaylandDropTracker tracker) {
        this.backend = backend;
        this.source = source;
        this.tracker = tracker;
    }

    /// Starts `request` from `window`, or answers null where it is not on
    /// Wayland or the compositor cannot carry it.
    static @Nullable WaylandDragOut start(
            Sdl3Backend backend, Sdl3Window window, DragRequest request, DragSession session) {
        var origin = backend.video().waylandDragOrigin(window.handle());
        if (origin.isEmpty()) {
            return null;
        }
        var offer = DragOutData.withImageFile(request.offer());
        var tracker = new WaylandDropTracker(session);
        var started = WaylandDragSource.start(
                origin.get(),
                DragOutData.data(offer),
                DragOutData.actions(offer),
                icon(request.picture(), window.scale()),
                tracker);
        return started.map(drag -> new WaylandDragOut(backend, drag, tracker)).orElse(null);
    }

    /// The picture as a Wayland buffer takes it, at the window's scale rounded
    /// to a whole one, as a buffer's must be: 1 where the window's is
    /// fractional, so the picture is shown at its pixel size there.
    static @Nullable DragIcon icon(@Nullable DragPicture picture, DisplayScale scale) {
        return DragOutData.icon(picture, DragOutData.wholeScale(scale));
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

    @Override
    public boolean takesDrop(int type, Sdl3Window window, float x, float y) {
        Objects.requireNonNull(window, "window");
        if (source.isClosed()) {
            return false;
        }
        if (type == SdlEventType.DROP_POSITION.value()) {
            var at = new LogicalPoint(x, y);
            backend.later(() -> tracker.over(window, at));
            return true;
        }
        if (type == SdlEventType.DROP_COMPLETE.value()) {
            backend.later(() -> tracker.left(window));
            return true;
        }
        // DROP_BEGIN carries nothing, and a DROP_FILE or DROP_TEXT is the
        // application's own offer coming back through SDL's target.
        return type == SdlEventType.DROP_BEGIN.value()
                || type == SdlEventType.DROP_FILE.value()
                || type == SdlEventType.DROP_TEXT.value();
    }
}
