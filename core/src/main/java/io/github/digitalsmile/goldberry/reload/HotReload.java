package io.github.digitalsmile.goldberry.reload;

import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;

/// Watches files and re-applies them while the application is running.
///
/// What §1 and §8 call hot reload: "markup + stylesheets are hot-reloadable at
/// runtime". Point it at a stylesheet, edit the file, and the window restyles
/// without restarting.
///
/// ## Threads
///
/// Watching blocks, so it happens on a daemon thread of its own. Applying does
/// **not** happen there — the callback is handed to the UI executor, because
/// everything it will touch (a window, a style, a box tree) is confined to the
/// UI thread (ADR-0020). That is the whole reason this class takes an
/// [Executor]: a reload that restyled from a background thread would be a data
/// race that only shows up under an editor's autosave.
///
/// ## Debouncing
///
/// Saving a file is rarely one event. Editors truncate then write, or write a
/// temporary file and rename it, and a watcher reports each step — so a naive
/// reload parses an empty file, fails, and logs a scary message about markup the
/// author never wrote. Events are therefore coalesced over a short quiet period
/// before anything is read.
///
/// ## A caveat worth knowing
///
/// On macOS the JDK's `WatchService` has no kernel backend and polls, so a change
/// can take a couple of seconds to be noticed. Nothing here can fix that; it is
/// mentioned because "hot reload is broken on my Mac" is otherwise a puzzle.
public final class HotReload implements AutoCloseable {

    private static final Logger LOG = Logs.of(HotReload.class);

    /// How long to wait for the file to stop changing before reading it.
    private static final Duration QUIET_PERIOD = Duration.ofMillis(120);

    private final WatchService service;
    private volatile Thread watcher;
    private volatile boolean running = true;

    private HotReload(WatchService service) {
        this.service = service;
    }

    /// Watches every source and applies it on `executor` when it changes.
    ///
    /// @param sources  the files to watch; each keeps its own last good value
    /// @param executor where the callback runs — [io.github.digitalsmile.goldberry.Goldberry#ui()]
    ///                 in an application
    /// @param onReload given the source that changed, after its value has been
    ///                 updated
    public static HotReload watch(
            List<ReloadableSource<?>> sources, Executor executor, Consumer<ReloadableSource<?>> onReload) {

        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(onReload, "onReload");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("nothing to watch");
        }

        WatchService service;
        try {
            service = FileSystems.getDefault().newWatchService();
            // Directories, not files: a WatchService cannot watch a file, and an
            // editor that renames a temporary file into place would break the
            // registration if it could.
            for (var directory : sources.stream().map(s -> s.file().toAbsolutePath().getParent()).distinct().toList()) {
                directory.register(service,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not watch for changes", e);
        }

        // One instance, and the thread reads its `running` flag -- two of these
        // sharing a service would mean close() stopping a flag nobody loops on.
        var reload = new HotReload(service);
        var thread = new Thread(() -> reload.loop(sources, executor, onReload), "goldberry-hot-reload");
        // Daemon: hot reload must never be the reason a process refuses to exit.
        thread.setDaemon(true);
        reload.watcher = thread;
        thread.start();
        return reload;
    }

    private void loop(
            List<ReloadableSource<?>> sources, Executor executor, Consumer<ReloadableSource<?>> onReload) {

        while (running) {
            try {
                var key = service.poll(200, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }
                key.pollEvents();
                key.reset();

                // Drain whatever else arrives during the quiet period rather than
                // reloading once per event: one save is several events, and the
                // first of them often sees a truncated file.
                var deadline = System.nanoTime() + QUIET_PERIOD.toNanos();
                while (running && System.nanoTime() < deadline) {
                    var more = service.poll(
                            Math.max(1, (deadline - System.nanoTime()) / 1_000_000), TimeUnit.MILLISECONDS);
                    if (more == null) {
                        break;
                    }
                    more.pollEvents();
                    more.reset();
                    deadline = System.nanoTime() + QUIET_PERIOD.toNanos();
                }

                // Every source is re-read rather than only the one the event
                // named: an editor's rename-into-place reports a change to a
                // temporary name, and reading a file that did not change costs a
                // string compare (see ReloadableSource#reload).
                for (var source : sources) {
                    if (source.reload().isPresent()) {
                        LOG.info("reloaded {}", source.file().getFileName());
                        executor.execute(() -> apply(onReload, source));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                return;
            } catch (RuntimeException e) {
                // The watcher thread outliving one bad reload matters more than
                // the reload: dying here means no further edits are noticed and
                // nothing says why.
                LOG.warn("hot reload failed", e);
            }
        }
    }

    private static void apply(Consumer<ReloadableSource<?>> onReload, ReloadableSource<?> source) {
        try {
            onReload.accept(source);
        } catch (RuntimeException e) {
            // This runs on the UI thread. Letting it out would end the event
            // loop -- the window would close because somebody saved a file.
            LOG.warn("applying the reload of {} failed", source.file().getFileName(), e);
        }
    }

    /// Stops watching. Idempotent.
    @Override
    public void close() {
        running = false;
        try {
            service.close();
        } catch (IOException e) {
            LOG.debug("closing the watch service failed", e);
        }
        if (watcher != null) {
            watcher.interrupt();
        }
    }
}
