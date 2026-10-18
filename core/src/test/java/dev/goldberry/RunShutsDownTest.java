package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.render.BackendException;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.WindowSpec;

/// `Goldberry.run()` hands every window back when its loop ends, and refuses
/// without touching the runtime when it cannot run.
@DisplayName("Goldberry.run() shuts the runtime down when it returns")
class RunShutsDownTest {

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("a loop stopped with windows open closes them and releases the backend")
    void stopClosesWhatIsOpen() {
        Window.open(WindowSpec.of("first", LogicalSize.of(80f, 60f)));
        Window.open(WindowSpec.of("second", LogicalSize.of(80f, 60f)));
        var opened = headlessWindows();
        assertEquals(2, opened.size());

        Goldberry.ui().execute(Goldberry::stop);
        Goldberry.run();

        assertFalse(GoldberryRuntime.isStarted(), "the runtime is gone");
        for (var window : opened) {
            assertFalse(window.isOpen(), "\"" + window + "\" was handed back");
        }
        assertTrue(backend.windows().isEmpty());
    }

    @Test
    @Timeout(10)
    @DisplayName("a handler that throws still leaves nothing open")
    void aFailureStillShutsDown() {
        Window.open(WindowSpec.of("failing", LogicalSize.of(80f, 60f)));
        var window = headlessWindows().getFirst();

        Goldberry.ui().execute(() -> {
            throw new IllegalStateException("the handler's own bug");
        });
        var thrown = assertThrows(BackendException.class, Goldberry::run);

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertFalse(GoldberryRuntime.isStarted());
        assertFalse(window.isOpen());
    }

    @Test
    @Timeout(10)
    @DisplayName("a second run() from inside the loop is refused, and the loop goes on")
    void reentryIsRefused() {
        Window.open(WindowSpec.of("reentrant", LogicalSize.of(80f, 60f)));
        var refused = new AtomicReference<Throwable>();
        var after = new ArrayList<String>();

        Goldberry.ui().execute(() -> {
            refused.set(assertThrows(BackendException.class, Goldberry::run));
            // Still the same runtime: the refusal shut nothing down.
            after.add(GoldberryRuntime.isStarted() ? "started" : "gone");
            Goldberry.ui().execute(() -> {
                after.add("the loop went on");
                Goldberry.stop();
            });
        });
        Goldberry.run();

        assertTrue(
                refused.get().getMessage().contains("already running"),
                refused.get().getMessage());
        assertEquals(List.of("started", "the loop went on"), after);
        assertFalse(GoldberryRuntime.isStarted());
    }

    @Test
    @Timeout(10)
    @DisplayName("run() off the UI thread is refused and leaves the runtime as it was")
    void offTheUiThreadIsRefused() throws InterruptedException {
        Window.open(WindowSpec.of("elsewhere", LogicalSize.of(80f, 60f)));
        var window = headlessWindows().getFirst();
        var refused = new AtomicReference<Throwable>();

        var other = Thread.ofVirtual().start(() -> {
            try {
                Goldberry.run();
            } catch (BackendException e) {
                refused.set(e);
            }
        });
        other.join();

        assertInstanceOf(BackendException.class, refused.get());
        assertTrue(GoldberryRuntime.isStarted(), "the runtime outlived the refusal");
        assertTrue(window.isOpen(), "and so did its window");
    }

    @Test
    @Timeout(10)
    @DisplayName("a window opened after run() returns starts a fresh runtime")
    void aLaterWindowStartsAgain() {
        Window.open(WindowSpec.of("once", LogicalSize.of(80f, 60f)));
        Goldberry.ui().execute(Goldberry::stop);
        Goldberry.run();
        assertFalse(GoldberryRuntime.isStarted());

        var again = new HeadlessBackend();
        GoldberryRuntime.install(again);
        Window.open(WindowSpec.of("twice", LogicalSize.of(80f, 60f)));

        assertSame(again, GoldberryRuntime.get().backend());
        assertEquals(1, again.windows().size());
    }

    @Test
    @DisplayName("shutting down twice, or with nothing started, is harmless")
    void shutdownIsIdempotent() {
        Goldberry.shutdown();
        assertFalse(GoldberryRuntime.isStarted());
        Goldberry.shutdown();
        assertFalse(GoldberryRuntime.isStarted());
    }

    @Test
    @DisplayName("a stale runtime's shutdown leaves the current one alone")
    void aStaleShutdownIsIgnored() {
        var stale = GoldberryRuntime.get();
        GoldberryRuntime.shutdown();
        var current = new HeadlessBackend();
        GoldberryRuntime.install(current);

        GoldberryRuntime.shutdown(stale);

        assertTrue(GoldberryRuntime.isStarted());
        assertSame(current, GoldberryRuntime.get().backend());
    }

    private List<HeadlessWindow> headlessWindows() {
        return backend.windows().stream().map(HeadlessWindow.class::cast).toList();
    }
}
