package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.PathElement.sequenceElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;

/// The fields of `AVFrame` the Engine reads ([FfmpegStructs#AV_FRAME]).
///
/// Phase 2 reads what describes audio. The picture fields (`width`, `height`,
/// `linesize`, the colour fields) are in the layout and are read by CPU present
/// in phase 3.
final class AvFrameView {

    /// `AV_NUM_DATA_POINTERS`: how many planes `data[]` holds. A planar frame with
    /// more channels keeps the rest in `extended_data`, which is not read, so such
    /// a frame is refused.
    static final int DATA_POINTERS = 8;

    private static final long DATA = FfmpegStructs.AV_FRAME.byteOffset(groupElement("data"), sequenceElement(0));
    private static final long NB_SAMPLES = offset("nb_samples");
    private static final long FORMAT = offset("format");
    private static final long PTS = offset("pts");
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
