package dev.goldberry.natives.desktop.dragout.cocoa;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Upcalls;
import dev.goldberry.natives.desktop.macos.ObjC;

/// `GoldberryDraggingSource`, a class of this process's own: the
/// `NSDraggingSource` of a drag out, and the `NSPasteboardItemDataProvider`
/// that makes its data when a target asks for it.
///
/// One object per drag. Its methods are upcall stubs shared by every object,
/// and each finds the drag it belongs to by its own address. The drag's
/// [Callbacks] answer; nothing they throw reaches AppKit.
///
/// UNVERIFIED: written against AppKit's documentation. The macOS lane's
/// tests send the methods to an object of the class and write its pasteboard
/// item to a pasteboard of their own; a dragging session needs a held button
/// no test can make.
public final class CocoaDraggingSource {

    private static final Logger LOG = Logs.of(CocoaDraggingSource.class);

    /// The class's name in the runtime.
    public static final String CLASS = "GoldberryDraggingSource";

    static final String OPERATION_MASK = "draggingSession:sourceOperationMaskForDraggingContext:";
    static final String MOVED = "draggingSession:movedToPoint:";
    static final String ENDED = "draggingSession:endedAtPoint:operation:";
    static final String PROVIDE = "pasteboard:item:provideDataForType:";
    static final String FINISHED = "pasteboardFinishedWithDataProvider:";

