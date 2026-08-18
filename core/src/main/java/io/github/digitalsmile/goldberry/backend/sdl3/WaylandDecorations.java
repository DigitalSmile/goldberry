package io.github.digitalsmile.goldberry.backend.sdl3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Works out, before anyone looks at the window, whether a Wayland window is
/// going to come up with no titlebar and no way to resize it.
///
/// ## Why a window can have no decorations
///
/// Wayland has no decoration protocol of its own. A toplevel is decorated either
/// by the compositor — negotiated through `zxdg_decoration_manager_v1` — or by the
/// client. **GNOME's compositor declines to do it**, so on GNOME the client-side
/// path is the only one, and SDL's implementation of it is libdecor.
///
/// libdecor draws nothing itself; it loads a plugin. Debian and Ubuntu ship two:
/// `libdecor-gtk.so`, installed by default as a dependency of the library, and
/// `libdecor-cairo.so`, which is a separate package almost nobody installs.
///
/// ## Why the GTK plugin never works here
///
/// `libdecor-gtk.so`'s constructor opens with this, before it touches GTK or
/// D-Bus or Wayland:
///
/// ```text
/// call   getpid
/// call   gettid
/// cmp    %eax,%ebx
/// jne    <return NULL>
/// ```
///
/// `getpid() == gettid()` is the Linux test for "am I the process's initial
/// thread", which is what GTK requires. **The stock `java` launcher does not
/// satisfy it**: it runs `main` on a thread it creates, so that the primordial
/// thread's stack size does not limit Java. The plugin then refuses — silently,
/// because the jump lands past its own `fprintf`.
///
/// It is not a property of the JVM, though, only of how the JVM was started. A
/// launcher that embeds the VM — its own `main` calling `JNI_CreateJavaVM` and
/// then the Java `main` — runs Java on the primordial thread, and there the GTK
/// plugin loads and draws decorations that match the desktop. Measured, on one
/// machine, one libdecor, one plugin directory:
///
/// | launcher | `main` runs on | libdecor |
/// |---|---|---|
/// | stock `java` | a created thread | `failed to init`, then no decorations |
/// | embedded `JNI_CreateJavaVM` | the primordial thread | loads, GTK decorations |
///
/// So the thread is measured here rather than assumed, through
/// [#onInitialThread()].
///
/// The result is a window that opens, paints, and receives input perfectly, with
/// no titlebar, no close button and no resize edge — because on Wayland a resize
/// is a client-initiated `xdg_toplevel.resize` and the thing that decides the
/// pointer is on a resize edge *is* the decoration.
///
/// ## Why this has to be inferred rather than asked
///
/// SDL cannot answer the question. `libdecor_new` returns a valid context even
/// when every plugin failed — it falls back to drawing nothing — so SDL sees
/// success, marks the surface `WAYLAND_SHELL_SURFACE_TYPE_LIBDECOR` and exposes no
/// property saying the frame is empty. What is knowable from here is the input to
/// that decision: which plugins are installed. Since the GTK one is guaranteed to
/// fail in this process, "the GTK plugin is the only one" is equivalent to "there
/// will be no decorations".
///
/// SDL 3.4 knows about the restriction and works around it in the one case it can
/// detect — see `Wayland_LoadLibdecor`, which deliberately initializes libdecor on
/// a secondary thread "so that it will not use its GTK plugin, but instead will
/// fall back to the Cairo or dummy plugin". It takes that branch only when
/// `SDL_CanUseGtk()` is false, and that function checks the `SDL_ENABLE_GTK` hint
/// and setuid/setgid — never the thread. So SDL believes GTK is usable, takes the
/// direct path, and the plugin fails anyway.
///
/// See ADR-0084.
final class WaylandDecorations {

    private WaylandDecorations() {
    }

    /// SDL's name for the driver this applies to. X11 is decorated by the window
    /// manager and never reaches libdecor.
    private static final String WAYLAND = "wayland";

    /// The plugin that cannot work in a JVM.
    static final String GTK_PLUGIN = "libdecor-gtk.so";

    /// libdecor's plugin ABI directory. The `-1` is the ABI version, not the
    /// library version: libdecor 0.1 and 0.2 both use `plugins-1`.
    private static final String PLUGIN_SUBDIRECTORY = "libdecor/plugins-1";

    /// Overrides where libdecor looks. Honoured here for the same reason libdecor
    /// honours it: a message naming a directory the loader will not read is worse
    /// than no message.
    private static final String PLUGIN_DIR_ENV = "LIBDECOR_PLUGIN_DIR";

    /// What SDL was told to prefer, for the "run on X11 instead" suggestion.
    private static final String DRIVER_PROPERTY = Sdl3Backend.VIDEO_DRIVER_PROPERTY;

    /// The verdict. Deliberately three-valued: "we could not find out" is not the
    /// same as "everything is fine", and only one of them should stay quiet.
    enum Verdict {
        /// A plugin that works in this process is installed.
        DECORATED,
        /// libdecor has a plugin directory, and nothing in it can decorate here.
        UNDECORATED,
        /// Not Wayland, or no plugin directory could be located. Say nothing.
        UNKNOWN
    }

