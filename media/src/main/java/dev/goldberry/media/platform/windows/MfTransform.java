package dev.goldberry.media.platform.windows;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;

/// `IMFTransform`, a Media Foundation transform (`mftransform.h`), and the one
/// method of `IMFActivate` that makes one (`mfobjects.h`).
final class MfTransform {

    // IMFTransform follows IUnknown's three slots directly (mftransform.h):
    // GetStreamLimits 3, GetStreamCount 4, GetStreamIDs 5, GetInputStreamInfo 6,
    // then the slots used here, with GetAttributes 8 … AddInputStreams 12,
    // GetInputAvailableType 13, GetInputCurrentType 17, GetInputStatus 19,
    // GetOutputStatus 20, SetOutputBounds 21 and ProcessEvent 22 between them.
    // Written from the header, not measured.

    /// `IMFTransform::GetOutputStreamInfo` (`mftransform.h`).
    static final int IMFTransform_GetOutputStreamInfo = 7;
    /// `IMFTransform::GetOutputAvailableType` (`mftransform.h`).
    static final int IMFTransform_GetOutputAvailableType = 14;
    /// `IMFTransform::SetInputType` (`mftransform.h`).
    static final int IMFTransform_SetInputType = 15;
    /// `IMFTransform::SetOutputType` (`mftransform.h`).
    static final int IMFTransform_SetOutputType = 16;
    /// `IMFTransform::GetOutputCurrentType` (`mftransform.h`).
    static final int IMFTransform_GetOutputCurrentType = 18;
    /// `IMFTransform::ProcessMessage` (`mftransform.h`).
    static final int IMFTransform_ProcessMessage = 23;
    /// `IMFTransform::ProcessInput` (`mftransform.h`).
    static final int IMFTransform_ProcessInput = 24;
    /// `IMFTransform::ProcessOutput` (`mftransform.h`).
    static final int IMFTransform_ProcessOutput = 25;

    /// `IMFActivate::ActivateObject` (`mfobjects.h`): after IMFAttributes' 33
    /// slots (0-32); `ShutdownObject` 34 and `DetachObject` 35 follow.
    static final int IMFActivate_ActivateObject = 33;

    // MFT_MESSAGE_TYPE (mftransform.h).

    /// `MFT_MESSAGE_COMMAND_FLUSH`: drop everything held.
    static final int MESSAGE_COMMAND_FLUSH = 0;
    /// `MFT_MESSAGE_COMMAND_DRAIN`: produce everything held, then ask for input.
    static final int MESSAGE_COMMAND_DRAIN = 1;
    /// `MFT_MESSAGE_NOTIFY_BEGIN_STREAMING`.
    static final int MESSAGE_NOTIFY_BEGIN_STREAMING = 0x1000_0000;
    /// `MFT_MESSAGE_NOTIFY_END_OF_STREAM`.
    static final int MESSAGE_NOTIFY_END_OF_STREAM = 0x1000_0002;
    /// `MFT_MESSAGE_NOTIFY_START_OF_STREAM`.
    static final int MESSAGE_NOTIFY_START_OF_STREAM = 0x1000_0003;

    // _MFT_OUTPUT_STREAM_INFO_FLAGS (mftransform.h).

    /// `MFT_OUTPUT_STREAM_PROVIDES_SAMPLES`: the transform allocates its output
    /// samples, and a caller's are not taken.
    static final int OUTPUT_STREAM_PROVIDES_SAMPLES = 0x100;
    /// `MFT_OUTPUT_STREAM_CAN_PROVIDE_SAMPLES`: the transform allocates its
    /// output samples when the caller passes none.
    static final int OUTPUT_STREAM_CAN_PROVIDE_SAMPLES = 0x200;

