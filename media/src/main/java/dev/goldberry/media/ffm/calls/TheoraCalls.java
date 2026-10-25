package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;

/// The decoder half of libtheora, which the Theora decoder calls: the three
/// header packets in, as `ogg_packet`s, then one frame at a time, and the picture
/// out as three planes the decoder keeps until its next frame.
public record TheoraCalls(
        VersionString versionString,
        InfoInit infoInit,
        InfoClear infoClear,
        CommentInit commentInit,
        CommentClear commentClear,
        DecodeHeaderin decodeHeaderin,
        DecodeAlloc decodeAlloc,
        SetupFree setupFree,
        DecodePacketin decodePacketin,
        DecodeYcbcrOut decodeYcbcrOut,
        DecodeFree decodeFree) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static TheoraCalls bind(SymbolLookup lookup) {
        return new TheoraCalls(
                new VersionString(lookup),
                new InfoInit(lookup),
                new InfoClear(lookup),
                new CommentInit(lookup),
                new CommentClear(lookup),
                new DecodeHeaderin(lookup),
                new DecodeAlloc(lookup),
                new SetupFree(lookup),
                new DecodePacketin(lookup),
                new DecodeYcbcrOut(lookup),
                new DecodeFree(lookup));
    }

    /// `const char *th_version_string(void)`
    public static final class VersionString {

        private static final MethodHandle FD_th_version_string = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        VersionString(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_version_string");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_th_version_string.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("th_version_string", t);
            }
        }
    }

    /// `void th_info_init(th_info *_info)`
    public static final class InfoInit {

        private static final MethodHandle FD_th_info_init = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        InfoInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_info_init");
        }

        public void call(MemorySegment info) {
            try {
                FD_th_info_init.invokeExact(address, info);
            } catch (Throwable t) {
                throw Downcalls.failure("th_info_init", t);
            }
        }
    }

    /// `void th_info_clear(th_info *_info)`
    public static final class InfoClear {

        private static final MethodHandle FD_th_info_clear = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        InfoClear(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_info_clear");
        }

        public void call(MemorySegment info) {
            try {
                FD_th_info_clear.invokeExact(address, info);
            } catch (Throwable t) {
                throw Downcalls.failure("th_info_clear", t);
            }
        }
    }

    /// `void th_comment_init(th_comment *_tc)`
    public static final class CommentInit {

        private static final MethodHandle FD_th_comment_init = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        CommentInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_comment_init");
        }

        public void call(MemorySegment comment) {
            try {
                FD_th_comment_init.invokeExact(address, comment);
            } catch (Throwable t) {
                throw Downcalls.failure("th_comment_init", t);
            }
        }
    }

    /// `void th_comment_clear(th_comment *_tc)`
    public static final class CommentClear {

        private static final MethodHandle FD_th_comment_clear = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        CommentClear(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_comment_clear");
        }

        public void call(MemorySegment comment) {
            try {
                FD_th_comment_clear.invokeExact(address, comment);
            } catch (Throwable t) {
                throw Downcalls.failure("th_comment_clear", t);
            }
        }
    }

    /// `int th_decode_headerin(th_info *_info, th_comment *_tc, th_setup_info **_setup, ogg_packet
    /// *_op)`
    ///
    /// A positive number when the packet was a header and was taken, 0 when it was the first frame,
    /// a negative `TH_E…` otherwise.
    public static final class DecodeHeaderin {

        private static final MethodHandle FD_th_decode_headerin =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DecodeHeaderin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_decode_headerin");
        }

        public int call(MemorySegment info, MemorySegment comment, MemorySegment setupOut, MemorySegment packet) {
            try {
                return (int) FD_th_decode_headerin.invokeExact(address, info, comment, setupOut, packet);
            } catch (Throwable t) {
                throw Downcalls.failure("th_decode_headerin", t);
            }
        }
    }

    /// `th_dec_ctx *th_decode_alloc(const th_info *_info, const th_setup_info *_setup)`
    ///
    /// Null when the headers do not describe a stream it can decode.
    public static final class DecodeAlloc {

        private static final MethodHandle FD_th_decode_alloc =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DecodeAlloc(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_decode_alloc");
        }

        public MemorySegment call(MemorySegment info, MemorySegment setup) {
            try {
                return (MemorySegment) FD_th_decode_alloc.invokeExact(address, info, setup);
            } catch (Throwable t) {
                throw Downcalls.failure("th_decode_alloc", t);
            }
        }
    }

    /// `void th_setup_free(th_setup_info *_setup)`
    public static final class SetupFree {

        private static final MethodHandle FD_th_setup_free = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        SetupFree(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_setup_free");
        }

        public void call(MemorySegment setup) {
            try {
                FD_th_setup_free.invokeExact(address, setup);
            } catch (Throwable t) {
                throw Downcalls.failure("th_setup_free", t);
            }
        }
    }

    /// `int th_decode_packetin(th_dec_ctx *_dec, const ogg_packet *_op, ogg_int64_t *_granpos)`
    ///
    /// 0 for a new picture, `TH_DUPFRAME` for a frame that repeats the last one, a negative `TH_E…`
    /// for a packet it refused.
    public static final class DecodePacketin {

        private static final MethodHandle FD_th_decode_packetin =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DecodePacketin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_decode_packetin");
        }

        public int call(MemorySegment decoder, MemorySegment packet, MemorySegment granuleOut) {
            try {
                return (int) FD_th_decode_packetin.invokeExact(address, decoder, packet, granuleOut);
            } catch (Throwable t) {
                throw Downcalls.failure("th_decode_packetin", t);
            }
        }
    }

    /// `int th_decode_ycbcr_out(th_dec_ctx *_dec, th_ycbcr_buffer _ycbcr)`
    ///
    /// Fills the three planes of the last picture: the whole coded frame, top row first, at a
    /// positive stride.
    public static final class DecodeYcbcrOut {

        private static final MethodHandle FD_th_decode_ycbcr_out =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DecodeYcbcrOut(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_decode_ycbcr_out");
        }

        public int call(MemorySegment decoder, MemorySegment buffer) {
            try {
                return (int) FD_th_decode_ycbcr_out.invokeExact(address, decoder, buffer);
            } catch (Throwable t) {
                throw Downcalls.failure("th_decode_ycbcr_out", t);
            }
        }
    }

    /// `void th_decode_free(th_dec_ctx *_dec)`
    public static final class DecodeFree {

        private static final MethodHandle FD_th_decode_free = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DecodeFree(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "th_decode_free");
        }

        public void call(MemorySegment decoder) {
            try {
                FD_th_decode_free.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("th_decode_free", t);
            }
        }
    }
}
