package dev.goldberry.natives.desktop.dragout;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.cocoa.CocoaDragItems;
import dev.goldberry.natives.desktop.dragout.cocoa.CocoaDraggingSource;
import dev.goldberry.natives.desktop.macos.ObjC;

/// A drag out on macOS: an `NSDraggingSession` begun on SDL's content view,
/// with a source object of the toolkit's own.
///
/// Not modal. [#start] begins the session and returns; AppKit runs it from
/// the ordinary event loop, which is SDL's pump, and tells the source where
/// the pointer went and how the drag ended. The listener hears it there, so a
/// caller hands what it hears to its own queue before running application
/// code. Files go as file URLs, one dragging item each; every other type goes
/// on one pasteboard item whose data is made when a target asks for it.
///
/// The drag is over when AppKit says it ended ([#isClosed]), but its source
/// object stays until the pasteboard says it will ask for nothing more: a
/// target may read the data as late as that. [#close] lets it go at once.
///
/// UNVERIFIED: written against AppKit's documentation. The pure parts are
/// tested everywhere, the class and the pasteboard item on the macOS lane;
/// the session itself needs a held button no test can make.
public final class CocoaDragSource implements AutoCloseable {

    private static final Logger LOG = Logs.of(CocoaDragSource.class);

    /// `NSEventTypeKeyDown`: what the current event is when Escape ended a
    /// session.
    static final long KEY_DOWN = 10;

    private final ObjC objc;
    private final MemorySegment source;
    private final CocoaDragTracker tracker;
    private boolean disposed;

    private CocoaDragSource(ObjC objc, MemorySegment source, CocoaDragTracker tracker) {
        this.objc = objc;
        this.source = source;
        this.tracker = tracker;
    }

    /// What a drag starts from: the window, where the pointer is in it, and
    /// what it offers.
    ///
    /// @param window        the `NSWindow` the press was in, as an address
    /// @param pointerX      the pointer, points from the content's left edge
    /// @param pointerYTop   the pointer, points from the content's top edge
    /// @param contentHeight the content's height in points
    /// @param own           the application's own windows, as addresses
    /// @param files         files that exist, absolute
    /// @param formats       every other type, most specific first
    /// @param actions       what a target may do with it
    /// @param picture       what follows the pointer, or null for AppKit's
    ///                      default
    public record Origin(
            long window,
            double pointerX,
            double pointerYTop,
            double contentHeight,
            Set<Long> own,
            List<Path> files,
            List<PasteboardFormat> formats,
            Set<DragAction> actions,
            @Nullable DragIcon picture) {

        public Origin {
            own = Set.copyOf(own);
            files = List.copyOf(files);
            formats = List.copyOf(formats);
            actions = Set.copyOf(actions);
            if (files.isEmpty() && formats.isEmpty()) {
                throw new IllegalArgumentException("a drag offers something");
            }
        }
    }