    /// Decides from a plugin listing and the calling thread.
    ///
    /// @param videoDriver     what `SDL_GetCurrentVideoDriver` reported
    /// @param pluginFiles     the file names in libdecor's plugin directory, or
    ///                        empty if no such directory could be found
    /// @param onInitialThread whether this thread is the process's initial one,
    ///                        or empty if that could not be determined
    /// @return the verdict
    static Verdict verdict(String videoDriver,
                           Optional<List<String>> pluginFiles,
                           Optional<Boolean> onInitialThread) {
        if (videoDriver == null || !videoDriver.toLowerCase(Locale.ROOT).equals(WAYLAND)) {
            return Verdict.UNKNOWN;
        }
        return verdictForWayland(pluginFiles, onInitialThread);
    }

    /// The same question with the driver already settled — *if* this process ends
    /// up on Wayland, will its windows be decorated?
    ///
    /// Split out when the answer was needed **before** `SDL_Init`, to choose the
    /// video driver on the strength of it (ADR-0085). ADR-0086 replaced that with
    /// an unconditional X11 preference, so nothing asks it that early any more —
    /// it stays as the driver-independent core of [#verdict], and as the shape to
    /// return to if the conditional fallback comes back.
    ///
    /// @param pluginFiles     the file names in libdecor's plugin directory, or
    ///                        empty if no such directory could be found
    /// @param onInitialThread whether this thread is the process's initial one,
    ///                        or empty if that could not be determined
    /// @return the verdict, as it would apply to a Wayland window
    static Verdict verdictForWayland(Optional<List<String>> pluginFiles,
                                     Optional<Boolean> onInitialThread) {
        if (pluginFiles.isEmpty()) {
            // libdecor's directory could be anywhere a distribution puts it, and
            // guessing wrong must not turn into a warning about a problem that is
            // not there. Crying wolf here costs more than staying quiet: this
            // message only helps if it is always true when it appears.
            return Verdict.UNKNOWN;
        }
        var usable = pluginFiles.get().stream()
                .filter(name -> name.endsWith(".so"))
                .filter(name -> !name.equals(GTK_PLUGIN))
                .toList();
        if (!usable.isEmpty()) {
            return Verdict.DECORATED;
        }
        if (!pluginFiles.get().contains(GTK_PLUGIN)) {
            // Nothing at all to load. Which thread this is does not enter into it.
            return Verdict.UNDECORATED;
        }
        // The GTK plugin is the only candidate, so everything now turns on the
        // one thing it checks. Measured rather than assumed: an embedded launcher
        // puts Java on the primordial thread and the plugin loads there.
        return onInitialThread
                .map(initial -> initial ? Verdict.DECORATED : Verdict.UNDECORATED)
                .orElse(Verdict.UNKNOWN);
    }

    /// The warning, or empty when there is nothing to say.
    ///
    /// @param videoDriver     what `SDL_GetCurrentVideoDriver` reported
    /// @param pluginFiles     the file names in libdecor's plugin directory, or
    ///                        empty if no such directory could be found
    /// @param onInitialThread whether this thread is the process's initial one
    /// @return the text to log, already wrapped, or empty
    static Optional<String> diagnose(String videoDriver,
                                     Optional<List<String>> pluginFiles,
                                     Optional<Boolean> onInitialThread) {
        if (verdict(videoDriver, pluginFiles, onInitialThread) != Verdict.UNDECORATED) {
            return Optional.empty();
        }
        return Optional.of(message(pluginFiles.get().contains(GTK_PLUGIN)));
    }

    /// The same question, against the real filesystem and the calling thread.
    ///
    /// Must be called on the thread that will drive SDL, since that is the thread
    /// libdecor is initialized from and therefore the one the plugin inspects.
    ///
    /// @param videoDriver what `SDL_GetCurrentVideoDriver` reported
    /// @return the text to log, or empty
    static Optional<String> diagnose(String videoDriver) {
        return diagnose(videoDriver,
                pluginFiles(System.getenv(PLUGIN_DIR_ENV),
                        System.getProperty("os.arch", ""),
                        WaylandDecorations::listDirectory),
                onInitialThread());
    }

    /// Whether the calling thread is the process's initial thread — the
    /// `getpid() == gettid()` the GTK plugin tests for.
    ///
    /// Read from `/proc/thread-self`, which is a symlink to `<pid>/task/<tid>`, so
    /// both numbers come out of one readlink and no native call is needed.
    ///
    /// @return true or false, or empty where `/proc` cannot answer — a machine
    ///         that cannot say must produce silence rather than a guess
    static Optional<Boolean> onInitialThread() {
        return onInitialThread(Path.of("/proc/thread-self"));
    }

