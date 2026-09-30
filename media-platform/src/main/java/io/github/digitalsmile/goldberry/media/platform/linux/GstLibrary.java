package io.github.digitalsmile.goldberry.media.platform.linux;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// The GStreamer and GLib libraries the providers bind, and the linking they
/// share.
///
/// Opened by soname, as a distribution installs them. GStreamer's ABI has been
/// stable across 1.x since 1.0, and the bindings use nothing newer than 1.20
/// (`gst_app_src_get_current_level_buffers`), so any maintained distribution has
/// what they need. Nothing is built or shipped.
///
/// The binding classes follow `:media`'s idiom (ADR-0173), as the macOS ones do:
/// each function's unbound handle is a `private static final` constant, linked
/// once, and its address is a field found in the loaded library (ADR-0161).
enum GstLibrary {
    GLIB("libglib-2.0.so.0"),
    GSTREAMER("libgstreamer-1.0.so.0"),
    GST_APP("libgstapp-1.0.so.0"),
    GST_VIDEO("libgstvideo-1.0.so.0");

    private static final Linker LINKER = Linker.nativeLinker();

    /// Every descriptor linked so far, in the order linked, for the native-image
    /// metadata `PlatformForeignMetadata` writes (ADR-0339).
    private static final Set<FunctionDescriptor> LINKED = new LinkedHashSet<>();

    private final String soname;

    GstLibrary(String soname) {
        this.soname = soname;
    }

    /// The name the dynamic loader finds the library by.
    String soname() {
        return soname;
    }

    /// Opens the library for the life of the process: a library is never unloaded
    /// while handles bound against it may still be called.
    ///
    /// @throws IllegalArgumentException when it cannot be opened: not installed
    @SuppressWarnings("restricted")
    SymbolLookup open() {
        return SymbolLookup.libraryLookup(soname, Arena.global());
    }

    /// The address of the function `symbol`.
    ///
    /// @throws UnsatisfiedLinkError when the library does not export it: a
    ///                              GStreamer older than the bindings need
    MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(soname + " does not export " + symbol));
    }

    /// The unbound handle for `descriptor`. The obligation that it matches the C
    /// prototype is the binding's, which writes the prototype beside it.
    @SuppressWarnings("restricted")
    static MethodHandle link(FunctionDescriptor descriptor) {
        synchronized (LINKED) {
            LINKED.add(descriptor);
        }
        return LINKER.downcallHandle(descriptor);
    }

    /// The distinct descriptors linked so far, in the order first linked.
    static List<FunctionDescriptor> linked() {
        synchronized (LINKED) {
            return List.copyOf(LINKED);
        }
    }

    /// What a binding raises when the crossing itself fails, rather than the
    /// call answering an error.
    static IllegalStateException failure(String function, Throwable cause) {
        return new IllegalStateException(function + "() failed", cause);
    }

    @Override
    public String toString() {
        return soname;
    }
}