    /// `MFT_OUTPUT_DATA_BUFFER` (`mftransform.h`), x64: 32 bytes with the padding
    /// that aligns each pointer.
    static final StructLayout OUTPUT_DATA_BUFFER = MemoryLayout.structLayout(
            JAVA_INT.withName("dwStreamID"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("pSample"),
            JAVA_INT.withName("dwStatus"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("pEvents"));

    /// `MFT_OUTPUT_STREAM_INFO` (`mftransform.h`): 12 bytes.
    static final StructLayout OUTPUT_STREAM_INFO = MemoryLayout.structLayout(
            JAVA_INT.withName("dwFlags"), JAVA_INT.withName("cbSize"), JAVA_INT.withName("cbAlignment"));

    static final long OUTPUT_SAMPLE = OUTPUT_DATA_BUFFER.byteOffset(groupElement("pSample"));
    static final long OUTPUT_STATUS = OUTPUT_DATA_BUFFER.byteOffset(groupElement("dwStatus"));
    static final long OUTPUT_EVENTS = OUTPUT_DATA_BUFFER.byteOffset(groupElement("pEvents"));

    private static final long INFO_FLAGS = OUTPUT_STREAM_INFO.byteOffset(groupElement("dwFlags"));
    private static final long INFO_SIZE = OUTPUT_STREAM_INFO.byteOffset(groupElement("cbSize"));
    private static final long INFO_ALIGNMENT = OUTPUT_STREAM_INFO.byteOffset(groupElement("cbAlignment"));

    /// `HRESULT GetOutputStreamInfo(IMFTransform *this, DWORD dwOutputStreamID, MFT_OUTPUT_STREAM_INFO *pStreamInfo)`,
    /// and `HRESULT GetOutputCurrentType(IMFTransform *this, DWORD dwOutputStreamID, IMFMediaType **ppType)`
    private static final MethodHandle FD_IMFTransform_StreamPointer =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    /// `HRESULT GetOutputAvailableType(IMFTransform *this, DWORD dwOutputStreamID, DWORD dwTypeIndex,
    /// IMFMediaType **ppType)`
    private static final MethodHandle FD_IMFTransform_GetOutputAvailableType =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

    /// `HRESULT SetInputType(IMFTransform *this, DWORD dwInputStreamID, IMFMediaType *pType, DWORD dwFlags)`,
    /// `SetOutputType`, and `HRESULT ProcessInput(IMFTransform *this, DWORD dwInputStreamID, IMFSample *pSample,
    /// DWORD dwFlags)`: the same shape
    private static final MethodHandle FD_IMFTransform_StreamPointerFlags =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

    /// `HRESULT ProcessMessage(IMFTransform *this, MFT_MESSAGE_TYPE eMessage, ULONG_PTR ulParam)`
    private static final MethodHandle FD_IMFTransform_ProcessMessage =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));

    /// `HRESULT ProcessOutput(IMFTransform *this, DWORD dwFlags, DWORD cOutputBufferCount,
    /// MFT_OUTPUT_DATA_BUFFER *pOutputSamples, DWORD *pdwStatus)`
    private static final MethodHandle FD_IMFTransform_ProcessOutput =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

    /// `HRESULT ActivateObject(IMFActivate *this, REFIID riid, void **ppv)`
    private static final MethodHandle FD_IMFActivate_ActivateObject =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// The decoders' streams: the Microsoft decoders have one input and one
    /// output, fixed, numbered 0.
    static final int STREAM = 0;

    private MfTransform() {}

    /// What `GetOutputStreamInfo` says of the output stream.
    ///
    /// @param flags     `MFT_OUTPUT_STREAM_…` flags
    /// @param size      the bytes an output buffer needs, or 0 when the
    ///                  transform does not say
    /// @param alignment the alignment an output buffer needs, or 0 or 1 for none
    record StreamInfo(int flags, int size, int alignment) {

        /// Whether the caller must allocate the output samples.
        boolean callerAllocates() {
            return (flags & (OUTPUT_STREAM_PROVIDES_SAMPLES | OUTPUT_STREAM_CAN_PROVIDE_SAMPLES)) == 0;
        }
    }

