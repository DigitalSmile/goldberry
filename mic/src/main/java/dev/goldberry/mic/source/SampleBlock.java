package dev.goldberry.mic.source;

import java.nio.FloatBuffer;
import java.util.Objects;

/// One block of samples as a [SampleSource] hands it to its listeners:
/// interleaved, `frames × channels` floats from position zero.
///
/// **Borrowed.** The buffer is the source's and is reused for the next block,
/// so it is valid only during the [SampleListener#onSamples] call; a listener
/// that keeps samples copies them ([#copy()]).
///
/// @param samples  the samples, read-only, `frames × channels` of them
/// @param format   their rate and channel count
/// @param frames   frames in this block
/// @param position frames the source delivered before this block
/// @param nanoTime when the block's last frame was taken: [System#nanoTime()]
///                 for a recording, the source's own clock for a synthetic one
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public record SampleBlock(FloatBuffer samples, SampleFormat format, int frames, long position, long nanoTime) {

    public SampleBlock {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(format, "format");
        if (frames < 0 || position < 0) {
            throw new IllegalArgumentException(frames + " frames at " + position);
        }
        if (samples.limit() < frames * format.channels()) {
            throw new IllegalArgumentException(
                    samples.limit() + " samples cannot hold " + frames + " frames of " + format.channels());
        }
    }

    /// The sample of `channel` in frame `frame`.
    public float sample(int frame, int channel) {
        Objects.checkIndex(frame, frames);
        Objects.checkIndex(channel, format.channels());
        return samples.get(frame * format.channels() + channel);
    }

    /// Frame `frame`'s channels averaged: what an analysis of one signal reads.
    public float mono(int frame) {
        Objects.checkIndex(frame, frames);
        var channels = format.channels();
        if (channels == 1) {
            return samples.get(frame);
        }
        var sum = 0f;
        for (var channel = 0; channel < channels; channel++) {
            sum += samples.get(frame * channels + channel);
        }
        return sum / channels;
    }

    /// The samples, copied out of the borrowed buffer.
    public float[] copy() {
        var copy = new float[frames * format.channels()];
        samples.get(0, copy);
        return copy;
    }
}
