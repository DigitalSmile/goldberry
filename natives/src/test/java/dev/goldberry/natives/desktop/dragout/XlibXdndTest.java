package dev.goldberry.natives.desktop.dragout;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.natives.NativePlatform;

/// The XDND source's transport against a real X server: messages sent with
/// `XSendEvent` arrive as the protocol reads them, a selection request is
/// answered with the bytes of the type asked for, and a message to a window
/// that is gone does not take the process down.
///
/// Two connections and two unmapped windows, so nothing appears on a desktop
/// and the real pointer is never moved. The server is an `Xvfb` of the test's
/// own where this machine has one, and `$DISPLAY` otherwise.
@DisplayName("XDND over libX11")
@Timeout(60)
class XlibXdndTest {

    private static final int INPUT_ONLY = 2;

    private static @Nullable Process xvfb;
    private static @Nullable String display;

    private TestX test;
    private MemorySegment sourceDisplay = MemorySegment.NULL;
    private MemorySegment targetDisplay = MemorySegment.NULL;
    private @Nullable XlibXdnd transport;

    /// The calls the target side makes, which the transport never needs.
    private static final class TestX {

        private static final Linker LINKER = Linker.nativeLinker();

        final MethodHandle openDisplay;
        final MethodHandle closeDisplay;
        final MethodHandle nextEvent;
        final MethodHandle createWindow;
        final MethodHandle defaultRoot;
        final MethodHandle internAtom;
        final MethodHandle changeProperty;
        final MethodHandle convertSelection;
        final MethodHandle getWindowProperty;
        final MethodHandle sync;
        final MethodHandle free;

