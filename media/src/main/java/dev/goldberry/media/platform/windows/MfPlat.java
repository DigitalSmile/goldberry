package dev.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/// The Media Foundation platform functions, from `mfplat.dll`: starting Media
/// Foundation, making the media types, samples and buffers the decoders pass,
/// and finding a decoder.
final class MfPlat {

    /// `MF_VERSION` (`mfapi.h`): `MF_SDK_VERSION << 16 | MF_API_VERSION`, 0x0002
    /// and 0x0070, which Windows 7 and every later release accept.
    static final int MF_VERSION = 0x0002_0070;
    /// `MFSTARTUP_FULL` (`mfapi.h`).
    static final int MFSTARTUP_FULL = 0;

    /// `MFT_ENUM_FLAG_SYNCMFT` (`mfapi.h`): synchronous transforms, the kind
    /// driven by `ProcessInput` and `ProcessOutput` alone.
    static final int MFT_ENUM_FLAG_SYNCMFT = 0x01;
    /// `MFT_ENUM_FLAG_LOCALMFT` (`mfapi.h`): transforms registered in this
    /// process too.
    static final int MFT_ENUM_FLAG_LOCALMFT = 0x10;
    /// `MFT_ENUM_FLAG_SORTANDFILTER` (`mfapi.h`): in merit order, without those
    /// the system has blocked.
    static final int MFT_ENUM_FLAG_SORTANDFILTER = 0x40;

    /// `MFT_REGISTER_TYPE_INFO` (`mfobjects.h`): two GUIDs, 32 bytes.
    static final StructLayout REGISTER_TYPE_INFO =
            MemoryLayout.structLayout(Guid.LAYOUT.withName("guidMajorType"), Guid.LAYOUT.withName("guidSubtype"));

