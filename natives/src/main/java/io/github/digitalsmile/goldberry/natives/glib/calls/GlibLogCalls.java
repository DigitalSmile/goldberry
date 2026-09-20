package io.github.digitalsmile.goldberry.natives.glib.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// GLib's two logging hooks.
///
/// The only holders in this module bound against a library that is **not**
/// `libgoldberry` and not `libgoldberry-webview` either: GLib arrives in the
/// process under its own soname, dragged in by whatever wanted it — the system
/// tray through libayatana-appindicator, a web view through WebKitGTK. See
/// `io.github.digitalsmile.goldberry.natives.glib.GlibLibrary` for why it is
/// found by `dlopen` rather than exported from ours.
///
/// One holder per function, in a `…calls` package of its own, for the reason
/// [Downcalls] gives: a downcall handle is 450× slower in a native image unless
/// its class was initialised while the image was being built, and what the
/// `--initialize-at-build-time` flag names is this package.
public record GlibLogCalls(SetDefaultHandler setDefaultHandler, SetWriterFunc setWriterFunc) {

    /// Binds both functions, either of which may be absent from a very old GLib.
    ///
    /// @param lookup the `dlopen`ed GLib
    public static GlibLogCalls bind(SymbolLookup lookup) {
        return new GlibLogCalls(new SetDefaultHandler(lookup), new SetWriterFunc(lookup));
    }

    /// Installs the handler every legacy `g_log` call ends at, and hands back
    /// the one that was there.
    ///
    /// `GLogFunc g_log_set_default_handler(GLogFunc, void*)`
    ///
    /// This is the one that catches the message this bridge was built for.
    /// `g_warning`, `g_message` and `g_critical` are macros over `g_log`, and
    /// `g_logv` consults the domain's handler — this one, when the domain has no
    /// handler of its own — **before** it forwards anything to the structured
    /// path. So a default handler intercepts them and the writer function below
    /// never sees them.
    ///
    /// Safe to call more than once and at any time, unlike its neighbour.
    public static final class SetDefaultHandler {

        private static final MethodHandle FD_g_log_set_default_handler =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetDefaultHandler(SymbolLookup lookup) {
            this.address = Downcalls.optionalSymbol(lookup, "g_log_set_default_handler");
        }

        /// Whether this GLib has the function.
        public boolean isPresent() {
            return address != null;
        }

        /// Calls `g_log_set_default_handler`.
        ///
        /// @param handler  an upcall stub of GLogFunc's shape
        /// @param userData passed back to the handler untouched; may be
        ///        [MemorySegment#NULL]
        /// @return the handler that was installed before, which may be GLib's own
        public MemorySegment call(MemorySegment handler, MemorySegment userData) {
            try {
                return (MemorySegment) FD_g_log_set_default_handler.invokeExact(address, handler, userData);
            } catch (Throwable t) {
                throw Downcalls.failure("g_log_set_default_handler", t);
            }
        }
    }

    /// Installs the writer every **structured** log record ends at.
    ///
    /// `void g_log_set_writer_func(GLogWriterFunc, void*, GDestroyNotify)`
    ///
    /// **GLib aborts the process if this is called twice.** Not a return code,
    /// not a warning: `g_log_set_writer_func` calls `g_error` when the writer is
    /// no longer the default one, and `g_error` is fatal by definition. So
    /// nothing may call this unless the application has said, in as many words,
    /// that nothing else in its process will — which is what
    /// `GlibLog.WRITER_PROPERTY` is for.
    public static final class SetWriterFunc {

        private static final MethodHandle FD_g_log_set_writer_func =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetWriterFunc(SymbolLookup lookup) {
            this.address = Downcalls.optionalSymbol(lookup, "g_log_set_writer_func");
        }

        /// Whether this GLib has the function. Added in GLib 2.50 (2016), so
        /// "no" means a GLib older than every distribution still supported.
        public boolean isPresent() {
            return address != null;
        }

        /// Calls `g_log_set_writer_func`.
        ///
        /// @param writer   an upcall stub of GLogWriterFunc's shape
        /// @param userData passed back to the writer untouched
        /// @param userDataFree a `GDestroyNotify` for it, or
        ///        [MemorySegment#NULL] when there is nothing to free — which
        ///        there is not, because the stub lives in a global arena
        public void call(MemorySegment writer, MemorySegment userData, MemorySegment userDataFree) {
            try {
                FD_g_log_set_writer_func.invokeExact(address, writer, userData, userDataFree);
            } catch (Throwable t) {
                throw Downcalls.failure("g_log_set_writer_func", t);
            }
        }
    }
}
