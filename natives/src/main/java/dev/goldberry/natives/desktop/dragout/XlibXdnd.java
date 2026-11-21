package dev.goldberry.natives.desktop.dragout;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongPredicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.Upcalls;

/// [XdndTransport] over libX11, on a `Display` somebody else opened: SDL's,
/// for a real drag, or a test's.
///
/// The source window is an unmapped InputOnly child of the root. While one of
/// these is open, an X error handler of its own swallows `BadWindow` on its
/// display: a target can be destroyed between a query and the next request,
/// and Xlib's default answer to an error is to exit the process. Everything
/// else goes to the handler that was there before.
public final class XlibXdnd implements XdndTransport, AutoCloseable {

    private static final Logger LOG = Logs.of(XlibXdnd.class);

    static final String SONAME = "libX11.so.6";

    // Event types and the XEvent union, as an LP64 compiler lays them out.
    static final long EVENT_BYTES = 192;
    static final int KEY_PRESS = 2;
    static final int SELECTION_REQUEST = 30;
    static final int SELECTION_NOTIFY = 31;
    static final int CLIENT_MESSAGE = 33;
    static final long ANY_DISPLAY = 24;
    static final long ANY_WINDOW = 32;
    static final long KEY_KEYCODE = 84;
    static final long CLIENT_TYPE = 40;
    static final long CLIENT_FORMAT = 48;
    static final long CLIENT_DATA = 56;
    static final long REQUEST_OWNER = 32;
    static final long REQUEST_REQUESTOR = 40;
    static final long REQUEST_SELECTION = 48;
    static final long REQUEST_TARGET = 56;
    static final long REQUEST_PROPERTY = 64;
    static final long REQUEST_TIME = 72;

    // XErrorEvent: the display at 8, the resource at 16, the code at 32.
    private static final long ERROR_DISPLAY = 8;
    private static final long ERROR_CODE = 32;
    private static final int BAD_WINDOW = 3;

    private static final int BUTTON1_MASK = 1 << 8;
    private static final int SHIFT_MASK = 1;
    private static final int CONTROL_MASK = 1 << 2;
    private static final int INPUT_ONLY = 2;
    private static final int PROP_MODE_REPLACE = 0;
    private static final long XA_WINDOW = 33;
    private static final long XK_ESCAPE = 0xff1b;
    private static final int MAX_DEPTH = 32;