    /// `HRESULT MFStartup(ULONG Version, DWORD dwFlags)`
    private static final MethodHandle FD_MFStartup =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));

    /// `HRESULT MFCreateMediaType(IMFMediaType **ppMFType)`, and
    /// `HRESULT MFCreateSample(IMFSample **ppIMFSample)`
    private static final MethodHandle FD_MFCreateObject = WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `HRESULT MFCreateMemoryBuffer(DWORD cbMaxLength, IMFMediaBuffer **ppBuffer)`
    private static final MethodHandle FD_MFCreateMemoryBuffer =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));

    /// `HRESULT MFCreateAlignedMemoryBuffer(DWORD cbMaxLength, DWORD cbAligment, IMFMediaBuffer **ppBuffer)`
    private static final MethodHandle FD_MFCreateAlignedMemoryBuffer =
            WindowsLibrary.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));

    /// `HRESULT MFTEnumEx(GUID guidCategory, UINT32 Flags, const MFT_REGISTER_TYPE_INFO *pInputType,
    /// const MFT_REGISTER_TYPE_INFO *pOutputType, IMFActivate ***pppMFTActivate, UINT32 *pnumMFTActivate)`.
    /// The category is a GUID passed by value, which the x64 convention passes
    /// as a pointer to a copy; the linker makes the copy.
    private static final MethodHandle FD_MFTEnumEx = WindowsLibrary.link(
            FunctionDescriptor.of(JAVA_INT, Guid.LAYOUT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private final Ole32 ole32;
    private final MemorySegment startup;
    private final MemorySegment createMediaType;
    private final MemorySegment createSample;
    private final MemorySegment createMemoryBuffer;
    private final MemorySegment createAlignedMemoryBuffer;
    private final MemorySegment enumEx;

    MfPlat(SymbolLookup lookup, Ole32 ole32) {
        this.ole32 = ole32;
        var l = WindowsLibrary.MFPLAT;
        this.startup = l.symbol(lookup, "MFStartup");
        this.createMediaType = l.symbol(lookup, "MFCreateMediaType");
        this.createSample = l.symbol(lookup, "MFCreateSample");
        this.createMemoryBuffer = l.symbol(lookup, "MFCreateMemoryBuffer");
        this.createAlignedMemoryBuffer = l.symbol(lookup, "MFCreateAlignedMemoryBuffer");
        this.enumEx = l.symbol(lookup, "MFTEnumEx");
    }

    /// Starts Media Foundation for the process. Once is enough; each call must be
    /// matched by an `MFShutdown`, and a process that decodes until it exits
    /// never makes one.
    void startup() {
        try {
            HResult.check("MFStartup", (int) FD_MFStartup.invokeExact(startup, MF_VERSION, MFSTARTUP_FULL));
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("MFStartup", t);
        }
    }

    /// A new, empty `IMFMediaType`, which the caller releases.
    MemorySegment createMediaType() {
        return create("MFCreateMediaType", createMediaType);
    }

    /// A new `IMFSample` with no buffers, which the caller releases.
    MemorySegment createSample() {
        return create("MFCreateSample", createSample);
    }

    /// A new `IMFMediaBuffer` of `maxLength` bytes, which the caller releases.
    MemorySegment createMemoryBuffer(int maxLength) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            HResult.check("MFCreateMemoryBuffer", (int)
                    FD_MFCreateMemoryBuffer.invokeExact(createMemoryBuffer, maxLength, out));
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("MFCreateMemoryBuffer", t);
        }
    }

    /// A new `IMFMediaBuffer` of `maxLength` bytes starting on a multiple of
    /// `alignment` bytes (a power of two, or 0 or 1 for none), which the caller
    /// releases.
    MemorySegment createAlignedMemoryBuffer(int maxLength, int alignment) {
        // The flag is the alignment less one: MF_16_BYTE_ALIGNMENT is 0x0F.
        var flag = Math.max(alignment, 1) - 1;
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            HResult.check("MFCreateAlignedMemoryBuffer", (int)
                    FD_MFCreateAlignedMemoryBuffer.invokeExact(createAlignedMemoryBuffer, maxLength, flag, out));
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow("MFCreateAlignedMemoryBuffer", t);
        }
    }

    /// The `IMFActivate`s of the decoders in `category` that take `major` and
    /// `subtype` as input, best first: synchronous, local ones included, sorted
    /// by merit. The caller releases each; the array they came in is freed here.
    @SuppressWarnings("restricted")
    List<MemorySegment> enumerate(Guid category, Guid major, Guid subtype) {
        try (var arena = Arena.ofConfined()) {
            var input = arena.allocate(REGISTER_TYPE_INFO);
            major.write(input, 0);
            subtype.write(input, Guid.LAYOUT.byteSize());
            var categoryByValue = arena.allocate(Guid.LAYOUT);
            category.write(categoryByValue, 0);
            var array = arena.allocate(ADDRESS);
            var count = arena.allocate(JAVA_INT);
            var flags = MFT_ENUM_FLAG_SYNCMFT | MFT_ENUM_FLAG_LOCALMFT | MFT_ENUM_FLAG_SORTANDFILTER;
            int hr;
            try {
                hr = (int) FD_MFTEnumEx.invokeExact(
                        enumEx, categoryByValue, flags, input, MemorySegment.NULL, array, count);
            } catch (Throwable t) {
                throw WindowsLibrary.rethrow("MFTEnumEx", t);
            }
            HResult.check("MFTEnumEx", hr);
            var n = count.get(JAVA_INT, 0);
            var activates = array.get(ADDRESS, 0);
            if (activates.equals(MemorySegment.NULL)) {
                return List.of();
            }
            try {
                var elements = activates.reinterpret(ADDRESS.byteSize() * n);
                var found = new ArrayList<MemorySegment>(n);
                for (var i = 0; i < n; i++) {
                    found.add(elements.getAtIndex(ADDRESS, i));
                }
                return found;
            } finally {
                ole32.taskMemFree(activates);
            }
        }
    }

    private static MemorySegment create(String function, MemorySegment address) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            HResult.check(function, (int) FD_MFCreateObject.invokeExact(address, out));
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw WindowsLibrary.rethrow(function, t);
        }
    }
}
