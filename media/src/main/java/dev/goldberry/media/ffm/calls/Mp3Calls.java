package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;

/// The two functions of minimp3, instantiated in `goldberry_media.c` with float
/// output: one frame of MP3 in, up to 1152 samples a channel out.
public record Mp3Calls(Init init, DecodeFrame decodeFrame) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static Mp3Calls bind(SymbolLookup lookup) {
        return new Mp3Calls(new Init(lookup), new DecodeFrame(lookup));
    }

    /// `void mp3dec_init(mp3dec_t *dec)`
    public static final class Init {

        private static final MethodHandle FD_mp3dec_init = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Init(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "mp3dec_init");
        }

        public void call(MemorySegment decoder) {
            try {
                FD_mp3dec_init.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("mp3dec_init", t);
            }
        }
    }

    /// `int mp3dec_decode_frame(mp3dec_t *dec, const uint8_t *mp3, int mp3_bytes, mp3d_sample_t
    /// *pcm, mp3dec_frame_info_t *info)`
    ///
    /// The samples per channel decoded: 0 with `frame_bytes` > 0 means a frame was skipped, 0 with
    /// `frame_bytes` 0 means more data is needed.
    public static final class DecodeFrame {

        private static final MethodHandle FD_mp3dec_decode_frame =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DecodeFrame(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "mp3dec_decode_frame");
        }

        public int call(MemorySegment decoder, MemorySegment data, int length, MemorySegment pcm, MemorySegment info) {
            try {
                return (int) FD_mp3dec_decode_frame.invokeExact(address, decoder, data, length, pcm, info);
            } catch (Throwable t) {
                throw Downcalls.failure("mp3dec_decode_frame", t);
            }
        }
    }
}
