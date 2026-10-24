package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;
import dev.goldberry.media.ffm.NativeTypes;

/// The functions of libvpx the VP9 and VP8 decoder calls. `vpx_codec_flags_t`
/// and the decode deadline are C `long`s, 4 bytes on Windows and 8 elsewhere,
/// so those two handles take a Java `long` and are adapted to the target's.
public record VpxCalls(
        VersionStr versionStr,
        ErrToString errToString,
        ErrorText errorText,
        ErrorDetail errorDetail,
        Destroy destroy,
        DecInitVer decInitVer,
        Decode decode,
        GetFrame getFrame,
        Vp8Dx vp8Dx,
        Vp9Dx vp9Dx) {

    private static final java.lang.foreign.ValueLayout C_LONG = NativeTypes.C_LONG;

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static VpxCalls bind(SymbolLookup lookup) {
        return new VpxCalls(
                new VersionStr(lookup),
                new ErrToString(lookup),
                new ErrorText(lookup),
                new ErrorDetail(lookup),
                new Destroy(lookup),
                new DecInitVer(lookup),
                new Decode(lookup),
                new GetFrame(lookup),
                new Vp8Dx(lookup),
                new Vp9Dx(lookup));
    }

    /// `const char *vpx_codec_version_str(void)`
    public static final class VersionStr {

        private static final MethodHandle FD_vpx_codec_version_str = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        VersionStr(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_version_str");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_vpx_codec_version_str.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_version_str", t);
            }
        }
    }

    /// `const char *vpx_codec_err_to_string(vpx_codec_err_t err)`
    public static final class ErrToString {

        private static final MethodHandle FD_vpx_codec_err_to_string =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ErrToString(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_err_to_string");
        }

        public MemorySegment call(int error) {
            try {
                return (MemorySegment) FD_vpx_codec_err_to_string.invokeExact(address, error);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_err_to_string", t);
            }
        }
    }

    /// `const char *vpx_codec_error(const vpx_codec_ctx_t *ctx)`
    public static final class ErrorText {

        private static final MethodHandle FD_vpx_codec_error = Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ErrorText(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_error");
        }

        public MemorySegment call(MemorySegment context) {
            try {
                return (MemorySegment) FD_vpx_codec_error.invokeExact(address, context);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_error", t);
            }
        }
    }

    /// `const char *vpx_codec_error_detail(const vpx_codec_ctx_t *ctx)`
    ///
    /// Null when there is no detail.
    public static final class ErrorDetail {

        private static final MethodHandle FD_vpx_codec_error_detail =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ErrorDetail(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_error_detail");
        }

        public MemorySegment call(MemorySegment context) {
            try {
                return (MemorySegment) FD_vpx_codec_error_detail.invokeExact(address, context);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_error_detail", t);
            }
        }
    }

    /// `vpx_codec_err_t vpx_codec_destroy(vpx_codec_ctx_t *ctx)`
    public static final class Destroy {

        private static final MethodHandle FD_vpx_codec_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Destroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_destroy");
        }

        public int call(MemorySegment context) {
            try {
                return (int) FD_vpx_codec_destroy.invokeExact(address, context);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_destroy", t);
            }
        }
    }

    /// `vpx_codec_err_t vpx_codec_dec_init_ver(vpx_codec_ctx_t *ctx, vpx_codec_iface_t *iface,
    /// const vpx_codec_dec_cfg_t *cfg, vpx_codec_flags_t flags, int ver)`
    ///
    /// `ver` is `VPX_DECODER_ABI_VERSION`, which the layout file reports.
    public static final class DecInitVer {

        private static final MethodHandle FD_vpx_codec_dec_init_ver = Downcalls.cLongsAsLongs(
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, C_LONG, JAVA_INT)), 4);

        private final MemorySegment address;

        DecInitVer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_dec_init_ver");
        }

        public int call(MemorySegment context, MemorySegment iface, MemorySegment config, long flags, int version) {
            try {
                return (int) FD_vpx_codec_dec_init_ver.invokeExact(address, context, iface, config, flags, version);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_dec_init_ver", t);
            }
        }
    }

    /// `vpx_codec_err_t vpx_codec_decode(vpx_codec_ctx_t *ctx, const uint8_t *data, unsigned int
    /// data_sz, void *user_priv, long deadline)`
    public static final class Decode {

        private static final MethodHandle FD_vpx_codec_decode = Downcalls.cLongsAsLongs(
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, C_LONG)), 5);

        private final MemorySegment address;

        Decode(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_decode");
        }

        public int call(MemorySegment context, MemorySegment data, int size, MemorySegment userPrivate, long deadline) {
            try {
                return (int) FD_vpx_codec_decode.invokeExact(address, context, data, size, userPrivate, deadline);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_decode", t);
            }
        }
    }

    /// `vpx_image_t *vpx_codec_get_frame(vpx_codec_ctx_t *ctx, vpx_codec_iter_t *iter)`
    ///
    /// Null when the last decode has no more pictures.
    public static final class GetFrame {

        private static final MethodHandle FD_vpx_codec_get_frame =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetFrame(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_get_frame");
        }

        public MemorySegment call(MemorySegment context, MemorySegment iterator) {
            try {
                return (MemorySegment) FD_vpx_codec_get_frame.invokeExact(address, context, iterator);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_get_frame", t);
            }
        }
    }

    /// `vpx_codec_iface_t *vpx_codec_vp8_dx(void)`
    public static final class Vp8Dx {

        private static final MethodHandle FD_vpx_codec_vp8_dx = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        Vp8Dx(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_vp8_dx");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_vpx_codec_vp8_dx.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_vp8_dx", t);
            }
        }
    }

    /// `vpx_codec_iface_t *vpx_codec_vp9_dx(void)`
    public static final class Vp9Dx {

        private static final MethodHandle FD_vpx_codec_vp9_dx = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        Vp9Dx(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vpx_codec_vp9_dx");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_vpx_codec_vp9_dx.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("vpx_codec_vp9_dx", t);
            }
        }
    }
}