        @SuppressWarnings("restricted")
        TestX() {
            var lib = SymbolLookup.libraryLookup("libX11.so.6", Arena.global());
            openDisplay = bind(lib, "XOpenDisplay", FunctionDescriptor.of(ADDRESS, ADDRESS));
            closeDisplay = bind(lib, "XCloseDisplay", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            nextEvent = bind(lib, "XNextEvent", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
            createWindow = bind(
                    lib,
                    "XCreateWindow",
                    FunctionDescriptor.of(
                            JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                            JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
            defaultRoot = bind(lib, "XDefaultRootWindow", FunctionDescriptor.of(JAVA_LONG, ADDRESS));
            internAtom = bind(lib, "XInternAtom", FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT));
            changeProperty = bind(
                    lib,
                    "XChangeProperty",
                    FunctionDescriptor.of(
                            JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
            convertSelection = bind(
                    lib,
                    "XConvertSelection",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
            getWindowProperty = bind(
                    lib,
                    "XGetWindowProperty",
                    FunctionDescriptor.of(
                            JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, ADDRESS,
                            ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            sync = bind(lib, "XSync", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
            free = bind(lib, "XFree", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        }

        @SuppressWarnings("restricted")
        private static MethodHandle bind(SymbolLookup lib, String name, FunctionDescriptor shape) {
            return LINKER.downcallHandle(lib.find(name).orElseThrow(), shape);
        }

        Object call(MethodHandle handle, Object... arguments) {
            try {
                return handle.invokeWithArguments(arguments);
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }

        long atom(MemorySegment display, String name) {
            try (var arena = Arena.ofConfined()) {
                return (long) call(internAtom, display, arena.allocateFrom(name), 0);
            }
        }

        long window(MemorySegment display) {
            var root = (long) call(defaultRoot, display);
            return (long) call(
                    createWindow,
                    display,
                    root,
                    0,
                    0,
                    1,
                    1,
                    0,
                    0,
                    INPUT_ONLY,
                    MemorySegment.NULL,
                    0L,
                    MemorySegment.NULL);
        }

        /// The next event on `display`, blocking, as the transport reads it.
        XdndEvent next(MemorySegment display, Arena arena) {
            var event = arena.allocate(XlibXdnd.EVENT_BYTES);
            call(nextEvent, display, event);
            return XlibXdnd.decode(event);
        }

        /// The raw type of the next event on `display`, and the event.
        MemorySegment nextRaw(MemorySegment display, Arena arena) {
            var event = arena.allocate(XlibXdnd.EVENT_BYTES);
            call(nextEvent, display, event);
            return event;
        }

        @SuppressWarnings("restricted")
        byte[] property(MemorySegment display, long window, long property) {
            try (var arena = Arena.ofConfined()) {
                var type = arena.allocate(JAVA_LONG);
                var format = arena.allocate(JAVA_INT);
                var items = arena.allocate(JAVA_LONG);
                var after = arena.allocate(JAVA_LONG);
                var value = arena.allocate(ADDRESS);
                call(
                        getWindowProperty,
                        display,
                        window,
                        property,
                        0L,
                        1L << 20,
                        0,
                        0L,
                        type,
                        format,
                        items,
                        after,
                        value);
                var pointer = value.get(ADDRESS, 0);
                var count = items.get(JAVA_LONG, 0);
                var bytes = pointer.reinterpret(count).toArray(JAVA_BYTE);
                call(free, pointer);
                return bytes;
            }
        }
    }

    @BeforeAll
    static void server() throws IOException {
        if (NativePlatform.current().os() != NativePlatform.OperatingSystem.LINUX) {
            return;
        }
        var server = Path.of("/usr/bin/Xvfb");
        if (Files.isExecutable(server)) {
            var process = new ProcessBuilder(server.toString(), "-displayfd", "1", "-nolisten", "tcp")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            var number = reader.readLine();
            if (number != null && !number.isBlank()) {
                xvfb = process;
                display = ":" + number.trim();
                return;
            }
            process.destroyForcibly();
        }
        var desktop = System.getenv("DISPLAY");
        if (desktop != null && !desktop.isBlank()) {
            display = desktop;
        }
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (xvfb != null) {
            xvfb.destroy();
            xvfb.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @BeforeEach
    void connect() {
        assumeTrue(display != null, "no X server here");
        assumeTrue(XlibXdnd.Xlib.get() != null, "no libX11");
        test = new TestX();
        try (var arena = Arena.ofConfined()) {
            sourceDisplay = (MemorySegment) test.call(test.openDisplay, arena.allocateFrom(display));
            targetDisplay = (MemorySegment) test.call(test.openDisplay, arena.allocateFrom(display));
        }
        assumeTrue(sourceDisplay.address() != 0 && targetDisplay.address() != 0, display + " does not open");
        transport = XlibXdnd.open(sourceDisplay, _ -> false).orElseThrow();
    }

    @AfterEach
    void disconnect() {
        if (transport != null) {
            transport.close();
        }
        if (sourceDisplay.address() != 0) {
            test.call(test.closeDisplay, sourceDisplay);
        }
        if (targetDisplay.address() != 0) {
            test.call(test.closeDisplay, targetDisplay);
        }
    }

    @Test
    @DisplayName("a message it sends arrives as a client message the protocol reads, with all five words")
    void sends() {
        var x = transport;
        var target = test.window(targetDisplay);
        test.call(test.sync, targetDisplay, 0);
        x.send(target, x.atom("XdndEnter"), new long[] {x.sourceWindow(), 5L << 24, 11, 12, 13});
        x.flush();

        try (var arena = Arena.ofConfined()) {
            var event = assertInstanceOf(XdndEvent.ClientMessage.class, test.next(targetDisplay, arena));
            assertEquals(target, event.window());
            assertEquals(test.atom(targetDisplay, "XdndEnter"), event.type());
            assertArrayEquals(new long[] {x.sourceWindow(), 5L << 24, 11, 12, 13}, event.data());
        }
    }

    @Test
    @DisplayName("a target's request for a type is answered with that type's bytes on the target's window")
    void answersARequest() {
        var x = transport;
        var heard = new ArrayList<String>();
        var source = new XdndSource(
                x,
                new DragData() {
                    @Override
                    public List<String> types() {
                        return List.of("text/plain;charset=utf-8");
                    }

                    @Override
                    public byte @Nullable [] bytes(String mime) {
                        return mime.equals("text/plain;charset=utf-8")
                                ? "Frame 7".getBytes(StandardCharsets.UTF_8)
                                : null;
                    }
                },
                EnumSet.of(DragAction.COPY),
                new DragSourceListener() {
                    @Override
                    public void over(long window, int px, int py) {}

                    @Override
                    public void outside() {}

                    @Override
                    public void delivered(@Nullable DragAction action) {}

                    @Override
                    public void releasedOver(long window, int px, int py) {}

                    @Override
                    public void cancelled() {
                        heard.add("cancelled");
                    }
                });
        x.ownSelection(x.atom("XdndSelection"));
        x.flush();
        test.call(test.sync, sourceDisplay, 0);

        var target = test.window(targetDisplay);
        var property = test.atom(targetDisplay, "GOLDBERRY_DROP");
        test.call(
                test.convertSelection,
                targetDisplay,
                test.atom(targetDisplay, "XdndSelection"),
                test.atom(targetDisplay, "text/plain;charset=utf-8"),
                property,
                target,
                0L);
        test.call(test.sync, targetDisplay, 0);

        try (var arena = Arena.ofConfined()) {
            // Others may ask too: on XWayland the compositor's window manager
            // watches XdndSelection and asks its owner for TARGETS, to bridge
            // the drag to Wayland. Every request is answered, as a real drag
            // answers them, until the test's own target's comes.
            XdndEvent.SelectionRequest request;
            do {
                var event = test.next(sourceDisplay, arena);
                if (!(event instanceof XdndEvent.SelectionRequest asked)) {
                    continue;
                }
                assertEquals(x.sourceWindow(), asked.owner());
                assertTrue(source.handle(asked));
                request = asked;
                if (asked.requestor() == target) {
                    break;
                }
            } while (true);
            assertEquals(test.atom(targetDisplay, "text/plain;charset=utf-8"), request.target());

            var notify = test.nextRaw(targetDisplay, arena);
            assertEquals(XlibXdnd.SELECTION_NOTIFY, notify.get(JAVA_INT, 0));
            assertEquals(property, notify.get(JAVA_LONG, 56), "the conversion was done, into the property asked for");
        }
        assertArrayEquals("Frame 7".getBytes(StandardCharsets.UTF_8), test.property(targetDisplay, target, property));
        assertEquals(List.of(), heard);
    }

    @Test
    @DisplayName("a message to a window that is gone is an error the drag survives")
    void survivesAGoneWindow() {
        var x = transport;
        x.send(0x7fff_fff0L, x.atom("XdndLeave"), new long[] {x.sourceWindow(), 0, 0, 0, 0});
        // The error comes back on this round trip. Xlib's default handler would
        // exit the process here.
        test.call(test.sync, sourceDisplay, 0);

        assertTrue(x.maxPropertyBytes() > 0);
        assertTrue(x.escapeKeycode() > 0);
        assertEquals("XdndLeave", x.atomName(x.atom("XdndLeave")));
    }
}
