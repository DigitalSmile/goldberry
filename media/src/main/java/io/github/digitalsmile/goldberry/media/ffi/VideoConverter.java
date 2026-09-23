package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;

/// Converts pictures with swscale: CPU present's one pass from a decoded
/// [VideoFrame] to the toolkit's premultiplied BGRA (`docs/goldberry-media.md`
/// §3, "Presentation"), and the built-in decoder's pass from a pixel format
/// outside the frame contract to I420.
///
/// **The same size in and out.** Nothing here scales: a picture is converted at
/// the size it was decoded at, and the widget scales it at the blit, where the
/// size on screen is known and no pixels are kept. So swscale's filter choice
/// touches only the chroma upsampling.
///
/// **The same bytes on every machine.** The context is made with
/// `SWS_BITEXACT | SWS_ACCURATE_RND`, which turns off the SIMD paths whose
/// rounding differs between CPUs. The cost is a slower conversion. What it buys
/// is a golden of a decoded frame that holds on x64 and on ARM (§7, S5), which
/// the software decoders already guarantee for their half.
///
/// The picture's colour is honoured: the matrix and range it was tagged with
/// become swscale's source coefficients, and RGB is always full range.
///
/// One context, remade only when the conversion changes (size, format, matrix or
/// range), so a stream pays for `sws_getContext` once. **Confined to one thread**,
/// the video decode thread that owns it, which is swscale's own rule for a
/// context.
public final class VideoConverter implements AutoCloseable {

    /// Neutral brightness, contrast and saturation, in swscale's 16.16 fixed
    /// point.
    private static final int UNITY = 1 << 16;

    private final Ffmpeg ffmpeg;
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment srcPlanes = arena.allocate(ADDRESS, 4);
    private final MemorySegment srcStrides = arena.allocate(JAVA_INT, 4);
    private final MemorySegment dstPlanes = arena.allocate(ADDRESS, 4);
    private final MemorySegment dstStrides = arena.allocate(JAVA_INT, 4);
    private MemorySegment context = MemorySegment.NULL;
    private Key key = Key.NONE;
    private boolean closed;

    /// What the current context converts: remade when any of it changes.
    private record Key(int width, int height, int source, int target, int colorspace, boolean fullRange) {
        static final Key NONE = new Key(0, 0, -1, -1, -1, false);
    }

    /// A converter over `ffmpeg`'s swscale. Nothing is allocated natively until
    /// the first picture.
    public VideoConverter(Ffmpeg ffmpeg) {
        this.ffmpeg = Objects.requireNonNull(ffmpeg, "ffmpeg");
    }