    /// Begins a drag, or answers empty off macOS, or where AppKit will not
    /// begin one from the event being handled.
    ///
    /// @param listener told where the pointer is over own windows and how the
    ///                 drag ended, from inside AppKit's event handling
    public static Optional<CocoaDragSource> start(Origin origin, CocoaDragListener listener) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(listener, "listener");
        var runtime = ObjC.get();
        if (runtime.isEmpty()) {
            return Optional.empty();
        }
        var objc = runtime.get();
        ObjC.loadFramework("AppKit");
        ObjC.loadFramework("UniformTypeIdentifiers");
        return objc.autoreleased(() -> begin(objc, origin, listener));
    }

    private static Optional<CocoaDragSource> begin(ObjC objc, Origin origin, CocoaDragListener listener) {
        var window = MemorySegment.ofAddress(origin.window());
        var view = objc.send(window, "contentView");
        if (view.address() == 0) {
            return Optional.empty();
        }
        var byNumber = new HashMap<Long, Long>();
        for (var own : origin.own()) {
            byNumber.put(objc.sendLong(MemorySegment.ofAddress(own), "windowNumber"), own);
        }
        var data = provided(origin.formats(), mime -> identifier(objc, mime));
        var tracker = new CocoaDragTracker(listener);
        var mask = CocoaDragTracker.mask(origin.actions());
        var drag = new CocoaDragSource[1];
        var source = CocoaDraggingSource.create(objc, new CocoaDraggingSource.Callbacks() {
            @Override
            public long operationMask(boolean outside) {
                return mask;
            }

            @Override
            public void moved(ObjC.Point screen) {
                tracker.moved(locate(objc, byNumber, screen));
            }

            @Override
            public void ended(ObjC.Point screen, long operation) {
                var event = objc.send(objc.send(objc.cls("NSApplication"), "sharedApplication"), "currentEvent");
                var escaped = event.address() != 0 && objc.sendLong(event, "type") == KEY_DOWN;
                tracker.ended(locate(objc, byNumber, screen), operation, escaped);
                if (data.isEmpty() && drag[0] != null) {
                    // Files alone: no pasteboard item will say it is finished.
                    drag[0].close();
                }
            }

            @Override
            public void finished() {
                if (drag[0] != null && tracker.isEnded()) {
                    drag[0].close();
                }
            }

            @Override
            public byte @Nullable [] provide(String type) {
                var bytes = data.get(type);
                return bytes == null ? null : bytes.get();
            }
        });
        try {
            var item = data.isEmpty() ? null : CocoaDragItems.pasteboardItem(objc, source, List.copyOf(data.keySet()));
            var picture = origin.picture();
            var image = picture == null
                    ? null
                    : CocoaDragItems.image(
                            objc,
                            picture.width(),
                            picture.height(),
                            picture.stride(),
                            picture.pixels(),
                            picture.scale());
            var scale = picture == null ? 1 : picture.scale();
            var frame = CocoaDragTracker.frame(
                    origin.pointerX(),
                    origin.pointerYTop(),
                    origin.contentHeight(),
                    objc.sendBoolean(view, "isFlipped"),
                    picture == null ? 1 : (double) picture.width() / scale,
                    picture == null ? 1 : (double) picture.height() / scale,
                    picture == null ? 0 : (double) picture.hotspotX() / scale,
                    picture == null ? 0 : (double) picture.hotspotY() / scale);
            var items = CocoaDragItems.draggingItems(objc, origin.files(), item, frame, image);
            var event = objc.send(objc.send(objc.cls("NSApplication"), "sharedApplication"), "currentEvent");
            var session = objc.send(view, "beginDraggingSessionWithItems:event:source:", items, event, source);
            if (session.address() == 0) {
                LOG.debug("AppKit would not begin a dragging session from the current event");
                CocoaDraggingSource.dispose(objc, source);
                return Optional.empty();
            }
            objc.sendVoid(session, "setAnimatesToStartingPositionsOnCancelOrFail:", true);
            drag[0] = new CocoaDragSource(objc, source, tracker);
            return Optional.of(drag[0]);
        } catch (RuntimeException e) {
            CocoaDraggingSource.dispose(objc, source);
            throw e;
        }
    }

    /// Where the pointer is: over one of the application's windows, in its
    /// coordinates, or null over anything else.
    private static CocoaDragTracker.@Nullable Over locate(ObjC objc, Map<Long, Long> byNumber, ObjC.Point screen) {
        var window = byNumber.get(CocoaDragItems.windowNumberAt(objc, screen));
        if (window == null) {
            return null;
        }
        var at = CocoaDragItems.fromScreen(objc, MemorySegment.ofAddress(window), screen);
        return new CocoaDragTracker.Over(window, at.x(), at.y());
    }

    /// The identifier the system has for `mime`, a dynamic one where it
    /// registers none, or the MIME type itself where it will not say.
    private static String identifier(ObjC objc, String mime) {
        var type = objc.send(objc.cls("UTType"), "typeWithMIMEType:", objc.string(mime));
        var identifier = type.address() == 0 ? null : objc.text(objc.send(type, "identifier"));
        return identifier == null ? mime : identifier;
    }

    /// `formats` by the pasteboard type each is offered under, MIME types
    /// turned into identifiers, in order. A type offered twice is offered
    /// once, as first given.
    static Map<String, Supplier<byte[]>> provided(List<PasteboardFormat> formats, Function<String, String> identify) {
        var provided = new LinkedHashMap<String, Supplier<byte[]>>();
        for (var format : formats) {
            var type = format.mime() ? identify.apply(format.type()) : format.type();
            provided.putIfAbsent(type, format.bytes());
        }
        return provided;
    }

    /// Whether AppKit has said how the drag ended, or it was closed.
    public boolean isClosed() {
        return disposed || tracker.isEnded();
    }

    /// Forgets the drag: AppKit's later reports and data requests find
    /// nothing. A session still running carries on without the application.
    @Override
    public void close() {
        if (disposed) {
            return;
        }
        disposed = true;
        CocoaDraggingSource.dispose(objc, source);
    }
}