    private static final FunctionDescriptor ERROR_HANDLER =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // Every shape this class sends, declared here rather than in [Xlib] so that
    // initialising this class records them where libX11 is missing.
    private static final FunctionDescriptor J_PPI =
            Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT));
    private static final FunctionDescriptor P_PJ =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG));
    private static final FunctionDescriptor I_P = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final FunctionDescriptor J_P = Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final FunctionDescriptor P_P = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final FunctionDescriptor I_PI =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final FunctionDescriptor I_PJ =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    private static final FunctionDescriptor B_PJ =
            Downcalls.describe(FunctionDescriptor.of(JAVA_BYTE, ADDRESS, JAVA_LONG));
    private static final FunctionDescriptor QUERY_POINTER = Downcalls.describe(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor TRANSLATE = Downcalls.describe(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor GET_PROPERTY = Downcalls.describe(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS,
            ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor CREATE_WINDOW = Downcalls.describe(FunctionDescriptor.of(
            JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
            ADDRESS, JAVA_LONG, ADDRESS));
    private static final FunctionDescriptor SET_OWNER =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    private static final FunctionDescriptor CHANGE_PROPERTY = Downcalls.describe(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
    private static final FunctionDescriptor SEND_EVENT =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_LONG, ADDRESS));

    /// The displays with a transport open, by address, for the error handler.
    private static final Map<Long, XlibXdnd> OPEN = new HashMap<>();

    private static MemorySegment previousHandler = MemorySegment.NULL;
    private static @Nullable MemorySegment handlerStub;

    private final Xlib xlib;
    private final MemorySegment display;
    private final long root;
    private final long source;
    private final LongPredicate own;
    private final Map<String, Long> atoms = new HashMap<>();
    private final long xdndAware;
    private final long xdndProxy;
    private final int escape;
    private boolean closed;

    private XlibXdnd(Xlib xlib, MemorySegment display, LongPredicate own) {
        this.xlib = xlib;
        this.display = display;
        this.own = own;
        this.root = (long) xlib.call(xlib.defaultRootWindow, display);
        this.source = (long) xlib.call(
                xlib.createWindow,
                display,
                root,
                -10,
                -10,
                1,
                1,
                0,
                0,
                INPUT_ONLY,
                MemorySegment.NULL,
                0L,
                MemorySegment.NULL);
        this.xdndAware = atom("XdndAware");
        this.xdndProxy = atom("XdndProxy");
        this.escape = Byte.toUnsignedInt((byte) xlib.call(xlib.keysymToKeycode, display, XK_ESCAPE));
    }

    /// A transport on `display`, or empty where libX11 will not load.
    ///
    /// @param display a `Display*` that stays open longer than this
    /// @param own     whether a window id is one of the application's own
    static Optional<XlibXdnd> open(MemorySegment display, LongPredicate own) {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(own, "own");
        var xlib = Xlib.get();
        if (xlib == null || display.address() == 0) {
            return Optional.empty();
        }
        var transport = new XlibXdnd(xlib, display, own);
        transport.watchErrors();
        return Optional.of(transport);
    }

    @Override
    public long sourceWindow() {
        return source;
    }

    @Override
    public long atom(String name) {
        return atoms.computeIfAbsent(name, key -> {
            try (var arena = Arena.ofConfined()) {
                return (long) xlib.call(xlib.internAtom, display, arena.allocateFrom(key), 0);
            }
        });
    }

    @Override
    @SuppressWarnings("restricted")
    public @Nullable String atomName(long atom) {
        if (atom == 0) {
            return null;
        }
        var name = (MemorySegment) xlib.call(xlib.getAtomName, display, atom);
        if (name.address() == 0) {
            return null;
        }
        try {
            return name.reinterpret(Integer.MAX_VALUE).getString(0);
        } finally {
            xlib.call(xlib.free, name);
        }
    }

    @Override
    public Pointer pointer() {
        try (var arena = Arena.ofConfined()) {
            var rootReturn = arena.allocate(JAVA_LONG);
            var child = arena.allocate(JAVA_LONG);
            var rootX = arena.allocate(JAVA_INT);
            var rootY = arena.allocate(JAVA_INT);
            var winX = arena.allocate(JAVA_INT);
            var winY = arena.allocate(JAVA_INT);
            var mask = arena.allocate(JAVA_INT);
            xlib.call(xlib.queryPointer, display, root, rootReturn, child, rootX, rootY, winX, winY, mask);
            var state = mask.get(JAVA_INT, 0);
            return new Pointer(
                    rootX.get(JAVA_INT, 0),
                    rootY.get(JAVA_INT, 0),
                    (state & BUTTON1_MASK) != 0,
                    (state & SHIFT_MASK) != 0,
                    (state & CONTROL_MASK) != 0);
        }
    }

    @Override
    public @Nullable Target targetAt(int rootX, int rootY) {
        var window = root;
        for (var depth = 0; depth < MAX_DEPTH; depth++) {
            var child = childAt(window, rootX, rootY);
            if (child == 0) {
                return null;
            }
            window = child;
            if (own.test(window)) {
                var local = translate(window, rootX, rootY);
                return new Target(window, 0, true, local[0], local[1]);
            }
            var version = awareVersion(window);
            if (version >= 3) {
                var proxy = windowProperty(window, xdndProxy);
                return new Target(proxy == 0 ? window : proxy, version, false, 0, 0);
            }
        }
        return null;
    }

    @Override
    public void send(long window, long type, long[] data) {
        try (var arena = Arena.ofConfined()) {
            var event = arena.allocate(EVENT_BYTES);
            event.set(JAVA_INT, 0, CLIENT_MESSAGE);
            event.set(ADDRESS, ANY_DISPLAY, display);
            event.set(JAVA_LONG, ANY_WINDOW, window);
            event.set(JAVA_LONG, CLIENT_TYPE, type);
            event.set(JAVA_INT, CLIENT_FORMAT, 32);
            for (var i = 0; i < 5 && i < data.length; i++) {
                event.set(JAVA_LONG, CLIENT_DATA + i * 8L, data[i]);
            }
            xlib.call(xlib.sendEvent, display, window, 0, 0L, event);
        }
    }

    @Override
    public void typeList(long[] list) {
        writeProperty(source, atom("XdndTypeList"), XdndSource.XA_ATOM, 32, XdndSource.longs(list));
    }

    @Override
    public void ownSelection(long selection) {
        xlib.call(xlib.setSelectionOwner, display, selection, source, 0L);
    }

    @Override
    public void writeProperty(long window, long property, long type, int format, byte[] data) {
        var elements = format == 32 ? data.length / Long.BYTES : format == 16 ? data.length / 2 : data.length;
        try (var arena = Arena.ofConfined()) {
            var bytes = arena.allocate(Math.max(1, data.length));
            MemorySegment.copy(data, 0, bytes, JAVA_BYTE, 0, data.length);
            xlib.call(xlib.changeProperty, display, window, property, type, format, PROP_MODE_REPLACE, bytes, elements);
        }
    }

    @Override
    public void notifySelection(long requestor, long selection, long target, long property, long time) {
        try (var arena = Arena.ofConfined()) {
            var event = arena.allocate(EVENT_BYTES);
            event.set(JAVA_INT, 0, SELECTION_NOTIFY);
            event.set(ADDRESS, ANY_DISPLAY, display);
            event.set(JAVA_LONG, 32, requestor);
            event.set(JAVA_LONG, 40, selection);
            event.set(JAVA_LONG, 48, target);
            event.set(JAVA_LONG, 56, property);
            event.set(JAVA_LONG, 64, time);
            xlib.call(xlib.sendEvent, display, requestor, 0, 0L, event);
        }
    }

    @Override
    public long maxPropertyBytes() {
        var units = (long) xlib.call(xlib.extendedMaxRequestSize, display);
        if (units == 0) {
            units = (long) xlib.call(xlib.maxRequestSize, display);
        }
        // Units of four bytes, less the request's own header.
        return Math.max(0, units * 4 - 64);
    }

    @Override
    public int escapeKeycode() {
        return escape;
    }

    @Override
    public void flush() {
        xlib.call(xlib.flush, display);
    }

    /// Destroys the source window and gives the error handler back.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        xlib.call(xlib.destroyWindow, display, source);
        xlib.call(xlib.sync, display, 0);
        synchronized (OPEN) {
            OPEN.remove(display.address());
            if (OPEN.isEmpty()) {
                xlib.call(xlib.setErrorHandler, previousHandler);
                previousHandler = MemorySegment.NULL;
            }
        }
    }

    /// Reads an `XEvent` into what the protocol acts on.
    static XdndEvent decode(MemorySegment raw) {
        var event = reinterpret(raw);
        return switch (event.get(JAVA_INT, 0)) {
            case CLIENT_MESSAGE -> {
                if (event.get(JAVA_INT, CLIENT_FORMAT) != 32) {
                    yield new XdndEvent.Other();
                }
                var data = new long[5];
                for (var i = 0; i < 5; i++) {
                    data[i] = event.get(JAVA_LONG, CLIENT_DATA + i * 8L);
                }
                yield new XdndEvent.ClientMessage(
                        event.get(JAVA_LONG, ANY_WINDOW), event.get(JAVA_LONG, CLIENT_TYPE), data);
            }
            case SELECTION_REQUEST ->
                new XdndEvent.SelectionRequest(
                        event.get(JAVA_LONG, REQUEST_OWNER),
                        event.get(JAVA_LONG, REQUEST_REQUESTOR),
                        event.get(JAVA_LONG, REQUEST_SELECTION),
                        event.get(JAVA_LONG, REQUEST_TARGET),
                        event.get(JAVA_LONG, REQUEST_PROPERTY),
                        event.get(JAVA_LONG, REQUEST_TIME));
            case KEY_PRESS -> new XdndEvent.KeyPress(event.get(JAVA_INT, KEY_KEYCODE));
            default -> new XdndEvent.Other();
        };
    }

    // Restricted: the hook hands over a bare `XEvent*`, whose extent is the
    // union's.
    @SuppressWarnings("restricted")
    private static MemorySegment reinterpret(MemorySegment raw) {
        return raw.reinterpret(EVENT_BYTES);
    }

    /// The child of `window` under the root point, or 0.
    private long childAt(long window, int rootX, int rootY) {
        try (var arena = Arena.ofConfined()) {
            var x = arena.allocate(JAVA_INT);
            var y = arena.allocate(JAVA_INT);
            var child = arena.allocate(JAVA_LONG);
            var ok = (int) xlib.call(xlib.translateCoordinates, display, root, window, rootX, rootY, x, y, child);
            return ok == 0 ? 0 : child.get(JAVA_LONG, 0);
        }
    }

    /// The root point in `window`'s own coordinates.
    private int[] translate(long window, int rootX, int rootY) {
        try (var arena = Arena.ofConfined()) {
            var x = arena.allocate(JAVA_INT);
            var y = arena.allocate(JAVA_INT);
            var child = arena.allocate(JAVA_LONG);
            xlib.call(xlib.translateCoordinates, display, root, window, rootX, rootY, x, y, child);
            return new int[] {x.get(JAVA_INT, 0), y.get(JAVA_INT, 0)};
        }
    }

    /// The XDND version `window` announces, or 0 when it takes no drags.
    private int awareVersion(long window) {
        var value = longProperty(window, xdndAware, XdndSource.XA_ATOM);
        return value == null ? 0 : (int) (long) value;
    }

    /// A window-valued property, or 0.
    private long windowProperty(long window, long property) {
        var value = longProperty(window, property, XA_WINDOW);
        return value == null ? 0 : value;
    }

    @SuppressWarnings("restricted")
    private @Nullable Long longProperty(long window, long property, long type) {
        try (var arena = Arena.ofConfined()) {
            var actualType = arena.allocate(JAVA_LONG);
            var actualFormat = arena.allocate(JAVA_INT);
            var items = arena.allocate(JAVA_LONG);
            var after = arena.allocate(JAVA_LONG);
            var value = arena.allocate(ADDRESS);
            var status = (int) xlib.call(
                    xlib.getWindowProperty,
                    display,
                    window,
                    property,
                    0L,
                    1L,
                    0,
                    type,
                    actualType,
                    actualFormat,
                    items,
                    after,
                    value);
            var pointer = value.get(ADDRESS, 0);
            try {
                if (status != 0
                        || actualType.get(JAVA_LONG, 0) != type
                        || items.get(JAVA_LONG, 0) < 1
                        || pointer.address() == 0) {
                    return null;
                }
                return pointer.reinterpret(Long.BYTES).get(JAVA_LONG, 0);
            } finally {
                if (pointer.address() != 0) {
                    xlib.call(xlib.free, pointer);
                }
            }
        }
    }

    private void watchErrors() {
        synchronized (OPEN) {
            if (OPEN.isEmpty()) {
                previousHandler = (MemorySegment) xlib.call(xlib.setErrorHandler, handler());
            }
            OPEN.put(display.address(), this);
        }
    }

    /// `int (Display*, XErrorEvent*)`. **Called from Xlib; must not throw.**
    @SuppressWarnings("restricted")
    static int onError(MemorySegment display, MemorySegment error) {
        try {
            var report = error.reinterpret(EVENT_BYTES);
            var code = Byte.toUnsignedInt(report.get(JAVA_BYTE, ERROR_CODE));
            boolean ours;
            synchronized (OPEN) {
                ours = OPEN.containsKey(report.get(ADDRESS, ERROR_DISPLAY).address());
            }
            if (ours && code == BAD_WINDOW) {
                LOG.debug("a window went away during a drag");
                return 0;
            }
            var previous = previousHandler;
            if (previous.address() != 0) {
                return (int) Xlib.FORWARD.invokeExact(previous, display, error);
            }
        } catch (Throwable t) {
            // Nothing may unwind into Xlib.
        }
        return 0;
    }

    @SuppressWarnings("restricted")
    private static synchronized MemorySegment handler() {
        if (handlerStub == null) {
            try {
                var target = MethodHandles.lookup()
                        .findStatic(
                                XlibXdnd.class,
                                "onError",
                                MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
                handlerStub = Linker.nativeLinker().upcallStub(target, ERROR_HANDLER, Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("the X error handler is missing", e);
            }
        }
        return handlerStub;
    }

    /// libX11's functions an XDND source calls, bound once, or null without it.
    static final class Xlib {

        /// Calls the handler that was installed before this one.
        static final MethodHandle FORWARD = Downcalls.link(ERROR_HANDLER);

        private static final @Nullable Xlib INSTANCE = find();

        final MethodHandle internAtom;
        final MethodHandle getAtomName;
        final MethodHandle free;
        final MethodHandle queryPointer;
        final MethodHandle translateCoordinates;
        final MethodHandle getWindowProperty;
        final MethodHandle createWindow;
        final MethodHandle destroyWindow;
        final MethodHandle setSelectionOwner;
        final MethodHandle changeProperty;
        final MethodHandle sendEvent;
        final MethodHandle flush;
        final MethodHandle sync;
        final MethodHandle defaultRootWindow;
        final MethodHandle extendedMaxRequestSize;
        final MethodHandle maxRequestSize;
        final MethodHandle keysymToKeycode;
        final MethodHandle setErrorHandler;

        private Xlib(SymbolLookup lookup) {
            internAtom = bind(lookup, "XInternAtom", J_PPI);
            getAtomName = bind(lookup, "XGetAtomName", P_PJ);
            free = bind(lookup, "XFree", I_P);
            queryPointer = bind(lookup, "XQueryPointer", QUERY_POINTER);
            translateCoordinates = bind(lookup, "XTranslateCoordinates", TRANSLATE);
            getWindowProperty = bind(lookup, "XGetWindowProperty", GET_PROPERTY);
            createWindow = bind(lookup, "XCreateWindow", CREATE_WINDOW);
            destroyWindow = bind(lookup, "XDestroyWindow", I_PJ);
            setSelectionOwner = bind(lookup, "XSetSelectionOwner", SET_OWNER);
            changeProperty = bind(lookup, "XChangeProperty", CHANGE_PROPERTY);
            sendEvent = bind(lookup, "XSendEvent", SEND_EVENT);
            flush = bind(lookup, "XFlush", I_P);
            sync = bind(lookup, "XSync", I_PI);
            defaultRootWindow = bind(lookup, "XDefaultRootWindow", J_P);
            extendedMaxRequestSize = bind(lookup, "XExtendedMaxRequestSize", J_P);
            maxRequestSize = bind(lookup, "XMaxRequestSize", J_P);
            keysymToKeycode = bind(lookup, "XKeysymToKeycode", B_PJ);
            setErrorHandler = bind(lookup, "XSetErrorHandler", P_P);
        }

        static @Nullable Xlib get() {
            return INSTANCE;
        }

        private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
            var address =
                    lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError(SONAME + " does not export " + name));
            return Downcalls.link(shape).bindTo(address);
        }

        // Restricted for XlibHotkeys' reason: opening a system library is what
        // this module exists to do.
        @SuppressWarnings("restricted")
        private static @Nullable Xlib find() {
            try {
                return new Xlib(SymbolLookup.libraryLookup(SONAME, Arena.global()));
            } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                LOG.debug("no {} ({}); no drag out on X11", SONAME, e.getMessage());
                return null;
            }
        }

        /// Calls a bound function.
        Object call(MethodHandle handle, Object... arguments) {
            try {
                var result = handle.invokeWithArguments(arguments);
                return result == null ? Boolean.TRUE : result;
            } catch (Throwable e) {
                throw new IllegalStateException("an Xlib call failed", e);
            }
        }
    }
}
