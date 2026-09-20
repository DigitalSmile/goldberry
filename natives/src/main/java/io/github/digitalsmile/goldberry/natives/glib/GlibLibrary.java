package io.github.digitalsmile.goldberry.natives.glib;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;

/// Finds GLib, which this project does not ship and does not link.
///
/// ## The third kind of library in this module
///
/// `libgoldberry` is ours and is always there. `libgoldberry-webview` is ours
/// and is allowed to be missing
/// ([io.github.digitalsmile.goldberry.natives.webview.WebviewLibrary]).
/// GLib is neither: it is the **system's**, it arrives in the process because
/// something else wanted it, and Goldberry has no opinion about which version.
///
/// Two things drag it in on Linux, and both are Goldberry features:
/// `docs/core-widgets.md` §9's `tray-icon`, because SDL's tray is
/// libayatana-appindicator and that links GTK 3; and §9's `web-view`, because
/// WebKitGTK links GTK. Everything either of them says about itself goes through
/// `g_log`, and `g_log`'s default destination is this process's stderr.
///
/// ## Why it is `dlopen`ed rather than exported from `libgoldberry`
///
/// Because it cannot be. `exports/goldberry.symbols` is a version script over
/// the archives the superbuild statically links, and GLib is not one of them —
/// listing `g_log_set_default_handler` there would fail the link. The symbol has
/// to be found in whatever GLib the dynamic loader already has, which is exactly
/// what `SymbolLookup.libraryLookup` does.
///
/// It is therefore also the one place in this module where
/// `ExportListTest` must not look, and it is exempted by path — see the
/// `THIRD_LIBRARY` note there.
///
/// ## Loading it is not free, so it is asked for late
///
/// A lookup by soname `dlopen`s the library if nothing has yet. That is the
/// right trade only because nothing calls this until the toolkit is about to
/// load something that needs GLib anyway: a tray, or a page. An application with
/// neither never reaches here and never maps GLib.
///
/// ## Linux and FreeBSD only
///
/// macOS and Windows have no GLib unless somebody installed one, and neither
/// SDL's tray nor its web view uses it there — SDL uses `NSStatusItem` and
/// `Shell_NotifyIcon`. Asking would be a `dlopen` that fails on every Mac.
public final class GlibLibrary {

    /// The runtime soname. Not `libglib-2.0.so`, which is the symlink a `-dev`
    /// package installs and which most machines running an application do not
    /// have.
    public static final String SONAME = "libglib-2.0.so.0";

    private static final Logger LOG = Logs.of(GlibLibrary.class);

    private static final class Holder {
        private static final Optional<SymbolLookup> LOOKUP = load();
    }

    private GlibLibrary() {}

    /// GLib's symbols, or empty where there is no GLib to find.
    ///
    /// **Never throws.** A missing GLib is the ordinary state of a Mac, a
    /// Windows box and a container with no desktop libraries in it.
    public static Optional<SymbolLookup> get() {
        return Holder.LOOKUP;
    }

    /// Whether this platform is one where GLib is worth looking for.
    ///
    /// Read off `os.name` rather than off
    /// [io.github.digitalsmile.goldberry.natives.NativePlatform],
    /// deliberately: asking that class loads `libgoldberry`, and whether to
    /// bridge a log is a question that must be answerable before any native
    /// library of ours is mapped.
    static boolean isPlatformWithGlib(String osName) {
        var name = osName.toLowerCase(Locale.ROOT);
        return name.contains("linux") || name.contains("freebsd");
    }

    // Restricted for NativeLibrary's reason: opening a native library is what
    // this module exists to do. Suppressed at the call site so an unintended new
    // crossing still shows up as a build failure.
    @SuppressWarnings("restricted")
    private static Optional<SymbolLookup> load() {
        if (!isPlatformWithGlib(System.getProperty("os.name", ""))) {
            return Optional.empty();
        }
        try {
            // Arena.global(), like every other library this module opens: the
            // handler stub installed against it outlives any scope, and
            // unloading GLib under a running GTK is not a thing that can be
            // recovered from.
            var lookup = SymbolLookup.libraryLookup(SONAME, Arena.global());
            LOG.debug("found {} for the log bridge", SONAME);
            return Optional.of(lookup);
        } catch (IllegalArgumentException e) {
            // No GLib on this machine at all. `debug`, not `warn`: an
            // application with no tray and no page is not having a problem, and
            // one that has them would have failed louder and earlier.
            LOG.debug("no {} ({}); native log messages from GLib will go to stderr", SONAME, e.getMessage());
            return Optional.empty();
        }
    }
}
