package io.github.digitalsmile.goldberry.media.ffi.calls;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.media.ffi.FfmpegDowncalls;
import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibrary;

/// The functions of `libswscale` the Engine calls. Phase 1 binds the version for
/// the start-up check; CPU present binds the scaler in phase 3.
///
/// See [FfmpegDowncalls] for why each handle is a `static final` constant.
public record SwScaleCalls(Version version) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static SwScaleCalls bind(SymbolLookup lookup) {
        return new SwScaleCalls(new Version(lookup));
    }

    /// `unsigned swscale_version(void)`
    public static final class Version {

        private static final MethodHandle FD_swscale_version = FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.SWSCALE, "swscale_version");
        }

        public int call() {
            try {
                return (int) FD_swscale_version.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("swscale_version", t);
            }
        }
    }
}
