package dev.goldberry.media.picture;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.ffm.MediaLibrary;
import dev.goldberry.media.ffm.MediaNatives;
import dev.goldberry.media.ffm.calls.YuvCalls;

/// Converts pictures with libyuv: CPU present's one pass from a decoded
/// [VideoFrame] to the toolkit's premultiplied BGRA.
///
/// **The same size in and out.** Nothing here scales: a picture is converted at
/// the size it was decoded at, and the widget scales it at the blit, where the
/// size on screen is known and no pixels are kept.
///
/// **The same bytes on every machine.** libyuv's conversions are integer
/// arithmetic with one result whichever of its SIMD paths runs, so a golden of a
/// decoded frame holds on x64 and on ARM, as the software decoders already
/// guarantee for their half. There is one speed: the `exact` flag the swscale
/// converter took is kept so that the switch is a rename, and changes nothing.
///
/// The picture's colour is honoured: the matrix and range it was tagged with
/// pick one of libyuv's six constant tables (BT.601, BT.709 and BT.2020, each in
/// limited and full range), and RGB is always full range.
///
/// **Chroma is interpolated as GPU present interpolates it**: bilinearly, each
/// chroma sample at the centre of its 2×2 block of luma (libyuv's
/// `kFilterBilinear`, weights 9:3:3:1, edges repeated), which is what a GPU
/// sampler does with the planes. The two present paths then draw the same
/// picture, to a level of rounding. NV12 has no filtered conversion in libyuv,
/// so its chroma is split into two planes first and converted as I420.
///
/// **Alpha is premultiplied here.** A picture with alpha
/// ([dev.goldberry.media.codec.PixelFormat#I420A]) is converted with libyuv's attenuation on the way out,
/// `(c × a + 128) >> 8` a channel, so the toolkit blits it as it blits its own
/// premultiplied images. A picture without alpha is opaque, so premultiplied and
/// straight are the same bytes.
///
/// Nothing is kept between pictures: libyuv has no context to build, so a
/// converter is the library and six addresses, and may be used from any thread,
/// by one at a time or several. An NV12 picture borrows two chroma planes for
/// the length of its conversion.
public final class YuvConverter implements AutoCloseable {

    /// libyuv's `attenuate` argument: 1 premultiplies the colour by the alpha.
    private static final int ATTENUATE = 1;

    private final YuvCalls yuv;
    private final int bilinear;
    private final MemorySegment bt601Limited;
    private final MemorySegment bt601Full;
    private final MemorySegment bt709Limited;
    private final MemorySegment bt709Full;
    private final MemorySegment bt2020Limited;
    private final MemorySegment bt2020Full;
    private volatile boolean closed;

    /// A converter over the process's `libgoldberry-media`.
    ///
    /// @throws MediaException holding [MediaError.NativesUnavailable] when the
    ///                        library cannot be loaded
    public YuvConverter() {
        this(MediaNatives.get());
    }

    /// A converter over the process's `libgoldberry-media`. `exact` is kept from
    /// the swscale converter, whose fast mode differed from its exact one by a
    /// level or two; libyuv has one speed and one answer, so it changes nothing.
    public YuvConverter(boolean exact) {
        this(MediaNatives.get(), exact);
    }

    /// A converter over `library`'s libyuv. Not public: the library is this
    /// module's own, and this package is exported.
    YuvConverter(MediaLibrary library) {
        this(library, true);
    }

    /// A converter over `library`'s libyuv. `exact` is kept from the swscale
    /// converter and changes nothing: see [#YuvConverter(boolean)].
    YuvConverter(MediaLibrary library, boolean exact) {
        Objects.requireNonNull(library, "library");
        this.yuv = library.yuv();
        this.bilinear = library.constants().yuv().filterBilinear();
        this.bt601Limited = library.symbol("kYuvI601Constants");
        this.bt601Full = library.symbol("kYuvJPEGConstants");
        this.bt709Limited = library.symbol("kYuvH709Constants");
        this.bt709Full = library.symbol("kYuvF709Constants");
        this.bt2020Limited = library.symbol("kYuv2020Constants");
        this.bt2020Full = library.symbol("kYuvV2020Constants");
    }