    /// `NSDragOperation (*)(id, SEL, NSDraggingSession *, NSDraggingContext)`.
    static final FunctionDescriptor OPERATION_MASK_SHAPE =
            Upcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));

    /// `void (*)(id, SEL, NSDraggingSession *, NSPoint)`.
    static final FunctionDescriptor MOVED_SHAPE =
            Upcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ObjC.POINT));

    /// `void (*)(id, SEL, NSDraggingSession *, NSPoint, NSDragOperation)`.
    static final FunctionDescriptor ENDED_SHAPE =
            Upcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ObjC.POINT, JAVA_LONG));

    /// `void (*)(id, SEL, NSPasteboard *, NSPasteboardItem *, NSPasteboardType)`.
    static final FunctionDescriptor PROVIDE_SHAPE =
            Upcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /// `void (*)(id, SEL, NSPasteboard *)`.
    static final FunctionDescriptor FINISHED_SHAPE =
            Upcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

    /// What a drag's source object asks of the drag.
    public interface Callbacks {

        /// The operations a target may choose from: an `NSDragOperation` mask.
        ///
        /// @param outside whether the target is another application's
        long operationMask(boolean outside);

        /// The pointer moved to `screen`, in screen points.
        void moved(ObjC.Point screen);

        /// The drag is over: let go at `screen`, the target doing `operation`
        /// (`NSDragOperationNone` where nothing took it).
        void ended(ObjC.Point screen, long operation);

        /// The bytes for pasteboard type `type`, or null for none.
        byte @Nullable [] provide(String type);

        /// The pasteboard will ask for nothing more: the object may go.
        void finished();
    }

    private static final Map<Long, Callbacks> LIVE = new ConcurrentHashMap<>();

    private static volatile @Nullable ObjC runtime;

    private CocoaDraggingSource() {}

    /// A new source object for one drag, retained once; [#dispose] gives it
    /// back.
    public static MemorySegment create(ObjC objc, Callbacks callbacks) {
        Objects.requireNonNull(callbacks, "callbacks");
        runtime = objc;
        var made = objc.send(objc.send(registered(objc), "alloc"), "init");
        if (made.address() == 0) {
            throw new IllegalStateException("the runtime would not make a " + CLASS);
        }
        LIVE.put(made.address(), callbacks);
        return made;
    }

    /// Forgets `source`'s drag and releases it. A method AppKit sends it
    /// afterwards answers as if nothing were offered.
    public static void dispose(ObjC objc, MemorySegment source) {
        if (LIVE.remove(source.address()) != null) {
            objc.sendVoid(source, "release");
        }
    }

    /// Whether `source` still belongs to a drag.
    public static boolean isLive(MemorySegment source) {
        return LIVE.containsKey(source.address());
    }

    /// The class, registered the first time.
    static MemorySegment registered(ObjC objc) {
        return objc.defineClass(
                CLASS,
                "NSObject",
                List.of("NSDraggingSource", "NSPasteboardItemDataProvider"),
                Map.of(
                        OPERATION_MASK, new ObjC.Method(Stubs.OPERATION_MASK_STUB, "Q@:@q"),
                        MOVED, new ObjC.Method(Stubs.MOVED_STUB, "v@:@{CGPoint=dd}"),
                        ENDED, new ObjC.Method(Stubs.ENDED_STUB, "v@:@{CGPoint=dd}Q"),
                        PROVIDE, new ObjC.Method(Stubs.PROVIDE_STUB, "v@:@@@"),
                        FINISHED, new ObjC.Method(Stubs.FINISHED_STUB, "v@:@")));
    }

    private static ObjC.Point point(MemorySegment point) {
        return new ObjC.Point(point.get(JAVA_DOUBLE, 0), point.get(JAVA_DOUBLE, 8));
    }

    /// `draggingSession:sourceOperationMaskForDraggingContext:`. **Called from
    /// Objective-C; must not throw.**
    static long operationMask(MemorySegment self, MemorySegment command, MemorySegment session, long context) {
        try {
            var callbacks = LIVE.get(self.address());
            // NSDraggingContextOutsideApplication is 0.
            return callbacks == null ? 0 : callbacks.operationMask(context == 0);
        } catch (Throwable t) {
            LOG.warn("a drag's operations could not be answered", t);
            return 0;
        }
    }

    /// `draggingSession:movedToPoint:`. **Called from Objective-C; must not
    /// throw.**
    static void moved(MemorySegment self, MemorySegment command, MemorySegment session, MemorySegment at) {
        try {
            var callbacks = LIVE.get(self.address());
            if (callbacks != null) {
                callbacks.moved(point(at));
            }
        } catch (Throwable t) {
            LOG.warn("a drag's move could not be reported", t);
        }
    }

    /// `draggingSession:endedAtPoint:operation:`. **Called from Objective-C;
    /// must not throw.**
    static void ended(
            MemorySegment self, MemorySegment command, MemorySegment session, MemorySegment at, long operation) {
        try {
            var callbacks = LIVE.get(self.address());
            if (callbacks != null) {
                callbacks.ended(point(at), operation);
            }
        } catch (Throwable t) {
            LOG.warn("a drag's ending could not be reported", t);
        }
    }

    /// `pasteboard:item:provideDataForType:`: makes the data and sets it on the
    /// item. **Called from Objective-C; must not throw.**
    static void provide(
            MemorySegment self,
            MemorySegment command,
            MemorySegment pasteboard,
            MemorySegment item,
            MemorySegment type) {
        try {
            var callbacks = LIVE.get(self.address());
            var objc = runtime;
            if (callbacks == null || objc == null) {
                return;
            }
            var name = objc.text(type);
            if (name == null) {
                return;
            }
            var bytes = callbacks.provide(name);
            if (bytes == null) {
                return;
            }
            try (var arena = Arena.ofConfined()) {
                var copy = arena.allocateFrom(JAVA_BYTE, bytes);
                var data = objc.send(objc.cls("NSData"), "dataWithBytes:length:", copy, (long) bytes.length);
                objc.sendBoolean(item, "setData:forType:", data, type);
            }
        } catch (Throwable t) {
            LOG.warn("a drag's data could not be provided", t);
        }
    }

    /// `pasteboardFinishedWithDataProvider:`. **Called from Objective-C; must
    /// not throw.**
    static void finished(MemorySegment self, MemorySegment command, MemorySegment pasteboard) {
        try {
            var callbacks = LIVE.get(self.address());
            if (callbacks != null) {
                callbacks.finished();
            }
        } catch (Throwable t) {
            LOG.warn("a drag's pasteboard could not be let go", t);
        }
    }

    /// The five stubs, made the first time the class is registered.
    private static final class Stubs {

        static final MemorySegment OPERATION_MASK_STUB = stub("operationMask", OPERATION_MASK_SHAPE);
        static final MemorySegment MOVED_STUB = stub("moved", MOVED_SHAPE);
        static final MemorySegment ENDED_STUB = stub("ended", ENDED_SHAPE);
        static final MemorySegment PROVIDE_STUB = stub("provide", PROVIDE_SHAPE);
        static final MemorySegment FINISHED_STUB = stub("finished", FINISHED_SHAPE);

        @SuppressWarnings("restricted")
        private static MemorySegment stub(String name, FunctionDescriptor shape) {
            try {
                var target = MethodHandles.lookup().findStatic(CocoaDraggingSource.class, name, shape.toMethodType());
                return Linker.nativeLinker().upcallStub(target, shape, Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("no " + CLASS + " method " + name, e);
            }
        }
    }
}
