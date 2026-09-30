package io.github.digitalsmile.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/// Calling a COM method, and `IUnknown`.
///
/// A COM object's first eight bytes point at its vtable, an array of function
/// pointers in the order the interface declares its methods, the methods of the
/// interfaces it derives from first. A method is called with the object itself
/// as its first argument (`this`). So a call is: read the vtable pointer, read
/// the function pointer at `slot × 8`, and invoke an unbound downcall handle on
/// it with the object and the method's own arguments ([#method]).
///
/// The slot numbers here and in the other interface classes were written from
/// the SDK headers (`unknwn.h`, `mfobjects.h`, `mftransform.h`), counting each
/// interface's methods after its base's, not measured on a running system.
final class Com {

    /// `IUnknown::QueryInterface` (`unknwn.h`).
    static final int IUnknown_QueryInterface = 0;
    /// `IUnknown::AddRef` (`unknwn.h`).
    static final int IUnknown_AddRef = 1;
    /// `IUnknown::Release` (`unknwn.h`).
    static final int IUnknown_Release = 2;

    /// `HRESULT QueryInterface(IUnknown *this, REFIID riid, void **ppvObject)`
    private static final MethodHandle FD_IUnknown_QueryInterface =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `ULONG Release(IUnknown *this)`, and `ULONG AddRef(IUnknown *this)`
    private static final MethodHandle FD_IUnknown_Release =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private Com() {}

    /// The function pointer in `object`'s vtable at `slot`.
    @SuppressWarnings("restricted")
    static MemorySegment method(MemorySegment object, int slot) {
        if (object.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("a COM call on a null object, slot " + slot);
        }
        var vtable = object.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
        return vtable.reinterpret(ADDRESS.byteSize() * (slot + 1L)).getAtIndex(ADDRESS, slot);
    }

    /// Releases a reference to `object`; nothing for `NULL`.
    static void release(MemorySegment object) {
        if (object.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            var remaining = (int) FD_IUnknown_Release.invokeExact(method(object, IUnknown_Release), object);
            assert remaining >= 0;
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IUnknown::Release", t);
        }
    }

    /// `object`'s interface `iid`, with a reference the caller releases, or
    /// `NULL` when the object does not have it.
    static MemorySegment queryInterface(MemorySegment object, Guid iid) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            int hr;
            try {
                hr = (int) FD_IUnknown_QueryInterface.invokeExact(
                        method(object, IUnknown_QueryInterface), object, iid.segment(), out);
            } catch (Throwable t) {
                throw WindowsLibrary.rethrow("IUnknown::QueryInterface", t);
            }
            if (hr == HResult.E_NOINTERFACE) {
                return MemorySegment.NULL;
            }
            HResult.check("IUnknown::QueryInterface", hr);
            return out.get(ADDRESS, 0);
        }
    }
}
