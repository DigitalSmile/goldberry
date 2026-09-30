package io.github.digitalsmile.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/// `IMFAttributes`: the key–value store every media type, sample and activation
/// object is (`mfobjects.h`).
///
/// A getter answers empty for an attribute the object does not have, and for
/// one it has as another type: every attribute read here is optional to the
/// caller, which falls back on a default.
final class MfAttributes {

    // IMFAttributes follows IUnknown's three methods (mfobjects.h): GetItem 3,
    // GetItemType 4, CompareItem 5, Compare 6, then the slots used here, then
    // SetItem 18 … CopyAllItems 32. Written from the header, not measured.

    /// `IMFAttributes::GetUINT32` (`mfobjects.h`).
    static final int IMFAttributes_GetUINT32 = 7;
    /// `IMFAttributes::GetUINT64` (`mfobjects.h`).
    static final int IMFAttributes_GetUINT64 = 8;
    /// `IMFAttributes::GetGUID` (`mfobjects.h`).
    static final int IMFAttributes_GetGUID = 10;
    /// `IMFAttributes::GetBlobSize` (`mfobjects.h`).
    static final int IMFAttributes_GetBlobSize = 14;
    /// `IMFAttributes::GetBlob` (`mfobjects.h`).
    static final int IMFAttributes_GetBlob = 15;
    /// `IMFAttributes::SetUINT32` (`mfobjects.h`).
    static final int IMFAttributes_SetUINT32 = 21;
    /// `IMFAttributes::SetUINT64` (`mfobjects.h`).
    static final int IMFAttributes_SetUINT64 = 22;
    /// `IMFAttributes::SetGUID` (`mfobjects.h`).
    static final int IMFAttributes_SetGUID = 24;
    /// `IMFAttributes::SetBlob` (`mfobjects.h`).
    static final int IMFAttributes_SetBlob = 26;

    /// `HRESULT GetUINT32(IMFAttributes *this, REFGUID guidKey, UINT32 *punValue)`, and `GetUINT64`,
    /// `GetGUID` and `GetBlobSize`, which differ only in what the out-pointer points at
    private static final MethodHandle FD_IMFAttributes_GetValue =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `HRESULT GetBlob(IMFAttributes *this, REFGUID guidKey, UINT8 *pBuf, UINT32 cbBufSize, UINT32 *pcbBlobSize)`
    private static final MethodHandle FD_IMFAttributes_GetBlob =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

    /// `HRESULT SetUINT32(IMFAttributes *this, REFGUID guidKey, UINT32 unValue)`
    private static final MethodHandle FD_IMFAttributes_SetUINT32 =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    /// `HRESULT SetUINT64(IMFAttributes *this, REFGUID guidKey, UINT64 unValue)`
    private static final MethodHandle FD_IMFAttributes_SetUINT64 =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG));

    /// `HRESULT SetGUID(IMFAttributes *this, REFGUID guidKey, REFGUID guidValue)`
    private static final MethodHandle FD_IMFAttributes_SetGUID =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `HRESULT SetBlob(IMFAttributes *this, REFGUID guidKey, const UINT8 *pBuf, UINT32 cbBufSize)`
    private static final MethodHandle FD_IMFAttributes_SetBlob =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

    private MfAttributes() {}

    /// The UINT32 `key` of `attributes`, or empty.
    static OptionalInt getUint32(MemorySegment attributes, Guid key) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_INT);
            var hr = get("GetUINT32", attributes, IMFAttributes_GetUINT32, key, out);
            return HResult.failed(hr) ? OptionalInt.empty() : OptionalInt.of(out.get(JAVA_INT, 0));
        }
    }

    /// The UINT64 `key` of `attributes`, or empty.
    static OptionalLong getUint64(MemorySegment attributes, Guid key) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_LONG);
            var hr = get("GetUINT64", attributes, IMFAttributes_GetUINT64, key, out);
            return HResult.failed(hr) ? OptionalLong.empty() : OptionalLong.of(out.get(JAVA_LONG, 0));
        }
    }

    /// The GUID `key` of `attributes`, or empty.
    static Optional<Guid> getGuid(MemorySegment attributes, Guid key) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(Guid.LAYOUT);
            var hr = get("GetGUID", attributes, IMFAttributes_GetGUID, key, out);
            return HResult.failed(hr) ? Optional.empty() : Optional.of(Guid.read(out, 0));
        }
    }

    /// The blob `key` of `attributes`, or empty.
    static Optional<byte[]> getBlob(MemorySegment attributes, Guid key) {
        try (var arena = Arena.ofConfined()) {
            var size = arena.allocate(JAVA_INT);
            if (HResult.failed(get("GetBlobSize", attributes, IMFAttributes_GetBlobSize, key, size))) {
                return Optional.empty();
            }
            var length = size.get(JAVA_INT, 0);
            if (length <= 0) {
                return Optional.of(new byte[0]);
            }
            var buffer = arena.allocate(length);
            int hr;
            try {
                hr = (int) FD_IMFAttributes_GetBlob.invokeExact(
                        Com.method(attributes, IMFAttributes_GetBlob), attributes, key.segment(), buffer, length, size);
            } catch (Throwable t) {
                throw WindowsLibrary.rethrow("IMFAttributes::GetBlob", t);
            }
            if (HResult.failed(hr)) {
                return Optional.empty();
            }
            return Optional.of(
                    buffer.asSlice(0, Math.min(length, size.get(JAVA_INT, 0))).toArray(JAVA_BYTE));
        }
    }

    /// Sets the UINT32 `key` of `attributes`.
    static void setUint32(MemorySegment attributes, Guid key, int value) {
        try {
            HResult.check("IMFAttributes::SetUINT32", (int) FD_IMFAttributes_SetUINT32.invokeExact(
                    Com.method(attributes, IMFAttributes_SetUINT32), attributes, key.segment(), value));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFAttributes::SetUINT32", t);
        }
    }

    /// Sets the UINT64 `key` of `attributes`.
    static void setUint64(MemorySegment attributes, Guid key, long value) {
        try {
            HResult.check("IMFAttributes::SetUINT64", (int) FD_IMFAttributes_SetUINT64.invokeExact(
                    Com.method(attributes, IMFAttributes_SetUINT64), attributes, key.segment(), value));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFAttributes::SetUINT64", t);
        }
    }

    /// Sets the GUID `key` of `attributes`.
    static void setGuid(MemorySegment attributes, Guid key, Guid value) {
        try {
            HResult.check("IMFAttributes::SetGUID", (int) FD_IMFAttributes_SetGUID.invokeExact(
                    Com.method(attributes, IMFAttributes_SetGUID), attributes, key.segment(), value.segment()));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFAttributes::SetGUID", t);
        }
    }

    /// Sets the blob `key` of `attributes` to a copy of `value`.
    static void setBlob(MemorySegment attributes, Guid key, byte[] value) {
        try (var arena = Arena.ofConfined()) {
            var buffer = arena.allocateFrom(JAVA_BYTE, value);
            HResult.check("IMFAttributes::SetBlob", (int) FD_IMFAttributes_SetBlob.invokeExact(
                    Com.method(attributes, IMFAttributes_SetBlob), attributes, key.segment(), buffer, value.length));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFAttributes::SetBlob", t);
        }
    }

    /// Calls one of the getters that take a key and an out-pointer.
    private static int get(String method, MemorySegment attributes, int slot, Guid key, MemorySegment out) {
        try {
            return (int)
                    FD_IMFAttributes_GetValue.invokeExact(Com.method(attributes, slot), attributes, key.segment(), out);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFAttributes::" + method, t);
        }
    }
}
