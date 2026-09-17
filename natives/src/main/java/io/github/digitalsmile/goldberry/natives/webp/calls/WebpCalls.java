package io.github.digitalsmile.goldberry.natives.webp.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// The three functions `libgoldberry` exports for WebP — `docs/gaps.md` G35a,
/// [ADR-0329].
///
/// libwebp's own, bound directly with no C glue in between, which is §3.1's rule
/// and what the whole export list exists for. Only the decoder half of libwebp is
/// linked: the encoder, the muxer and the animation demuxer are switched off in
/// the superbuild, because Goldberry reads one still frame and writes PNG.
///
/// See [Downcalls] for why each handle is a `static final` constant and why these
/// live in a package of their own.
public record WebpCalls(GetInfo getInfo, DecodeRgba decodeRgba, Free free) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static WebpCalls bind(SymbolLookup lookup) {
        return new WebpCalls(new GetInfo(lookup), new DecodeRgba(lookup), new Free(lookup));
    }

    /// Reads the size out of a WebP header **without decoding it**.
    ///
    /// `int WebPGetInfo(const uint8_t* data, size_t data_size, int* width, int* height)`
    ///
    /// What makes it worth binding beside the decoder: it is also the cheapest
    /// honest answer to "are these bytes a WebP at all", which is what routes a
    /// decode. `size_t` is the pointer-width scalar on every target here.
    public static final class GetInfo {

        private static final MethodHandle FD_WebPGetInfo =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetInfo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPGetInfo");
        }

        /// Calls `WebPGetInfo`.
        ///
        /// @return non-zero when the bytes are a WebP whose header parsed
        public int call(MemorySegment data, long size, MemorySegment width, MemorySegment height) {
            try {
                return (int) FD_WebPGetInfo.invokeExact(address, data, size, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPGetInfo", t);
            }
        }
    }

    /// Decodes one still frame into **non**-premultiplied RGBA, top-down and
    /// tightly packed.
    ///
    /// `uint8_t* WebPDecodeRGBA(const uint8_t* data, size_t data_size, int* width, int* height)`
    ///
    /// The buffer is libwebp's and must go back through [Free]. This is the one
    /// place in this module where an upstream allocates the pixels — the same
    /// exception the image decoder makes, and for the same reason: only the
    /// decoder knows how big the image is.
    public static final class DecodeRgba {

        private static final MethodHandle FD_WebPDecodeRGBA =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DecodeRgba(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPDecodeRGBA");
        }

        /// Calls `WebPDecodeRGBA`.
        ///
        /// @return the pixels, or [MemorySegment#NULL] when the bytes are not a WebP
        ///         this library can decode
        public MemorySegment call(MemorySegment data, long size, MemorySegment width, MemorySegment height) {
            try {
                return (MemorySegment) FD_WebPDecodeRGBA.invokeExact(address, data, size, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPDecodeRGBA", t);
            }
        }
    }

    /// Gives a decoded buffer back.
    ///
    /// `void WebPFree(void* ptr)`
    ///
    /// **Not `free()`.** libwebp may be built against a different allocator than
    /// the one this process's `free` uses, and upstream says so in as many words;
    /// a mismatched pair is a heap corruption rather than an error.
    public static final class Free {

        private static final MethodHandle FD_WebPFree = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Free(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPFree");
        }

        public void call(MemorySegment pixels) {
            try {
                FD_WebPFree.invokeExact(address, pixels);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPFree", t);
            }
        }
    }
}
