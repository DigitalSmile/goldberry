package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.PathElement.sequenceElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;

/// The fields of `AVFrame` the Engine reads ([FfmpegStructs#AV_FRAME]).
///
/// Audio reads the sample fields. Video (phase 3) reads the picture's size, its
/// planes and strides, and the colour a decoder tagged it with.
final class AvFrameView {

    /// `AV_NUM_DATA_POINTERS`: how many planes `data[]` holds. A planar frame with
    /// more channels keeps the rest in `extended_data`, which is not read, so such
    /// a frame is refused.
    static final int DATA_POINTERS = 8;

    private static final long DATA = FfmpegStructs.AV_FRAME.byteOffset(groupElement("data"), sequenceElement(0));
    private static final long LINESIZE =
            FfmpegStructs.AV_FRAME.byteOffset(groupElement("linesize"), sequenceElement(0));
    private static final long WIDTH = offset("width");
    private static final long HEIGHT = offset("height");
    private static final long COLOR_RANGE = offset("color_range");
    private static final long COLORSPACE = offset("colorspace");
    private static final long DURATION = offset("duration");
    private static final long NB_SAMPLES = offset("nb_samples");
    private static final long FORMAT = offset("format");
    private static final long PTS = offset("pts");
    private static final long PKT_DTS = offset("pkt_dts");
    private static final long SAMPLE_RATE = offset("sample_rate");
    private static final long NB_CHANNELS =
            FfmpegStructs.AV_FRAME.byteOffset(groupElement("ch_layout"), groupElement("nb_channels"));

    private AvFrameView() {}

    static MemorySegment of(MemorySegment frame) {
        return Pointers.struct(frame, FfmpegStructs.AV_FRAME);
    }

    /// `data[index]`.
    static MemorySegment data(MemorySegment frame, int index) {
        if (index < 0 || index >= DATA_POINTERS) {
            throw new IndexOutOfBoundsException("plane " + index);
        }
        return frame.get(ADDRESS, DATA + (long) index * ADDRESS.byteSize());
    }

    /// `linesize[index]`: the bytes from one row of plane `index` to the next.
    static int lineSize(MemorySegment frame, int index) {
        if (index < 0 || index >= DATA_POINTERS) {
            throw new IndexOutOfBoundsException("plane " + index);
        }
        return frame.get(JAVA_INT, LINESIZE + (long) index * JAVA_INT.byteSize());
    }

    static int width(MemorySegment frame) {
        return frame.get(JAVA_INT, WIDTH);
    }

    static int height(MemorySegment frame) {
        return frame.get(JAVA_INT, HEIGHT);
    }

    /// `AVColorRange`.
    static int colorRange(MemorySegment frame) {
        return frame.get(JAVA_INT, COLOR_RANGE);
    }

    /// `AVColorSpace`: the YUV→RGB matrix.
    static int colorSpace(MemorySegment frame) {
        return frame.get(JAVA_INT, COLORSPACE);
    }

    /// How long the frame shows, in the stream's time base, or 0 when unknown.
    static long duration(MemorySegment frame) {
        return frame.get(JAVA_LONG, DURATION);
    }

    static int samples(MemorySegment frame) {
        return frame.get(JAVA_INT, NB_SAMPLES);
    }

    /// `AVSampleFormat` for audio, `AVPixelFormat` for video.
    static int format(MemorySegment frame) {
        return frame.get(JAVA_INT, FORMAT);
    }

    static long pts(MemorySegment frame) {
        return frame.get(JAVA_LONG, PTS);
    }

    /// The decode timestamp of the packet the frame came from: the fallback when a
    /// decoder left `pts` unset.
    static long packetDts(MemorySegment frame) {
        return frame.get(JAVA_LONG, PKT_DTS);
    }

    static int sampleRate(MemorySegment frame) {
        return frame.get(JAVA_INT, SAMPLE_RATE);
    }

    static int channels(MemorySegment frame) {
        return frame.get(JAVA_INT, NB_CHANNELS);
    }

    private static long offset(String field) {
        return FfmpegStructs.AV_FRAME.byteOffset(groupElement(field));
    }
}
