package io.github.digitalsmile.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// The two COM runtime functions the decoders need, from `ole32.dll`: joining a
/// thread to COM, and freeing the memory a COM call allocated.
final class Ole32 {

    /// `COINIT_MULTITHREADED` (`objbase.h`): the thread joins the process's
    /// multi-threaded apartment, where Media Foundation's objects may be called
    /// from any thread.
    static final int COINIT_MULTITHREADED = 0;

    /// `HRESULT CoInitializeEx(LPVOID pvReserved, DWORD dwCoInit)`
    private static final MethodHandle FD_CoInitializeEx =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// `void CoTaskMemFree(LPVOID pv)`
    private static final MethodHandle FD_CoTaskMemFree = WindowsLibrary.link(FunctionDescriptor.ofVoid(ADDRESS));

    private final MemorySegment initializeEx;
    private final MemorySegment taskMemFree;

    Ole32(SymbolLookup lookup) {
        var l = WindowsLibrary.OLE32;
        this.initializeEx = l.symbol(lookup, "CoInitializeEx");
        this.taskMemFree = l.symbol(lookup, "CoTaskMemFree");
    }

    /// Joins the calling thread to the multi-threaded apartment. A thread
    /// already joined answers `S_FALSE`, and one already in a single-threaded
    /// apartment `RPC_E_CHANGED_MODE`; Media Foundation works from either, so
    /// both are success here.
    void initializeMultithreaded() {
        int hr;
        try {
            hr = (int) FD_CoInitializeEx.invokeExact(initializeEx, MemorySegment.NULL, COINIT_MULTITHREADED);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("CoInitializeEx", t);
        }
        if (hr != HResult.RPC_E_CHANGED_MODE) {
            HResult.check("CoInitializeEx", hr);
        }
    }

    /// Frees memory a COM call allocated for its caller; nothing for `NULL`.
    void taskMemFree(MemorySegment memory) {
        if (memory.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            FD_CoTaskMemFree.invokeExact(taskMemFree, memory);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("CoTaskMemFree", t);
        }
    }
}