    /// Makes the object `activate` stands for, as an `IMFTransform` the caller
    /// releases.
    static MemorySegment activate(MemorySegment activate) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            HResult.check("IMFActivate::ActivateObject", (int) FD_IMFActivate_ActivateObject.invokeExact(
                    Com.method(activate, IMFActivate_ActivateObject),
                    activate,
                    MfGuids.IID_IMFTransform.segment(),
                    out));
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFActivate::ActivateObject", t);
        }
    }

    /// The output stream's requirements.
    static StreamInfo outputStreamInfo(MemorySegment transform) {
        try (var arena = Arena.ofConfined()) {
            var info = arena.allocate(OUTPUT_STREAM_INFO);
            HResult.check("IMFTransform::GetOutputStreamInfo", (int) FD_IMFTransform_StreamPointer.invokeExact(
                    Com.method(transform, IMFTransform_GetOutputStreamInfo), transform, STREAM, info));
            return new StreamInfo(
                    info.get(JAVA_INT, INFO_FLAGS), info.get(JAVA_INT, INFO_SIZE), info.get(JAVA_INT, INFO_ALIGNMENT));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFTransform::GetOutputStreamInfo", t);
        }
    }

    /// The output type at `index` in the transform's list, best first, with a
    /// reference the caller releases; `NULL` past the end of the list.
    static MemorySegment outputAvailableType(MemorySegment transform, int index) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            var hr = (int) FD_IMFTransform_GetOutputAvailableType.invokeExact(
                    Com.method(transform, IMFTransform_GetOutputAvailableType), transform, STREAM, index, out);
            if (hr == HResult.MF_E_NO_MORE_TYPES) {
                return MemorySegment.NULL;
            }
            HResult.check("IMFTransform::GetOutputAvailableType", hr);
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFTransform::GetOutputAvailableType", t);
        }
    }

    /// Offers `type` as the input type: what the transform answers, a failure
    /// when it does not take it.
    static int setInputType(MemorySegment transform, MemorySegment type) {
        try {
            return (int) FD_IMFTransform_StreamPointerFlags.invokeExact(
                    Com.method(transform, IMFTransform_SetInputType), transform, STREAM, type, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFTransform::SetInputType", t);
        }
    }

    /// Sets the output type to `type`.
    static void setOutputType(MemorySegment transform, MemorySegment type) {
        try {
            HResult.check("IMFTransform::SetOutputType", (int) FD_IMFTransform_StreamPointerFlags.invokeExact(
                    Com.method(transform, IMFTransform_SetOutputType), transform, STREAM, type, 0));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFTransform::SetOutputType", t);
        }
    }

    /// The output type set, with a reference the caller releases.
    static MemorySegment outputCurrentType(MemorySegment transform) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            HResult.check("IMFTransform::GetOutputCurrentType", (int) FD_IMFTransform_StreamPointer.invokeExact(
                    Com.method(transform, IMFTransform_GetOutputCurrentType), transform, STREAM, out));
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFTransform::GetOutputCurrentType", t);
        }
    }

    /// Sends the transform `message`, one of the `MESSAGE_…` constants.
    static void processMessage(MemorySegment transform, int message) {
        try {
            HResult.check("IMFTransform::ProcessMessage", (int) FD_IMFTransform_ProcessMessage.invokeExact(
                    Com.method(transform, IMFTransform_ProcessMessage), transform, message, 0L));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFTransform::ProcessMessage", t);
        }
    }

    /// Hands the transform `sample`: what it answers, `MF_E_NOTACCEPTING` when it
    /// has output to take first.
    static int processInput(MemorySegment transform, MemorySegment sample) {
        try {
            return (int) FD_IMFTransform_StreamPointerFlags.invokeExact(
                    Com.method(transform, IMFTransform_ProcessInput), transform, STREAM, sample, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFTransform::ProcessInput", t);
        }
    }

    /// Asks the transform for output into `outputBuffer`, one
    /// [#OUTPUT_DATA_BUFFER]: what it answers, `MF_E_TRANSFORM_NEED_MORE_INPUT`
    /// when it has none.
    static int processOutput(MemorySegment transform, MemorySegment outputBuffer, MemorySegment status) {
        try {
            return (int) FD_IMFTransform_ProcessOutput.invokeExact(
                    Com.method(transform, IMFTransform_ProcessOutput), transform, 0, 1, outputBuffer, status);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("IMFTransform::ProcessOutput", t);
        }
    }
}
