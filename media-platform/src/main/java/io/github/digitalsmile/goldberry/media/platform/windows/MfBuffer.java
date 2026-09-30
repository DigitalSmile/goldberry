package io.github.digitalsmile.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/// `IMFMediaBuffer` and `IMF2DBuffer`: a block of memory and, for a picture, its
/// rows (`mfobjects.h`).
final class MfBuffer {

    // Both interfaces follow IUnknown's three slots directly (mfobjects.h).
    // Written from the header, not measured.

    /// `IMFMediaBuffer::Lock` (`mfobjects.h`).
    static final int IMFMediaBuffer_Lock = 3;
    /// `IMFMediaBuffer::Unlock` (`mfobjects.h`).
    static final int IMFMediaBuffer_Unlock = 4;
    /// `IMFMediaBuffer::GetCurrentLength` (`mfobjects.h`).
    static final int IMFMediaBuffer_GetCurrentLength = 5;
    /// `IMFMediaBuffer::SetCurrentLength` (`mfobjects.h`).
    static final int IMFMediaBuffer_SetCurrentLength = 6;
    /// `IMFMediaBuffer::GetMaxLength` (`mfobjects.h`).
    static final int IMFMediaBuffer_GetMaxLength = 7;

    /// `IMF2DBuffer::Lock2D` (`mfobjects.h`).
    static final int IMF2DBuffer_Lock2D = 3;
    /// `IMF2DBuffer::Unlock2D` (`mfobjects.h`).
    static final int IMF2DBuffer_Unlock2D = 4;

    /// `HRESULT Lock(IMFMediaBuffer *this, BYTE **ppbBuffer, DWORD *pcbMaxLength, DWORD *pcbCurrentLength)`
    private static final MethodHandle FD_IMFMediaBuffer_Lock =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /// `HRESULT Unlock(IMFMediaBuffer *this)`, and `HRESULT Unlock2D(IMF2DBuffer *this)`
    private static final MethodHandle FD_IMFMediaBuffer_Unlock =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `HRESULT GetCurrentLength(IMFMediaBuffer *this, DWORD *pcbCurrentLength)`
    private static final MethodHandle FD_IMFMediaBuffer_GetCurrentLength =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /// `HRESULT SetCurrentLength(IMFMediaBuffer *this, DWORD cbCurrentLength)`
    private static final MethodHandle FD_IMFMediaBuffer_SetCurrentLength =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// `HRESULT Lock2D(IMF2DBuffer *this, BYTE **ppbScanline0, LONG *plPitch)`
    private static final MethodHandle FD_IMF2DBuffer_Lock2D =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    private MfBuffer() {}

    /// A locked buffer's memory.
    ///
    /// @param data          the first byte
    /// @param maxLength     bytes that may be written
    /// @param currentLength bytes that hold data
    record Locked(MemorySegment data, int maxLength, int currentLength) {}

    /// A locked 2D buffer's first row and pitch: bytes from one row to the next,
    /// negative for a bottom-up picture.
    record Locked2D(MemorySegment scanline0, int pitch) {}

    /// Locks `buffer`'s memory until [#unlock], sized to its maximum length.
    @SuppressWarnings("restricted")
    static Locked lock(MemorySegment buffer) {
        try (var arena = Arena.ofConfined()) {
            var data = arena.allocate(ADDRESS);
            var max = arena.allocate(JAVA_INT);
            var current = arena.allocate(JAVA_INT);
            HResult.check("IMFMediaBuffer::Lock", (int) FD_IMFMediaBuffer_Lock.invokeExact(
                    Com.method(buffer, IMFMediaBuffer_Lock), buffer, data, max, current));
            var maxLength = max.get(JAVA_INT, 0);
            return new Locked(
                    data.get(ADDRESS, 0).reinterpret(Integer.toUnsignedLong(maxLength)),
                    maxLength,
                    current.get(JAVA_INT, 0));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFMediaBuffer::Lock", t);
        }
    }

    /// Unlocks what [#lock] locked.
    static void unlock(MemorySegment buffer) {
        try {
            HResult.check("IMFMediaBuffer::Unlock", (int)
                    FD_IMFMediaBuffer_Unlock.invokeExact(Com.method(buffer, IMFMediaBuffer_Unlock), buffer));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFMediaBuffer::Unlock", t);
        }
    }

    /// The bytes of `buffer` that hold data.
    static int currentLength(MemorySegment buffer) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_INT);
            HResult.check("IMFMediaBuffer::GetCurrentLength", (int) FD_IMFMediaBuffer_GetCurrentLength.invokeExact(
                    Com.method(buffer, IMFMediaBuffer_GetCurrentLength), buffer, out));
            return out.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFMediaBuffer::GetCurrentLength", t);
        }
    }

    /// Says how many of `buffer`'s bytes hold data.
    static void setCurrentLength(MemorySegment buffer, int length) {
        try {
            HResult.check("IMFMediaBuffer::SetCurrentLength", (int) FD_IMFMediaBuffer_SetCurrentLength.invokeExact(
                    Com.method(buffer, IMFMediaBuffer_SetCurrentLength), buffer, length));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFMediaBuffer::SetCurrentLength", t);
        }
    }

    /// Locks the 2D buffer `buffer2d` until [#unlock2D]. The first row's segment
    /// is unsized: the caller knows the picture's rows.
    static Locked2D lock2D(MemorySegment buffer2d) {
        try (var arena = Arena.ofConfined()) {
            var scanline0 = arena.allocate(ADDRESS);
            var pitch = arena.allocate(JAVA_INT);
            HResult.check("IMF2DBuffer::Lock2D", (int) FD_IMF2DBuffer_Lock2D.invokeExact(
                    Com.method(buffer2d, IMF2DBuffer_Lock2D), buffer2d, scanline0, pitch));
            return new Locked2D(scanline0.get(ADDRESS, 0), pitch.get(JAVA_INT, 0));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMF2DBuffer::Lock2D", t);
        }
    }

    /// Unlocks what [#lock2D] locked.
    static void unlock2D(MemorySegment buffer2d) {
        try {
            HResult.check("IMF2DBuffer::Unlock2D", (int)
                    FD_IMFMediaBuffer_Unlock.invokeExact(Com.method(buffer2d, IMF2DBuffer_Unlock2D), buffer2d));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMF2DBuffer::Unlock2D", t);
        }
    }
}
