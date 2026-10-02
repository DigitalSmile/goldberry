package dev.goldberry.media.platform.windows;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// The Windows system libraries the providers bind, and the linking they share.
///
/// The libraries are part of the operating system: nothing is built, shipped or
/// unpacked. They are opened by name, which the loader resolves from the system
/// directory.
///
/// The binding classes follow `:media`'s idiom, as the macOS ones do:
/// each function's unbound handle is a `private static final` constant, linked
/// once. An exported function's address is a field found in the loaded
/// library; a COM method's is read from the object's vtable on each call
/// ([Com#method]). A constant handle is what lets a native image compile the
/// call rather than interpret it.
enum WindowsLibrary {
    MFPLAT("mfplat.dll"),
    OLE32("ole32.dll");

    private static final Linker LINKER = Linker.nativeLinker();

    /// Every descriptor linked so far, in the order linked, for the native-image
    /// metadata, as the macOS `Framework` records its own.
    private static final Set<FunctionDescriptor> LINKED = new LinkedHashSet<>();

    private final String fileName;

    WindowsLibrary(String fileName) {
        this.fileName = fileName;
    }

    /// The library's file name, which the loader finds in the system directory.
    String fileName() {
        return fileName;
    }

    /// Opens the library for the life of the process: a library is never
    /// unloaded while handles bound against it may still be called.
    ///
    /// @throws IllegalArgumentException when it cannot be opened: not Windows
    @SuppressWarnings("restricted")
    SymbolLookup open() {
        return SymbolLookup.libraryLookup(fileName, Arena.global());
    }

    /// The address of the function `symbol`.
    ///
    /// @throws UnsatisfiedLinkError when the library does not export it
    MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(fileName + " does not export " + symbol));
    }

    /// The unbound handle for `descriptor`. The obligation that it matches the C
    /// prototype is the binding's, which writes the prototype beside it. Linking
    /// needs no library, so this runs on any operating system.
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
    /// call answering an `HRESULT`.
    static IllegalStateException failure(String function, Throwable cause) {
        return new IllegalStateException(function + "() failed", cause);
    }

    /// `t` as it is rethrown from a binding: a runtime exception as it is, and
    /// anything else, which a downcall does not throw, wrapped.
    static RuntimeException rethrow(String function, Throwable t) {
        return t instanceof RuntimeException e ? e : failure(function, t);
    }

    @Override
    public String toString() {
        return fileName;
    }
}