    /// Converts `frame` to premultiplied BGRA at its own size. A picture without
    /// alpha is opaque, so premultiplied and straight are the same bytes; one
    /// with alpha is premultiplied as it is converted.
    ///
    /// @param target       at least `stride × height` bytes
    /// @param targetStride the bytes from one row of `target` to the next; at
    ///                     least `4 × width`
    /// @throws IllegalArgumentException when `target` or `targetStride` is too
    ///                                  small for the picture, or a 16-bit plane's
    ///                                  stride is odd
    /// @throws IllegalStateException    after [#close()]
    /// @throws MediaException           [MediaError.InvalidData] when libyuv
    ///                                  refuses the conversion
    public void toBgra(VideoFrame frame, MemorySegment target, int targetStride) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(target, "target");
        if (targetStride < frame.width() * 4) {
            throw new IllegalArgumentException(
                    "a stride of " + targetStride + " cannot hold " + frame.width() + " BGRA pixels");
        }
        if (target.byteSize() < (long) targetStride * frame.height()) {
            throw new IllegalArgumentException("a target of " + target.byteSize() + " bytes cannot hold a "
                    + frame.width() + "×" + frame.height() + " picture at stride " + targetStride);
        }
        if (closed) {
            throw new IllegalStateException("converter closed");
        }
        var matrix = matrix(frame);
        var planes = frame.planes();
        var width = frame.width();
        var height = frame.height();
        var result = switch (frame.format()) {
            case I420 ->
                yuv.i420ToArgbMatrixFilter()
                        .call(
                                planes.get(0),
                                stride(frame, 0),
                                planes.get(1),
                                stride(frame, 1),
                                planes.get(2),
                                stride(frame, 2),
                                target,
                                targetStride,
                                matrix,
                                width,
                                height,
                                bilinear);
            case NV12 -> nv12(frame, target, targetStride, matrix);
            case I010 ->
                yuv.i010ToArgbMatrixFilter()
                        .call(
                                planes.get(0),
                                stride(frame, 0),
                                planes.get(1),
                                stride(frame, 1),
                                planes.get(2),
                                stride(frame, 2),
                                target,
                                targetStride,
                                matrix,
                                width,
                                height,
                                bilinear);
            case P010 ->
                yuv.p010ToArgbMatrixFilter()
                        .call(
                                planes.get(0),
                                stride(frame, 0),
                                planes.get(1),
                                stride(frame, 1),
                                target,
                                targetStride,
                                matrix,
                                width,
                                height,
                                bilinear);
            case I420A ->
                yuv.i420AlphaToArgbMatrixFilter()
                        .call(
                                planes.get(0),
                                stride(frame, 0),
                                planes.get(1),
                                stride(frame, 1),
                                planes.get(2),
                                stride(frame, 2),
                                planes.get(3),
                                stride(frame, 3),
                                target,
                                targetStride,
                                matrix,
                                width,
                                height,
                                ATTENUATE,
                                bilinear);
        };
        if (result != 0) {
            throw new MediaException(new MediaError.InvalidData(
                    "libyuv refused a " + width + "×" + height + " " + frame.format() + " picture (" + result + ")"));
        }
    }

    /// Marks the converter closed. Idempotent. Nothing native is held, so
    /// nothing is freed; a closed converter refuses to convert, as the swscale
    /// one did, so that a picture is never converted through something its owner
    /// gave back.
    @Override
    public void close() {
        closed = true;
    }

    /// An NV12 picture: its interleaved chroma split into two planes, then
    /// converted as I420, which has the filtered conversion NV12 lacks.
    private int nv12(VideoFrame frame, MemorySegment target, int targetStride, MemorySegment matrix) {
        var planes = frame.planes();
        var chromaWidth = (frame.width() + 1) / 2;
        var chromaHeight = (frame.height() + 1) / 2;
        var plane = (long) chromaWidth * chromaHeight;
        try (var arena = Arena.ofConfined()) {
            var u = arena.allocate(plane);
            var v = arena.allocate(plane);
            yuv.splitUvPlane()
                    .call(planes.get(1), stride(frame, 1), u, chromaWidth, v, chromaWidth, chromaWidth, chromaHeight);
            return yuv.i420ToArgbMatrixFilter()
                    .call(
                            planes.get(0),
                            stride(frame, 0),
                            u,
                            chromaWidth,
                            v,
                            chromaWidth,
                            target,
                            targetStride,
                            matrix,
                            frame.width(),
                            frame.height(),
                            bilinear);
        }
    }

    /// The constant table for the matrix and range `frame` was tagged with.
    private MemorySegment matrix(VideoFrame frame) {
        return switch (frame.matrix()) {
            case BT601 -> frame.fullRange() ? bt601Full : bt601Limited;
            case BT709 -> frame.fullRange() ? bt709Full : bt709Limited;
            case BT2020 -> frame.fullRange() ? bt2020Full : bt2020Limited;
        };
    }

    /// The stride of `plane` as libyuv counts it: bytes for an 8-bit plane, and
    /// 16-bit samples for a 16-bit one, since libyuv steps a `uint16_t*` by it.
    private static int stride(VideoFrame frame, int plane) {
        var stride = frame.strides().get(plane);
        if (frame.format().bytesPerSample() == 1) {
            return stride;
        }
        if ((stride & 1) != 0) {
            throw new IllegalArgumentException(
                    "plane " + plane + " of a " + frame.format() + " picture has an odd stride of " + stride);
        }
        return stride / 2;
    }
}
