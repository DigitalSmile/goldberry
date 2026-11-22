package dev.goldberry.example.capture;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

import dev.goldberry.mic.source.SampleBlock;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.SampleListener;

/// Keeps what a sample source delivers, up to a length, as an [AudioClip].
///
/// A listener: hand it to `SampleSource.onSamples`, which calls it on the
/// source's thread, and read [#clip()] from any other. The block's buffer is
/// borrowed, so each block is copied as it arrives. Past the longest clip the
/// rest is dropped and [#full()] says so.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
public final class AudioClipRecorder implements SampleListener {

    /// The longest clip the Capture screen keeps.
    public static final Duration LONGEST = Duration.ofSeconds(30);

    private final SampleFormat format;
    private final int capacity;
    private float[] samples = new float[0];
    private int length;
    private boolean full;

    /// A recorder of blocks in `format`, keeping at most `longest` of them.
    ///
    /// @throws IllegalArgumentException when `longest` holds no frame
    public AudioClipRecorder(SampleFormat format, Duration longest) {
        this.format = Objects.requireNonNull(format, "format");
        var frames = format.framesIn(longest.toNanos());
        if (frames < 1) {
            throw new IllegalArgumentException("a clip of " + longest + " holds no frame");
        }
        this.capacity = Math.toIntExact(frames * format.channels());
    }

    @Override
    public synchronized void onSamples(SampleBlock block) {
        if (!block.format().equals(format)) {
            throw new IllegalArgumentException("a block in " + block.format() + " for a clip in " + format);
        }
        var copy = block.copy();
        var room = capacity - length;
        var taken = Math.min(room, copy.length);
        if (taken < copy.length) {
            full = true;
        }
        if (taken <= 0) {
            return;
        }
        if (length + taken > samples.length) {
            samples = Arrays.copyOf(samples, Math.min(capacity, Math.max(length + taken, samples.length * 2)));
        }
        System.arraycopy(copy, 0, samples, length, taken);
        length += taken;
        if (length == capacity) {
            full = true;
        }
    }

    /// Whether it has kept as much as it may.
    public synchronized boolean full() {
        return full;
    }

    /// How much it has kept.
    public synchronized Duration recorded() {
        return Duration.ofNanos(format.nanosOf(length / format.channels()));
    }

    /// What it has kept so far.
    public synchronized AudioClip clip() {
        return new AudioClip(Arrays.copyOf(samples, length), format);
    }
}