    /// Converts `frame` to premultiplied BGRA at its own size: opaque, so
    /// premultiplied and straight are the same bytes.
    ///
    /// @param target       at least `stride × height` bytes
    /// @param targetStride the bytes from one row of `target` to the next; at
    ///                     least `4 × width`
    /// @throws MediaException [MediaError.InvalidData] when swscale refuses the
    ///                        conversion
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
        var video = ffmpeg.constants().video();
        convert(
                frame.width(),
                frame.height(),
                video.avPixelFormat(frame.format()),
                frame.planes(),
                frame.strides(),
                video.swsColorspace(frame.matrix()),
                frame.fullRange(),
                video.pixFmtBgra(),
                List.of(target),
                List.of(targetStride));
    }

    /// Converts any picture swscale reads into any format it writes, at the same
    /// size, keeping the source's matrix and range. The built-in decoder's path
    /// for pixel formats outside the frame contract.
    ///
    /// @param source    the source's `AVPixelFormat`
    /// @param planes    the source's planes, as many as its format has
    /// @param strides   the bytes a row of each
    /// @param colorspace the `SWS_CS_*` matrix the source was encoded with
    /// @param fullRange whether the source's luma spans 0–255
    /// @param target    the `AVPixelFormat` to write
    /// @param outPlanes where to write, as many as `target` has
    /// @param outStrides the bytes a row of each
    void convert(
            int width,
            int height,
            int source,
            List<MemorySegment> planes,
            List<Integer> strides,
            int colorspace,
            boolean fullRange,
            int target,
            List<MemorySegment> outPlanes,
            List<Integer> outStrides) {
        if (closed) {
            throw new IllegalStateException("converter closed");
        }
        prepare(new Key(width, height, source, target, colorspace, fullRange));
        fill(srcPlanes, srcStrides, planes, strides);
        fill(dstPlanes, dstStrides, outPlanes, outStrides);
        var rows = ffmpeg.swScale().scale().call(context, srcPlanes, srcStrides, 0, height, dstPlanes, dstStrides);
        if (rows < 0) {
            throw new MediaException(new MediaError.InvalidData("sws_scale: " + ffmpeg.describe(rows)));
        }
        if (rows != height) {
            throw new MediaException(new MediaError.InvalidData("sws_scale wrote " + rows + " of " + height + " rows"));
        }
    }

    /// Frees the context. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        free();
        arena.close();
    }

    private void prepare(Key wanted) {
        if (wanted.equals(key)) {
            return;
        }
        free();
        // Checked here and not left to swscale: FFmpeg 8 asserts, and so aborts
        // the whole process, when a context is asked for with a format it has no
        // descriptor for. A name is what having a descriptor looks like from here.
        for (var format : new int[] {wanted.source(), wanted.target()}) {
            if (ffmpeg.pixelFormatName(format).isEmpty()) {
                throw new MediaException(
                        new MediaError.InvalidData("pixel format #" + format + " is not one this build knows"));
            }
        }
        var video = ffmpeg.constants().video();
        var flags = video.swsBilinear() | video.swsAccurateRnd() | video.swsBitexact() | video.swsFullChrHInt();
        var made = ffmpeg.swScale()
                .getContext()
                .call(
                        wanted.width(),
                        wanted.height(),
                        wanted.source(),
                        wanted.width(),
                        wanted.height(),
                        wanted.target(),
                        flags);
        if (made.equals(MemorySegment.NULL)) {
            throw new MediaException(new MediaError.InvalidData("swscale cannot convert "
                    + ffmpeg.pixelFormatName(wanted.source()).orElse("#" + wanted.source()) + " to "
                    + ffmpeg.pixelFormatName(wanted.target()).orElse("#" + wanted.target()) + " at "
                    + wanted.width() + "×" + wanted.height()));
        }
        context = made;
        key = wanted;
        var coefficients = ffmpeg.swScale().getCoefficients().call(wanted.colorspace());
        // The destination table matters only for a YUV target, and a YUV target
        // keeps the source's matrix and range; RGB is always full range.
        var targetFullRange = wanted.target() == video.pixFmtBgra() || wanted.fullRange();
        var result = ffmpeg.swScale()
                .setColorspaceDetails()
                .call(
                        context,
                        coefficients,
                        wanted.fullRange() ? 1 : 0,
                        coefficients,
                        targetFullRange ? 1 : 0,
                        0,
                        UNITY,
                        UNITY);
        if (result < 0) {
            throw new MediaException(
                    new MediaError.InvalidData("sws_setColorspaceDetails: " + ffmpeg.describe(result)));
        }
    }

    private void free() {
        if (!context.equals(MemorySegment.NULL)) {
            ffmpeg.swScale().freeContext().call(context);
            context = MemorySegment.NULL;
        }
        key = Key.NONE;
    }

    /// Writes up to four plane pointers and strides, and nulls the rest: swscale
    /// reads four entries whatever the format.
    private static void fill(
            MemorySegment pointers, MemorySegment lengths, List<MemorySegment> planes, List<Integer> strides) {
        if (planes.size() > 4 || planes.size() != strides.size()) {
            throw new IllegalArgumentException(planes.size() + " planes and " + strides.size() + " strides");
        }
        for (var i = 0; i < 4; i++) {
            pointers.setAtIndex(ADDRESS, i, i < planes.size() ? planes.get(i) : MemorySegment.NULL);
            lengths.setAtIndex(JAVA_INT, i, i < strides.size() ? strides.get(i) : 0);
        }
    }
}
