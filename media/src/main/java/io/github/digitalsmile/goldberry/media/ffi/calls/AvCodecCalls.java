package io.github.digitalsmile.goldberry.media.ffi.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.media.ffi.FfmpegDowncalls;
import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibrary;

/// The functions of `libavcodec` the Engine calls: naming codecs, listing
/// decoders, and the send/receive decode loop.
///
/// See [FfmpegDowncalls] for why each handle is a `static final` constant.
public record AvCodecCalls(
        Version version,
        GetName getName,
        FindDecoder findDecoder,
        CodecIterate codecIterate,
        IsDecoder isDecoder,
        AllocContext3 allocContext3,
        ParametersToContext parametersToContext,
        Open2 open2,
        SendPacket sendPacket,
        ReceiveFrame receiveFrame,
        FlushBuffers flushBuffers,
        FreeContext freeContext,
        PacketAlloc packetAlloc,
        PacketFree packetFree) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static AvCodecCalls bind(SymbolLookup lookup) {
        return new AvCodecCalls(
                new Version(lookup),
                new GetName(lookup),
                new FindDecoder(lookup),
                new CodecIterate(lookup),
                new IsDecoder(lookup),
                new AllocContext3(lookup),
                new ParametersToContext(lookup),
                new Open2(lookup),
                new SendPacket(lookup),
                new ReceiveFrame(lookup),
                new FlushBuffers(lookup),
                new FreeContext(lookup),
                new PacketAlloc(lookup),
                new PacketFree(lookup));
    }

    /// `unsigned avcodec_version(void)`
    public static final class Version {

        private static final MethodHandle FD_avcodec_version = FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_version");
        }

        public int call() {
            try {
                return (int) FD_avcodec_version.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_version", t);
            }
        }
    }

    /// `const char *avcodec_get_name(enum AVCodecID id)`
    ///
    /// The name `CodecId` maps from. It is answered for every codec FFmpeg knows,
    /// **whether or not this build decodes it**: the descriptor table is compiled
    /// in whole, and only the decoders are left out. That is how an MP4 with H.264
    /// in it is reported as `h264` by a library that cannot decode it.
    public static final class GetName {

        private static final MethodHandle FD_avcodec_get_name =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetName(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_get_name");
        }

        public MemorySegment call(int codecId) {
            try {
                return (MemorySegment) FD_avcodec_get_name.invokeExact(address, codecId);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_get_name", t);
            }
        }
    }

    /// `const AVCodec *avcodec_find_decoder(enum AVCodecID id)`
    ///
    /// The preferred decoder this build has for a codec, or null: the built-in
    /// provider's whole answer to "can you decode this".
    public static final class FindDecoder {

        private static final MethodHandle FD_avcodec_find_decoder =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        FindDecoder(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_find_decoder");
        }

        public MemorySegment call(int codecId) {
            try {
                return (MemorySegment) FD_avcodec_find_decoder.invokeExact(address, codecId);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_find_decoder", t);
            }
        }
    }

    /// `const AVCodec *av_codec_iterate(void **opaque)`
    ///
    /// Every codec compiled in, one per call, then null.
    public static final class CodecIterate {

        private static final MethodHandle FD_av_codec_iterate =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        CodecIterate(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "av_codec_iterate");
        }

        public MemorySegment call(MemorySegment opaque) {
            try {
                return (MemorySegment) FD_av_codec_iterate.invokeExact(address, opaque);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_codec_iterate", t);
            }
        }
    }

    /// `int av_codec_is_decoder(const AVCodec *codec)`
    public static final class IsDecoder {

        private static final MethodHandle FD_av_codec_is_decoder =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        IsDecoder(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "av_codec_is_decoder");
        }

        public int call(MemorySegment codec) {
            try {
                return (int) FD_av_codec_is_decoder.invokeExact(address, codec);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_codec_is_decoder", t);
            }
        }
    }

    /// `AVCodecContext *avcodec_alloc_context3(const AVCodec *codec)`
    public static final class AllocContext3 {

        private static final MethodHandle FD_avcodec_alloc_context3 =
                FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        AllocContext3(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_alloc_context3");
        }

        public MemorySegment call(MemorySegment codec) {
            try {
                return (MemorySegment) FD_avcodec_alloc_context3.invokeExact(address, codec);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_alloc_context3", t);
            }
        }
    }

    /// `int avcodec_parameters_to_context(AVCodecContext *codec, const AVCodecParameters *par)`
    ///
    /// Copies everything the container knows (extradata, rate, layout, block
    /// align) into the decoder before it opens.
    public static final class ParametersToContext {

        private static final MethodHandle FD_avcodec_parameters_to_context =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ParametersToContext(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_parameters_to_context");
        }

        public int call(MemorySegment context, MemorySegment parameters) {
            try {
                return (int) FD_avcodec_parameters_to_context.invokeExact(address, context, parameters);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_parameters_to_context", t);
            }
        }
    }

    /// `int avcodec_open2(AVCodecContext *avctx, const AVCodec *codec, AVDictionary **options)`
    public static final class Open2 {

        private static final MethodHandle FD_avcodec_open2 =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Open2(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_open2");
        }

        public int call(MemorySegment context, MemorySegment codec, MemorySegment options) {
            try {
                return (int) FD_avcodec_open2.invokeExact(address, context, codec, options);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_open2", t);
            }
        }
    }

    /// `int avcodec_send_packet(AVCodecContext *avctx, const AVPacket *avpkt)`
    ///
    /// A null packet starts draining. `AVERROR(EAGAIN)` means receive first. A
    /// packet with no `buf` is copied, so the caller keeps its memory.
    public static final class SendPacket {

        private static final MethodHandle FD_avcodec_send_packet =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SendPacket(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_send_packet");
        }

        public int call(MemorySegment context, MemorySegment packet) {
            try {
                return (int) FD_avcodec_send_packet.invokeExact(address, context, packet);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_send_packet", t);
            }
        }
    }

    /// `int avcodec_receive_frame(AVCodecContext *avctx, AVFrame *frame)`
    ///
    /// `AVERROR(EAGAIN)`: send more. `AVERROR_EOF`: drained.
    public static final class ReceiveFrame {

        private static final MethodHandle FD_avcodec_receive_frame =
                FfmpegDowncalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReceiveFrame(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_receive_frame");
        }

        public int call(MemorySegment context, MemorySegment frame) {
            try {
                return (int) FD_avcodec_receive_frame.invokeExact(address, context, frame);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_receive_frame", t);
            }
        }
    }

    /// `void avcodec_flush_buffers(AVCodecContext *avctx)`
    ///
    /// After a seek. It also resets a drained decoder.
    public static final class FlushBuffers {

        private static final MethodHandle FD_avcodec_flush_buffers =
                FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FlushBuffers(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_flush_buffers");
        }

        public void call(MemorySegment context) {
            try {
                FD_avcodec_flush_buffers.invokeExact(address, context);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_flush_buffers", t);
            }
        }
    }

    /// `void avcodec_free_context(AVCodecContext **avctx)`
    public static final class FreeContext {

        private static final MethodHandle FD_avcodec_free_context =
                FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        FreeContext(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "avcodec_free_context");
        }

        public void call(MemorySegment contextPointer) {
            try {
                FD_avcodec_free_context.invokeExact(address, contextPointer);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("avcodec_free_context", t);
            }
        }
    }

    /// `AVPacket *av_packet_alloc(void)`
    public static final class PacketAlloc {

        private static final MethodHandle FD_av_packet_alloc = FfmpegDowncalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        PacketAlloc(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "av_packet_alloc");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_av_packet_alloc.invokeExact(address);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_packet_alloc", t);
            }
        }
    }

    /// `void av_packet_free(AVPacket **pkt)`
    ///
    /// Unreferences and frees, and sets `*pkt` to null.
    public static final class PacketFree {

        private static final MethodHandle FD_av_packet_free = FfmpegDowncalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        PacketFree(SymbolLookup lookup) {
            this.address = FfmpegDowncalls.symbol(lookup, FfmpegLibrary.AVCODEC, "av_packet_free");
        }

        public void call(MemorySegment packetPointer) {
            try {
                FD_av_packet_free.invokeExact(address, packetPointer);
            } catch (Throwable t) {
                throw FfmpegDowncalls.failure("av_packet_free", t);
            }
        }
    }
}
