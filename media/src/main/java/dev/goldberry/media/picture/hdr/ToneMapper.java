package dev.goldberry.media.picture.hdr;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.ffm.calls.MediaCalls;

/// Converts HDR and wide-gamut pictures through `goldberry_media_tone_map`,
/// with each [ToneCurve]'s tables built once a matrix, range and depth, and
/// kept.
///
/// A stream keeps one curve, so a mapper holds one or two sets of tables; they
/// live in an automatic arena and go with the mapper. Safe to share between
/// threads: the tables are built under a lock and only read after.
public final class ToneMapper {

    /// What one set of tables is for.
    private record Key(ToneCurve curve, VideoFrame.ColorMatrix matrix, boolean fullRange, int bitDepth) {}

    private final MediaCalls.ToneMap toneMap;
    private final Arena arena = Arena.ofAuto();
    private final Map<Key, MemorySegment> tables = new HashMap<>();

    /// A mapper over the library's `goldberry_media_tone_map`.
    public ToneMapper(MediaCalls calls) {
        this.toneMap = Objects.requireNonNull(calls, "calls").toneMap();
    }

    /// Converts `frame`, opaque, to SDR BT.709 BGRA at `target`, rows
    /// `targetStride` bytes apart, as `curve` maps its colour.
    ///
    /// @throws IllegalArgumentException for a picture with alpha, or a target
    ///                                  too small for the picture
    public void convert(ToneCurve curve, VideoFrame frame, MemorySegment target, int targetStride) {
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(target, "target");
        var width = frame.width();
        var height = frame.height();
        if (targetStride < width * 4 || target.byteSize() < (long) targetStride * height) {
            throw new IllegalArgumentException("a " + width + "×" + height + " picture at stride " + targetStride
                    + " in " + target.byteSize() + " bytes");
        }
        var format = switch (frame.format()) {
            case I420 -> 0;
            case NV12 -> 1;
            case I010 -> 2;
            case P010 -> 3;
            case I420A -> throw new IllegalArgumentException("a picture with alpha is not tone-mapped");
        };
        var planes = frame.planes();
        var strides = frame.strides();
        var third = planes.size() > 2 ? planes.get(2) : MemorySegment.NULL;
        var thirdStride = strides.size() > 2 ? strides.get(2) : 0;
        var depth = frame.format().bytesPerSample() == 2 ? 10 : 8;
        toneMap.call(
                planes.get(0),
                strides.get(0),
                planes.get(1),
                strides.get(1),
                third,
                thirdStride,
                format,
                target,
                targetStride,
                width,
                height,
                curve.flags(),
                tablesOf(new Key(curve, frame.matrix(), frame.fullRange(), depth)));
    }

    /// Whether a picture of `format` is converted here when its colour asks:
    /// every opaque layout.
    public static boolean converts(PixelFormat format) {
        return !format.hasAlpha();
    }

    private MemorySegment tablesOf(Key key) {
        synchronized (tables) {
            return tables.computeIfAbsent(
                    key,
                    k -> arena.allocateFrom(JAVA_FLOAT, k.curve().tables(k.matrix(), k.fullRange(), k.bitDepth())));
        }
    }
}
