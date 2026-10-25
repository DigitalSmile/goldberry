package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;

/// The three functions of libgoldberry-media's own (`goldberry_media.c`): the ABI
/// number the loader checks first, the upstream versions, and CPU present's tone
/// mapping.
public record MediaCalls(Abi abi, Versions versions, ToneMap toneMap) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static MediaCalls bind(SymbolLookup lookup) {
        return new MediaCalls(new Abi(lookup), new Versions(lookup), new ToneMap(lookup));
    }

    /// `int goldberry_media_abi(void)`
    public static final class Abi {

        private static final MethodHandle FD_goldberry_media_abi = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Abi(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_media_abi");
        }

        public int call() {
            try {
                return (int) FD_goldberry_media_abi.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_media_abi", t);
            }
        }
    }

    /// `const char *goldberry_media_versions(void)`
    public static final class Versions {

        private static final MethodHandle FD_goldberry_media_versions = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        Versions(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_media_versions");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_goldberry_media_versions.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_media_versions", t);
            }
        }
    }

    /// `void goldberry_media_tone_map(const uint8_t *plane0, int stride0, const uint8_t *plane1,
    /// int stride1, const uint8_t *plane2, int stride2, int format, uint8_t *dst, int dst_stride,
    /// int width, int height, int flags, const float *tables)`
    ///
    /// Converts an opaque picture of planes to SDR BT.709 BGRA, mapping its HDR or wide gamut
    /// through the tables `ToneCurve` builds. Strides in bytes.
    public static final class ToneMap {

        private static final MethodHandle FD_goldberry_media_tone_map = Downcalls.link(FunctionDescriptor.ofVoid(
                ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT,
                JAVA_INT, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ToneMap(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_media_tone_map");
        }

        public void call(
                MemorySegment plane0,
                int stride0,
                MemorySegment plane1,
                int stride1,
                MemorySegment plane2,
                int stride2,
                int format,
                MemorySegment dst,
                int dstStride,
                int width,
                int height,
                int flags,
                MemorySegment tables) {
            try {
                FD_goldberry_media_tone_map.invokeExact(
                        address, plane0, stride0, plane1, stride1, plane2, stride2, format, dst, dstStride, width,
                        height, flags, tables);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_media_tone_map", t);
            }
        }
    }
}
