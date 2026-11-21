package dev.goldberry.render.backend.sdl3;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.CocoaDragListener;
import dev.goldberry.natives.desktop.dragout.CocoaDragSource;
import dev.goldberry.natives.desktop.dragout.DragAction;
import dev.goldberry.render.dragout.DragEnd;
import dev.goldberry.render.dragout.DragRequest;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.NativeHandle;

/// A drag out on macOS: an `NSDraggingSession` begun on SDL's content view,
/// the files as file URLs and every other type on one pasteboard item.
///
/// AppKit runs the session from its ordinary event loop, which is SDL's pump,
/// so the source's reports arrive inside SDL and are handed to the next pump,
/// as on X11. AppKit's points are the toolkit's logical pixels; only the
/// vertical axis turns over, since AppKit counts from the bottom.
///
/// SDL's own view is a drop target for files and text, so, as on Windows,
/// the drag takes SDL's drop events while it lasts and through the pump
/// after its end.
final class MacDragOut implements PlatformDragOut {

    private static final Logger LOG = Logs.of(MacDragOut.class);

    /// The session AppKit runs: [CocoaDragSource]'s two answers and its
    /// close, apart so the bookkeeping is tested without a Mac.
    interface Session {

        /// Whether AppKit has said how it ended.
        boolean isEnded();

        /// Lets the source object go.
        void close();
    }

    private final Session source;
    private final DropEchoes echoes = new DropEchoes();

    MacDragOut(Session source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /// Starts `request` from `window`, or answers null where it is not on
    /// macOS or AppKit will not begin a session.
    static @Nullable MacDragOut start(
            Sdl3Backend backend, Sdl3Window window, DragRequest request, DragSession session) {
        var handle = window.nativeHandle();
        if (handle.isEmpty() || handle.get().kind() != NativeHandle.Kind.COCOA) {
            return null;
        }
        var own = DragReports.ownWindows(backend, NativeHandle.Kind.COCOA);
        var offer = DragOutData.withImageFile(request.offer());
        var origin = new CocoaDragSource.Origin(
                handle.get().value(),
                request.pointer().x(),
                request.pointer().y(),
                window.size().height(),
                own.keySet(),
                offer.files(),
                MacDragData.formats(offer),
                DragOutData.actions(offer),
                DragOutData.icon(request.picture(), DragOutData.wholeScale(window.scale())));
        var started = CocoaDragSource.start(origin, new Reports(backend, own, session));
        return started.map(drag -> new MacDragOut(new Session() {
                    @Override
                    public boolean isEnded() {
                        return drag.isClosed();
                    }

                    @Override
                    public void close() {
                        drag.close();
                    }
                }))
                .orElse(null);
    }

    @Override
    public void tick() {
        if (source.isEnded()) {
            echoes.ended();
        }
        echoes.tick();
    }

    @Override
    public boolean isClosed() {
        return echoes.isClosed();
    }

    @Override
    public void close() {
        source.close();
        echoes.close();
        LOG.debug("a drag out on macOS was closed");
    }

    /// Every drop event while the drag lasts and for the pump after its end:
    /// SDL's view saw the application's own offer.
    @Override
    public boolean takesDrop(int type, Sdl3Window window, float x, float y) {
        return !echoes.isClosed();
    }

    /// The source's reports, as the session hears them, on the next pump.
    ///
    /// @param backend where they are queued
    /// @param own     the application's windows by their `NSWindow` addresses
    /// @param session what hears them
    record Reports(Sdl3Backend backend, Map<Long, Sdl3Window> own, DragSession session) implements CocoaDragListener {

        Reports {
            Objects.requireNonNull(backend, "backend");
            own = Map.copyOf(own);
            Objects.requireNonNull(session, "session");
        }

        /// A point from the window's bottom-left corner, in AppKit's points,
        /// from its top-left corner in the toolkit's logical pixels.
        static LogicalPoint fromTop(float height, double x, double yFromBottom) {
            return new LogicalPoint((float) x, (float) (height - yFromBottom));
        }

        @Override
        public void over(long window, double x, double yFromBottom) {
            var target = own.get(window);
            if (target != null) {
                var at = fromTop(target.size().height(), x, yFromBottom);
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
        public void releasedOver(long window, double x, double yFromBottom) {
            var target = own.get(window);
            if (target == null) {
                backend.later(() -> session.ended(new DragEnd.Cancelled()));
                return;
            }
            var at = fromTop(target.size().height(), x, yFromBottom);
            backend.later(() -> session.ended(new DragEnd.ReleasedOver(target, at, false)));
        }

        @Override
        public void cancelled() {
            backend.later(() -> session.ended(new DragEnd.Cancelled()));
        }
    }
}
