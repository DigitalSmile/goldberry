package dev.goldberry.media.ffi;

import java.lang.foreign.FunctionDescriptor;
import java.util.List;

import dev.goldberry.media.ffi.calls.AvCodecCalls;
import dev.goldberry.media.ffi.calls.AvFormatCalls;
import dev.goldberry.media.ffi.calls.AvUtilCalls;
import dev.goldberry.media.ffi.calls.SwResampleCalls;
import dev.goldberry.media.ffi.calls.SwScaleCalls;

/// Every foreign-call shape of FFmpeg's bindings, for the module's
/// `reachability-metadata.json` (ADR-0339), which `…media.nativeimage` writes.
///
/// `:natives`' `ForeignMetadata` does the same thing before asking: it
/// initialises every holder, so each `FD_…` constant has been through
/// [FfmpegDowncalls#link] and is recorded. A traced run would record only what
/// that run happened to call. This records every holder there is.
///
/// Public in a package the module does not export, so that the writer beside the
/// system decoders' shapes can read it (ADR-0172); nothing outside the module
/// sees it.
public final class FfmpegDescriptors {

    /// The `…Calls` records whose nested holders are the downcall surface.
    static final List<Class<?>> CALLS = List.of(
            AvUtilCalls.class, AvFormatCalls.class, AvCodecCalls.class, SwResampleCalls.class, SwScaleCalls.class);

    /// Every upcall shape: the two `AVIOContext` callbacks, and the hardware
    /// decoder's `get_format` (phase 5).
    public static final List<FunctionDescriptor> UPCALLS =
            List.of(AvioBridge.READ_PACKET, AvioBridge.SEEK, HardwareDecoder.GET_FORMAT);

    private FfmpegDescriptors() {}

    /// Every downcall descriptor, after initialising every holder.
    public static List<FunctionDescriptor> downcalls() {
        for (var calls : CALLS) {
            for (var component : calls.getRecordComponents()) {
                try {
                    Class.forName(component.getType().getName(), true, calls.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return FfmpegDowncalls.linked();
    }
}
