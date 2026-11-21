package dev.goldberry.render.backend.sdl3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.dragout.DragAction;
import dev.goldberry.natives.desktop.dragout.DragSourceListener;
import dev.goldberry.render.dragout.DragEnd;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.NativeHandle;

/// A platform drag source's reports, as the session hears them: the
/// application's own windows found by their native ids, points made logical,
/// and every report handed to the next pump, so that no drop target's
/// `onDrop` runs inside the platform's call.
///
/// X11's source and Windows' report through this; on Windows the "next pump"
/// is the turn the source runs while `DoDragDrop` holds the thread.
///
/// @param backend where the reports are queued
/// @param own     the application's windows by their native ids
/// @param session what hears them
record DragReports(Sdl3Backend backend, Map<Long, Sdl3Window> own, DragSession session) implements DragSourceListener {

    DragReports {
        Objects.requireNonNull(backend, "backend");
        own = Map.copyOf(own);
        Objects.requireNonNull(session, "session");
    }

    /// The application's open windows of `kind`, by their native ids.
    static Map<Long, Sdl3Window> ownWindows(Sdl3Backend backend, NativeHandle.Kind kind) {
        var own = new HashMap<Long, Sdl3Window>();
        for (var open : backend.windows()) {
            if (open instanceof Sdl3Window sdl) {
                sdl.nativeHandle()
                        .filter(handle -> handle.kind() == kind)
                        .ifPresent(handle -> own.put(handle.value(), sdl));
            }
        }
        return own;
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
