package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;

/// The functions of libopus the Opus decoder calls: the plain decoder for mono
/// and stereo, the multistream one for a channel mapping, and `ctl` in the two
/// shapes that are used, since it is variadic.
public record OpusCalls(
        Strerror strerror,
        DecoderCreate decoderCreate,
        DecodeFloat decodeFloat,
        DecoderCtl decoderCtl,
        DecoderCtlInt decoderCtlInt,
        DecoderDestroy decoderDestroy,
        MultistreamDecoderCreate multistreamDecoderCreate,
        MultistreamDecodeFloat multistreamDecodeFloat,
        MultistreamDecoderCtl multistreamDecoderCtl,
        MultistreamDecoderCtlInt multistreamDecoderCtlInt,
        MultistreamDecoderDestroy multistreamDecoderDestroy) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static OpusCalls bind(SymbolLookup lookup) {
        return new OpusCalls(
                new Strerror(lookup),
                new DecoderCreate(lookup),
                new DecodeFloat(lookup),
                new DecoderCtl(lookup),
                new DecoderCtlInt(lookup),
                new DecoderDestroy(lookup),
                new MultistreamDecoderCreate(lookup),
                new MultistreamDecodeFloat(lookup),
                new MultistreamDecoderCtl(lookup),
                new MultistreamDecoderCtlInt(lookup),
                new MultistreamDecoderDestroy(lookup));
    }

    /// `const char *opus_strerror(int error)`
    public static final class Strerror {

        private static final MethodHandle FD_opus_strerror = Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        Strerror(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_strerror");
        }

        public MemorySegment call(int error) {
            try {
                return (MemorySegment) FD_opus_strerror.invokeExact(address, error);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_strerror", t);
            }
        }
    }

    /// `OpusDecoder *opus_decoder_create(opus_int32 Fs, int channels, int *error)`
    public static final class DecoderCreate {

        private static final MethodHandle FD_opus_decoder_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        DecoderCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_decoder_create");
        }

        public MemorySegment call(int sampleRate, int channels, MemorySegment errorOut) {
            try {
                return (MemorySegment) FD_opus_decoder_create.invokeExact(address, sampleRate, channels, errorOut);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_decoder_create", t);
            }
        }
    }

    /// `int opus_decode_float(OpusDecoder *st, const unsigned char *data, opus_int32 len, float
    /// *pcm, int frame_size, int decode_fec)`
    ///
    /// The samples per channel decoded, or a negative error.
    public static final class DecodeFloat {

        private static final MethodHandle FD_opus_decode_float = Downcalls.link(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        DecodeFloat(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_decode_float");
        }

        public int call(
                MemorySegment decoder,
                MemorySegment data,
                int length,
                MemorySegment pcm,
                int frameSize,
                int decodeFec) {
            try {
                return (int)
                        FD_opus_decode_float.invokeExact(address, decoder, data, length, pcm, frameSize, decodeFec);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_decode_float", t);
            }
        }
    }

    /// `int opus_decoder_ctl(OpusDecoder *st, int request, ...)`
    ///
    /// The shape with no argument: `OPUS_RESET_STATE`.
    public static final class DecoderCtl {

        private static final MethodHandle FD_opus_decoder_ctl =
                Downcalls.linkVariadic(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), 2);

        private final MemorySegment address;

        DecoderCtl(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_decoder_ctl");
        }

        public int call(MemorySegment decoder, int request) {
            try {
                return (int) FD_opus_decoder_ctl.invokeExact(address, decoder, request);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_decoder_ctl", t);
            }
        }
    }

    /// `int opus_decoder_ctl(OpusDecoder *st, int request, opus_int32 value)`
    ///
    /// The shape with one `opus_int32`: `OPUS_SET_GAIN`.
    public static final class DecoderCtlInt {

        private static final MethodHandle FD_opus_decoder_ctl =
                Downcalls.linkVariadic(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT), 2);

        private final MemorySegment address;

        DecoderCtlInt(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_decoder_ctl");
        }

        public int call(MemorySegment decoder, int request, int value) {
            try {
                return (int) FD_opus_decoder_ctl.invokeExact(address, decoder, request, value);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_decoder_ctl", t);
            }
        }
    }

    /// `void opus_decoder_destroy(OpusDecoder *st)`
    public static final class DecoderDestroy {

        private static final MethodHandle FD_opus_decoder_destroy = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DecoderDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_decoder_destroy");
        }

        public void call(MemorySegment decoder) {
            try {
                FD_opus_decoder_destroy.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_decoder_destroy", t);
            }
        }
    }

    /// `OpusMSDecoder *opus_multistream_decoder_create(opus_int32 Fs, int channels, int streams,
    /// int coupled_streams, const unsigned char *mapping, int *error)`
    public static final class MultistreamDecoderCreate {

        private static final MethodHandle FD_opus_multistream_decoder_create = Downcalls.link(
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        MultistreamDecoderCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_multistream_decoder_create");
        }

        public MemorySegment call(
                int sampleRate,
                int channels,
                int streams,
                int coupledStreams,
                MemorySegment mapping,
                MemorySegment errorOut) {
            try {
                return (MemorySegment) FD_opus_multistream_decoder_create.invokeExact(
                        address, sampleRate, channels, streams, coupledStreams, mapping, errorOut);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_multistream_decoder_create", t);
            }
        }
    }

    /// `int opus_multistream_decode_float(OpusMSDecoder *st, const unsigned char *data, opus_int32
    /// len, float *pcm, int frame_size, int decode_fec)`
    public static final class MultistreamDecodeFloat {

        private static final MethodHandle FD_opus_multistream_decode_float = Downcalls.link(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        MultistreamDecodeFloat(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_multistream_decode_float");
        }

        public int call(
                MemorySegment decoder,
                MemorySegment data,
                int length,
                MemorySegment pcm,
                int frameSize,
                int decodeFec) {
            try {
                return (int) FD_opus_multistream_decode_float.invokeExact(
                        address, decoder, data, length, pcm, frameSize, decodeFec);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_multistream_decode_float", t);
            }
        }
    }

    /// `int opus_multistream_decoder_ctl(OpusMSDecoder *st, int request, ...)`
    public static final class MultistreamDecoderCtl {

        private static final MethodHandle FD_opus_multistream_decoder_ctl =
                Downcalls.linkVariadic(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), 2);

        private final MemorySegment address;

        MultistreamDecoderCtl(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_multistream_decoder_ctl");
        }

        public int call(MemorySegment decoder, int request) {
            try {
                return (int) FD_opus_multistream_decoder_ctl.invokeExact(address, decoder, request);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_multistream_decoder_ctl", t);
            }
        }
    }

    /// `int opus_multistream_decoder_ctl(OpusMSDecoder *st, int request, opus_int32 value)`
    public static final class MultistreamDecoderCtlInt {

        private static final MethodHandle FD_opus_multistream_decoder_ctl =
                Downcalls.linkVariadic(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT), 2);

        private final MemorySegment address;

        MultistreamDecoderCtlInt(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_multistream_decoder_ctl");
        }

        public int call(MemorySegment decoder, int request, int value) {
            try {
                return (int) FD_opus_multistream_decoder_ctl.invokeExact(address, decoder, request, value);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_multistream_decoder_ctl", t);
            }
        }
    }

    /// `void opus_multistream_decoder_destroy(OpusMSDecoder *st)`
    public static final class MultistreamDecoderDestroy {

        private static final MethodHandle FD_opus_multistream_decoder_destroy =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        MultistreamDecoderDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "opus_multistream_decoder_destroy");
        }

        public void call(MemorySegment decoder) {
            try {
                FD_opus_multistream_decoder_destroy.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("opus_multistream_decoder_destroy", t);
            }
        }
    }
}
