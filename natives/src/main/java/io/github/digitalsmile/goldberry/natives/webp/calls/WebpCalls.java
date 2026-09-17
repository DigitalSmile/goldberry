package io.github.digitalsmile.goldberry.natives.webp.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// The functions `libgoldberry` exports for WebP — `docs/gaps.md` G35a,
/// [ADR-0329], and the encoder and animation reader [ADR-0385] added.
///
/// libwebp's own, bound directly with no C glue in between, which is §3.1's rule
/// and what the whole export list exists for.
///
/// **`…Internal` is not a private symbol.** `WebPAnimDecoderOptionsInit` and
/// `WebPAnimDecoderNew` are static inlines in `demux.h` that forward to these
/// with the library's ABI version; a binding cannot call an inline, so it passes
/// the version itself and libwebp checks it — which is exactly what the inline
/// does and the only way to reach the function at all.
///
/// See [Downcalls] for why each handle is a `static final` constant and why these
/// live in a package of their own.
public record WebpCalls(
        GetInfo getInfo,
        DecodeRgba decodeRgba,
        EncodeRgba encodeRgba,
        EncodeLosslessRgba encodeLosslessRgba,
        AnimOptionsInit animOptionsInit,
        AnimNew animNew,
        AnimGetInfo animGetInfo,
        AnimGetNext animGetNext,
        AnimHasMoreFrames animHasMoreFrames,
        AnimDelete animDelete,
        Free free) {

    /// The ABI version `demux.h` compiles its inlines against —
    /// `WEBP_DEMUX_ABI_VERSION`, `MAJOR(8b) + MINOR(8b)`.
    ///
    /// Passed on every call the two `…Internal` entry points take, and checked by
    /// the library: a libwebp built from a different ABI refuses rather than
    /// reading a struct laid out differently. Bumped when the pinned revision
    /// bumps it, which the layout probe is what notices.
    public static final int DEMUX_ABI_VERSION = 0x0107;

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static WebpCalls bind(SymbolLookup lookup) {
        return new WebpCalls(
                new GetInfo(lookup),
                new DecodeRgba(lookup),
                new EncodeRgba(lookup),
                new EncodeLosslessRgba(lookup),
                new AnimOptionsInit(lookup),
                new AnimNew(lookup),
                new AnimGetInfo(lookup),
                new AnimGetNext(lookup),
                new AnimHasMoreFrames(lookup),
                new AnimDelete(lookup),
                new Free(lookup));
    }

    /// Encodes RGBA pixels as a lossy WebP.
    ///
    /// `size_t WebPEncodeRGBA(const uint8_t* rgba, int w, int h, int stride,`
    /// `float quality, uint8_t** out)`
    ///
    /// The buffer is libwebp's and goes back through [Free], like a decode's.
    public static final class EncodeRgba {

        private static final MethodHandle FD_WebPEncodeRGBA = Downcalls.link(FunctionDescriptor.of(
                JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, java.lang.foreign.ValueLayout.JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        EncodeRgba(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPEncodeRGBA");
        }

        /// Calls `WebPEncodeRGBA`.
        ///
        /// @return the encoded size in bytes, or 0 when the encode failed
        public long call(MemorySegment rgba, int width, int height, int stride, float quality, MemorySegment out) {
            try {
                return (long) FD_WebPEncodeRGBA.invokeExact(address, rgba, width, height, stride, quality, out);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPEncodeRGBA", t);
            }
        }
    }

    /// The same, losslessly — no quality, because there is nothing to trade.
    ///
    /// `size_t WebPEncodeLosslessRGBA(const uint8_t* rgba, int w, int h,`
    /// `int stride, uint8_t** out)`
    public static final class EncodeLosslessRgba {

        private static final MethodHandle FD_WebPEncodeLosslessRGBA =
                Downcalls.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        EncodeLosslessRgba(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPEncodeLosslessRGBA");
        }

        /// Calls `WebPEncodeLosslessRGBA`.
        public long call(MemorySegment rgba, int width, int height, int stride, MemorySegment out) {
            try {
                return (long) FD_WebPEncodeLosslessRGBA.invokeExact(address, rgba, width, height, stride, out);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPEncodeLosslessRGBA", t);
            }
        }
    }

    /// Fills a `WebPAnimDecoderOptions` with its defaults.
    ///
    /// `int WebPAnimDecoderOptionsInitInternal(WebPAnimDecoderOptions*, int abi)`
    public static final class AnimOptionsInit {

        private static final MethodHandle FD_WebPAnimDecoderOptionsInitInternal =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        AnimOptionsInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPAnimDecoderOptionsInitInternal");
        }

        /// @return non-zero on success; zero means the ABI version disagreed
        public int call(MemorySegment options, int abiVersion) {
            try {
                return (int) FD_WebPAnimDecoderOptionsInitInternal.invokeExact(address, options, abiVersion);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPAnimDecoderOptionsInitInternal", t);
            }
        }
    }

    /// Opens an animation over bytes the caller keeps alive.
    ///
    /// `WebPAnimDecoder* WebPAnimDecoderNewInternal(const WebPData*,`
    /// `const WebPAnimDecoderOptions*, int abi)`
    ///
    /// **The bytes must outlive the decoder**, which upstream says in as many
    /// words: the `WebPData` points at them and nothing is copied.
    public static final class AnimNew {

        private static final MethodHandle FD_WebPAnimDecoderNewInternal =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        AnimNew(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPAnimDecoderNewInternal");
        }

        /// @return the decoder, or [MemorySegment#NULL] when the bytes are not an
        ///         animation this library can read
        public MemorySegment call(MemorySegment data, MemorySegment options, int abiVersion) {
            try {
                return (MemorySegment) FD_WebPAnimDecoderNewInternal.invokeExact(address, data, options, abiVersion);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPAnimDecoderNewInternal", t);
            }
        }
    }

    /// The canvas size, the loop count and the frame count.
    ///
    /// `int WebPAnimDecoderGetInfo(const WebPAnimDecoder*, WebPAnimInfo*)`
    public static final class AnimGetInfo {

        private static final MethodHandle FD_WebPAnimDecoderGetInfo =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        AnimGetInfo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPAnimDecoderGetInfo");
        }

        public int call(MemorySegment decoder, MemorySegment info) {
            try {
                return (int) FD_WebPAnimDecoderGetInfo.invokeExact(address, decoder, info);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPAnimDecoderGetInfo", t);
            }
        }
    }

    /// The next frame, as a **fully composited canvas** and a timestamp.
    ///
    /// `int WebPAnimDecoderGetNext(WebPAnimDecoder*, uint8_t** buf, int* timestamp)`
    ///
    /// The disposal and blending model animated GIF needed in Java (ADR-0382) is
    /// upstream's here. The buffer belongs to the decoder and is valid only until
    /// the next call, which is why the caller copies before asking again.
    public static final class AnimGetNext {

        private static final MethodHandle FD_WebPAnimDecoderGetNext =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        AnimGetNext(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPAnimDecoderGetNext");
        }

        public int call(MemorySegment decoder, MemorySegment buffer, MemorySegment timestamp) {
            try {
                return (int) FD_WebPAnimDecoderGetNext.invokeExact(address, decoder, buffer, timestamp);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPAnimDecoderGetNext", t);
            }
        }
    }

    /// Whether there is another frame.
    ///
    /// `int WebPAnimDecoderHasMoreFrames(const WebPAnimDecoder*)`
    public static final class AnimHasMoreFrames {

        private static final MethodHandle FD_WebPAnimDecoderHasMoreFrames =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        AnimHasMoreFrames(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPAnimDecoderHasMoreFrames");
        }

        public int call(MemorySegment decoder) {
            try {
                return (int) FD_WebPAnimDecoderHasMoreFrames.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPAnimDecoderHasMoreFrames", t);
            }
        }
    }

    /// Frees a decoder and every buffer it lent out.
    ///
    /// `void WebPAnimDecoderDelete(WebPAnimDecoder*)`
    public static final class AnimDelete {

        private static final MethodHandle FD_WebPAnimDecoderDelete = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        AnimDelete(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "WebPAnimDecoderDelete");
        }

        public void call(MemorySegment decoder) {
            try {
                FD_WebPAnimDecoderDelete.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("WebPAnimDecoderDelete", t);
            }
        }
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