    /// @param threadSelf the `/proc/thread-self` symlink to read
    /// @return whether `<pid>` and `<tid>` in its target are the same, or empty
    static Optional<Boolean> onInitialThread(Path threadSelf) {
        try {
            // e.g. "55887/task/55887" on the initial thread, "55889/task/55890"
            // on any other.
            var parts = Files.readSymbolicLink(threadSelf).toString().split("/");
            if (parts.length < 3 || !parts[1].equals("task")) {
                return Optional.empty();
            }
            return Optional.of(parts[0].equals(parts[2]));
        } catch (IOException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    /// Where libdecor will look for plugins, and what is in it.
    ///
    /// @param pluginDirEnv the value of `LIBDECOR_PLUGIN_DIR`, or null
    /// @param osArch       the `os.arch` system property, for the multiarch triplet
    /// @param lister       lists a directory, or returns empty if it is not one
    /// @return the file names in the first directory that exists, or empty
    static Optional<List<String>> pluginFiles(String pluginDirEnv,
                                              String osArch,
                                              java.util.function.Function<Path, Optional<List<String>>> lister) {
        for (var directory : candidateDirectories(pluginDirEnv, osArch)) {
            var listing = lister.apply(directory);
            if (listing.isPresent()) {
                return listing;
            }
        }
        return Optional.empty();
    }

    /// The directories libdecor might load plugins from, most authoritative first.
    ///
    /// @param pluginDirEnv the value of `LIBDECOR_PLUGIN_DIR`, or null
    /// @param osArch       the `os.arch` system property
    /// @return absolute candidate directories, not filtered for existence
    static List<Path> candidateDirectories(String pluginDirEnv, String osArch) {
        // The environment override wins outright and is not joined with the
        // conventional paths: libdecor does not fall back either, so a message
        // derived from a directory it will not read would be fiction.
        if (pluginDirEnv != null && !pluginDirEnv.isBlank()) {
            return List.of(Path.of(pluginDirEnv));
        }
        var triplet = multiarchTriplet(osArch);
        var candidates = new java.util.ArrayList<Path>();
        triplet.ifPresent(t -> candidates.add(Path.of("/usr/lib", t, PLUGIN_SUBDIRECTORY)));
        candidates.add(Path.of("/usr/lib64", PLUGIN_SUBDIRECTORY));
        candidates.add(Path.of("/usr/lib", PLUGIN_SUBDIRECTORY));
        triplet.ifPresent(t -> candidates.add(Path.of("/usr/local/lib", t, PLUGIN_SUBDIRECTORY)));
        candidates.add(Path.of("/usr/local/lib", PLUGIN_SUBDIRECTORY));
        return List.copyOf(candidates);
    }

    /// Debian's multiarch directory name for an architecture.
    ///
    /// @param osArch the `os.arch` system property
    /// @return the triplet, or empty for an architecture with no known spelling
    static Optional<String> multiarchTriplet(String osArch) {
        var arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64", "x64" -> Optional.of("x86_64-linux-gnu");
            case "aarch64", "arm64" -> Optional.of("aarch64-linux-gnu");
            default -> Optional.empty();
        };
    }

    private static Optional<List<String>> listDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (var entries = Files.list(directory)) {
            return Optional.of(entries.map(path -> path.getFileName().toString()).sorted().toList());
        } catch (IOException e) {
            // A directory that exists and cannot be read is not knowledge either.
            throw new UncheckedIOException(e);
        }
    }

    private static String message(boolean gtkPluginPresent) {
        var cause = gtkPluginPresent
                ? """
                  The only libdecor plugin installed is the GTK one, and it refuses to \
                  start unless it is called on the process's initial thread. This thread \
                  is not it: the stock java launcher runs main() on a thread it creates, \
                  so gettid() != getpid() and the plugin returns without a word.

                  libdecor added that check in 0.2.3. Older libdecor let the GTK plugin \
                  run here and drew decorations that matched the desktop, so this may \
                  have worked before an upgrade — but it was corrupting GTK's state and \
                  crashing SDL applications, which is why the check exists. Downgrading \
                  libdecor brings the crash back, not the titlebar."""
                : "No libdecor plugin is installed, so libdecor has nothing to draw with.";

        return """
                This window may have no titlebar, no close button, and no way to resize it.

                %s

                Wayland has no decoration protocol of its own. A compositor that declines \
                to draw decorations — GNOME does — leaves it to the client, and SDL draws \
                them through libdecor.

                Three ways out, in increasing order of effort and of how native the \
                result looks.

                1. Install the Cairo plugin, which has no thread restriction. Generic
                   decorations that match no desktop, but they work and they resize:
                     sudo apt install libdecor-0-plugin-1-cairo    (Debian/Ubuntu)
                     or your distribution's libdecor Cairo plugin
                   Loaded at run time, so nothing needs rebuilding.

                2. Run on X11, where the window manager draws its own decorations.
                   Goldberry already prefers X11 on Linux, so reaching this message
                   means either XWayland is unavailable here or the driver was pinned
                   to Wayland. To pin it the other way:
                     -D%s=x11

                3. Start the JVM on the process's initial thread, which is the only way
                   to get the GTK plugin — and so decorations that match the desktop —
                   on Wayland. A launcher that embeds the VM (its own main() calling
                   JNI_CreateJavaVM, then the Java main()) runs Java there; the stock
                   `java` launcher does not, and neither does jpackage's.""".formatted(cause, DRIVER_PROPERTY);
    }
}
