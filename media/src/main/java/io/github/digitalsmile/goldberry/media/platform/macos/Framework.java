package io.github.digitalsmile.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// The macOS system frameworks the providers bind, and the linking they share.
///
/// The frameworks are part of the operating system: nothing is built, shipped or
/// unpacked, and every Mac has them. They are opened by their install path, which
/// `dlopen` resolves from the dyld shared cache even though no file is on disk
/// there.
///
/// The binding classes follow `:media`'s idiom (ADR-0173): each function's
/// unbound handle is a `private static final` constant, linked once, and its
/// address is a field found in the loaded framework. A constant handle is what
/// lets a native image compile the call rather than interpret it (ADR-0161).
enum Framework {
    CORE_FOUNDATION("CoreFoundation"),
    CORE_MEDIA("CoreMedia"),
    CORE_VIDEO("CoreVideo"),
    VIDEO_TOOLBOX("VideoToolbox"),
    AUDIO_TOOLBOX("AudioToolbox"),
    CORE_AUDIO("CoreAudio");

    private static final Linker LINKER = Linker.nativeLinker();

    /// Every descriptor linked so far, in the order linked, for the native-image
    /// metadata `MediaForeignMetadata` writes, as `FfmpegDowncalls` records
    /// `:media`'s (ADR-0339).
    private static final Set<FunctionDescriptor> LINKED = new LinkedHashSet<>();

    private final String name;

    Framework(String name) {
        this.name = name;
    }

    /// Where the framework's binary is installed.
    String path() {
        return "/System/Library/Frameworks/" + name + ".framework/" + name;
    }

    /// Opens the framework for the life of the process: a framework is never
    /// unloaded while handles bound against it may still be called.
    ///
    /// @throws IllegalArgumentException when it cannot be opened: not macOS
    @SuppressWarnings("restricted")
    SymbolLookup open() {
        return SymbolLookup.libraryLookup(path(), Arena.global());
    }

    /// The address of the function or data `symbol`.
    ///
    /// @throws UnsatisfiedLinkError when the framework does not export it: a macOS
    ///                              older than the bindings need
    MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(name + " does not export " + symbol));
    }

    /// The address of `symbol`, or empty on a macOS that does not have it.
    Optional<MemorySegment> optionalSymbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol);
    }

    /// The value of the exported constant `symbol`, a `CFStringRef` or another
    /// pointer: what the variable at the symbol's address holds.
    @SuppressWarnings("restricted")
    MemorySegment constant(SymbolLookup lookup, String symbol) {
        return symbol(lookup, symbol).reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
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
        return name;
    }
}
