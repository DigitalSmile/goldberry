package dev.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.OptionalLong;

/// `IMFSample`: a unit of media, its time and the buffers that hold it
/// (`mfobjects.h`).
final class MfSample {

    // IMFSample follows IMFAttributes' 33 slots (0-32) (mfobjects.h):
    // GetSampleFlags 33, SetSampleFlags 34, then the slots used here, then
    // GetSampleDuration 37, SetSampleDuration 38, GetBufferCount 39,
    // GetBufferByIndex 40, …, RemoveBufferByIndex 43, RemoveAllBuffers 44,
    // GetTotalLength 45, CopyToBuffer 46. Written from the header, not measured.

    /// `IMFSample::GetSampleTime` (`mfobjects.h`).
    static final int IMFSample_GetSampleTime = 35;
    /// `IMFSample::SetSampleTime` (`mfobjects.h`).
    static final int IMFSample_SetSampleTime = 36;
    /// `IMFSample::ConvertToContiguousBuffer` (`mfobjects.h`).
    static final int IMFSample_ConvertToContiguousBuffer = 41;
    /// `IMFSample::AddBuffer` (`mfobjects.h`).
    static final int IMFSample_AddBuffer = 42;

    /// `HRESULT GetSampleTime(IMFSample *this, LONGLONG *phnsSampleTime)`, and
    /// `HRESULT ConvertToContiguousBuffer(IMFSample *this, IMFMediaBuffer **ppBuffer)`, and
    /// `HRESULT AddBuffer(IMFSample *this, IMFMediaBuffer *pBuffer)`: a pointer each
    private static final MethodHandle FD_IMFSample_PointerArgument =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /// `HRESULT SetSampleTime(IMFSample *this, LONGLONG hnsSampleTime)`
    private static final MethodHandle FD_IMFSample_SetSampleTime =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));

    private MfSample() {}

    /// When `sample` is presented, in 100-nanosecond units, or empty when it has
    /// no time.
    static OptionalLong time(MemorySegment sample) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_LONG);
            int hr;
            try {
                hr = (int) FD_IMFSample_PointerArgument.invokeExact(
                        Com.method(sample, IMFSample_GetSampleTime), sample, out);
            } catch (Throwable t) {
                throw WindowsLibrary.rethrow("IMFSample::GetSampleTime", t);
            }
            // MF_E_NO_SAMPLE_TIMESTAMP, or any other failure: no time.
            return HResult.failed(hr) ? OptionalLong.empty() : OptionalLong.of(out.get(JAVA_LONG, 0));
        }
    }

    /// Sets when `sample` is presented, in 100-nanosecond units.
    static void setTime(MemorySegment sample, long hundredNanos) {
        try {
            HResult.check("IMFSample::SetSampleTime", (int) FD_IMFSample_SetSampleTime.invokeExact(
                    Com.method(sample, IMFSample_SetSampleTime), sample, hundredNanos));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFSample::SetSampleTime", t);
        }
    }

    /// Adds `buffer` to `sample`, which takes a reference of its own.
    static void addBuffer(MemorySegment sample, MemorySegment buffer) {
        try {
            HResult.check("IMFSample::AddBuffer", (int)
                    FD_IMFSample_PointerArgument.invokeExact(Com.method(sample, IMFSample_AddBuffer), sample, buffer));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFSample::AddBuffer", t);
        }
    }

    /// `sample`'s data as one buffer, with a reference the caller releases: its
    /// only buffer, or a copy of its several into one.
    static MemorySegment contiguousBuffer(MemorySegment sample) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            HResult.check("IMFSample::ConvertToContiguousBuffer", (int) FD_IMFSample_PointerArgument.invokeExact(
                    Com.method(sample, IMFSample_ConvertToContiguousBuffer), sample, out));
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFSample::ConvertToContiguousBuffer", t);
        }
    }
}
