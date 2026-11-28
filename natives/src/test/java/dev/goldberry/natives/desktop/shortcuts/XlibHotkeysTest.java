package dev.goldberry.natives.desktop.shortcuts;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.natives.NativePlatform;
import dev.goldberry.natives.desktop.shortcuts.HotkeyOutcome.Bound;
import dev.goldberry.natives.desktop.shortcuts.HotkeyOutcome.Refusal;
import dev.goldberry.natives.desktop.shortcuts.HotkeyOutcome.Refused;

/// Keys grabbed on an X server's root window, and a second grab of the same
/// combination refused.
///
/// The server is an `Xvfb` of the test's own where this machine has one, and
/// keys are then pressed into it through XTest. Without one, the grab tests
/// run on `$DISPLAY` with a combination nobody types (all four modifiers and
/// `F12`), held for a moment, and nothing is pressed into a desktop the person
/// running the suite is using. `-Dgoldberry.test.x11.inject=true` presses keys
/// into `$DISPLAY` too, for a display that is a test's own.
@DisplayName("global shortcuts through X11 key grabs")
@Timeout(60)
class XlibHotkeysTest {

    private static final int ALL = Hotkey.SHIFT | Hotkey.CTRL | Hotkey.ALT | Hotkey.META;
    private static final Hotkey ODD = new Hotkey("odd", "nobody types this", HotkeyKey.F12, ALL);
    private static final Hotkey TALK = new Hotkey("talk", "Push to talk", HotkeyKey.F9, Hotkey.CTRL);

    private static @Nullable Process xvfb;
    private static @Nullable String display;
    private static boolean injectable;

    private XlibHotkeys first;
    private XlibHotkeys second;
    private final BlockingQueue<String> heard = new LinkedBlockingQueue<>();
    private final HotkeyListener listener = new HotkeyListener() {
        @Override
        public void pressed(String id) {
            heard.add("pressed " + id);
        }

        @Override
        public void released(String id) {
            heard.add("released " + id);
        }
    };

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
            String number;
            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                number = reader.readLine();
            } catch (IOException e) {
                process.destroyForcibly();
                throw e;
            }
            if (number != null && !number.isBlank()) {
                xvfb = process;
                display = ":" + number.trim();
                injectable = true;
                return;
            }
            process.destroyForcibly();
        }
        var desktop = System.getenv("DISPLAY");
        if (desktop != null && !desktop.isBlank()) {
            display = desktop;
            injectable = Boolean.getBoolean("goldberry.test.x11.inject");
        }
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (xvfb != null) {
            xvfb.destroy();
            xvfb.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private XlibHotkeys open() {
        assumeTrue(display != null, "no X server here");
        var backend = XlibHotkeys.open(display);
        assumeTrue(backend.isPresent(), "no libX11, or " + display + " does not open");
        return backend.get();
    }

    @AfterEach
    void close() {
        if (first != null) {
            first.close();
        }
        if (second != null) {
            second.close();
        }
    }

    @Test
    @DisplayName("grabs a combination, and refuses it to a second client as taken")
    void taken() throws Exception {
        first = open();
        second = open();

        assertEquals(
                List.of(new Bound("odd", ODD.xdgTrigger())),
                first.bind(List.of(ODD), listener).get(10, TimeUnit.SECONDS));
        var refused = second.bind(List.of(ODD), listener).get(10, TimeUnit.SECONDS);

        assertEquals(
                Refusal.TAKEN,
                assertInstanceOf(Refused.class, refused.getFirst()).reason());
    }

    @Test
    @DisplayName("lets go of a combination when it is unbound, so another client can have it")
    void ungrabs() throws Exception {
        first = open();
        second = open();
        first.bind(List.of(ODD), listener).get(10, TimeUnit.SECONDS);

        first.bind(List.of(), listener).get(10, TimeUnit.SECONDS);

        assertInstanceOf(
                Bound.class,
                second.bind(List.of(ODD), listener).get(10, TimeUnit.SECONDS).getFirst());
    }

    @Test
    @DisplayName("hears a press and its release, the release after a held key's repeats")
    void pressAndRelease() throws Exception {
        first = open();
        assumeTrue(injectable, "keys are pressed only into an X server of the test's own");
        first.bind(List.of(TALK), listener).get(10, TimeUnit.SECONDS);

        try (var keyboard = XTest.open(display)) {
            assumeTrue(keyboard != null, "no libXtst");
            keyboard.key(0xffe3, true); // Control_L
            keyboard.key(HotkeyKey.F9.keysym(), true);
            keyboard.key(HotkeyKey.F9.keysym(), true);
            keyboard.key(0xffe3, false);
            keyboard.key(HotkeyKey.F9.keysym(), false);
        }

        assertEquals("pressed talk", heard.poll(10, TimeUnit.SECONDS));
        assertEquals("released talk", heard.poll(10, TimeUnit.SECONDS));
    }

    /// Keys pressed into a display through XTest, on a connection of its own.
    private record XTest(
            Arena arena,
            MemorySegment display,
            MethodHandle keysymToKeycode,
            MethodHandle fake,
            MethodHandle sync,
            MethodHandle closeDisplay)
            implements AutoCloseable {

        @SuppressWarnings("restricted")
        static @Nullable XTest open(String name) {
            try {
                var arena = Arena.ofConfined();
                var linker = Linker.nativeLinker();
                var x11 = SymbolLookup.libraryLookup("libX11.so.6", arena);
                var xtst = SymbolLookup.libraryLookup("libXtst.so.6", arena);
                var openDisplay = linker.downcallHandle(
                        x11.find("XOpenDisplay").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS));
                var display = (MemorySegment) openDisplay.invokeExact(arena.allocateFrom(name));
                if (display.address() == 0) {
                    arena.close();
                    return null;
                }
                return new XTest(
                        arena,
                        display,
                        linker.downcallHandle(
                                x11.find("XKeysymToKeycode").orElseThrow(),
                                FunctionDescriptor.of(JAVA_BYTE, ADDRESS, JAVA_LONG)),
                        linker.downcallHandle(
                                xtst.find("XTestFakeKeyEvent").orElseThrow(),
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_LONG)),
                        linker.downcallHandle(
                                x11.find("XSync").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)),
                        linker.downcallHandle(
                                x11.find("XCloseDisplay").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS)));
            } catch (Throwable e) {
                return null;
            }
        }

        void key(int keysym, boolean down) {
            try {
                var keycode = Byte.toUnsignedInt((byte) keysymToKeycode.invokeExact(display, (long) keysym));
                var _ = (int) fake.invokeExact(display, keycode, down ? 1 : 0, 0L);
                var _ = (int) sync.invokeExact(display, 0);
            } catch (Throwable e) {
                throw new IllegalStateException("XTest failed", e);
            }
        }

        @Override
        public void close() {
            try {
                var _ = (int) closeDisplay.invokeExact(display);
            } catch (Throwable e) {
                // The test is over.
            }
            arena.close();
        }
    }
}
