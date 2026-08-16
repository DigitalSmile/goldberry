package io.github.digitalsmile.goldberry.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// The watcher itself.
///
/// Deliberately few tests, and generous ones. File watching is the least
/// deterministic thing in the toolkit — the JDK polls on macOS, editors write in
/// several steps, and filesystems coalesce events — so what is asserted here is
/// only what a watcher can actually promise: that a change is eventually noticed,
/// that it arrives on the executor it was given, and that stopping works. The
/// decisions worth pinning are in [ReloadableSourceTest], which needs no threads.
class HotReloadTest {

    /// Long enough for the JDK's polling `WatchService` on macOS, which has no
    /// kernel backend and can take seconds.
    private static final int TIMEOUT_SECONDS = 30;

    @TempDir
    Path directory;

    @Test
    @Timeout(TIMEOUT_SECONDS + 15)
    @DisplayName("an edit is noticed and applied on the given executor")
    void noticesAnEdit() throws IOException, InterruptedException {
        var file = directory.resolve("app.css");
        Files.writeString(file, "button { color: red }");
        var source = ReloadableSource.load(
                file, css -> Stylesheet.parse(CascadeLayer.APPLICATION, css));

        var applied = new CountDownLatch(1);
        var appliedOn = new AtomicReference<String>();
        // Stands in for Goldberry.ui(): the point is that the callback does not
        // run on the watcher thread, because everything it would touch is
        // confined to the UI thread (ADR-0020).
        Runnable[] pending = new Runnable[1];
        java.util.concurrent.Executor executor = task -> {
            pending[0] = task;
            appliedOn.set(Thread.currentThread().getName());
            task.run();
            applied.countDown();
        };

        var watcher = HotReload.watch(List.of(source), executor, s -> { });
        try {
            // Written after the watcher is up, or the change can land before the
            // directory is registered.
            Thread.sleep(200);
            Files.writeString(file, "button { color: blue }\ninput { color: red }");

            assertTrue(applied.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "the edit was never noticed");
        } finally {
            watcher.close();
        }

        assertEquals(2, source.current().rules().size());
        assertTrue(pending[0] != null, "the callback was never handed to the executor");
    }

    @Test
    @Timeout(30)
    @DisplayName("a broken edit does not reach the callback at all")
    void brokenEditIsNotApplied() throws IOException, InterruptedException {
        var file = directory.resolve("app.css");
        Files.writeString(file, "button { color: red }");
        var source = ReloadableSource.load(
                file, css -> Stylesheet.parse(CascadeLayer.APPLICATION, css));
        var good = source.current();

        var applications = new java.util.concurrent.atomic.AtomicInteger();

        var watcher = HotReload.watch(
                List.of(source), Runnable::run, s -> applications.incrementAndGet());
        try {
            Thread.sleep(200);
            Files.writeString(file, "button { color:");
            // Nothing to wait for: the assertion is that nothing happens.
            Thread.sleep(1500);
        } finally {
            watcher.close();
        }

        assertEquals(0, applications.get(), "a broken file must not reach the callback");
        assertEquals(good, source.current());
    }

    @Test
    @DisplayName("watching nothing is refused rather than silently doing nothing")
    void refusesAnEmptyWatch() {
        assertThrows(IllegalArgumentException.class,
                () -> HotReload.watch(List.of(), Runnable::run, s -> { }));
    }

    @Test
    @Timeout(30)
    @DisplayName("close stops the watcher, and is idempotent")
    void closeStops() throws IOException, InterruptedException {
        var file = directory.resolve("app.css");
        Files.writeString(file, "button { color: red }");
        var source = ReloadableSource.load(
                file, css -> Stylesheet.parse(CascadeLayer.APPLICATION, css));
        var applications = new java.util.concurrent.atomic.AtomicInteger();

        var reload = HotReload.watch(List.of(source), Runnable::run, s -> applications.incrementAndGet());
        reload.close();
        reload.close();

        Files.writeString(file, "button { color: blue }");
        Thread.sleep(1000);

        assertEquals(0, applications.get(), "a closed watcher must stop noticing edits");
    }
}
