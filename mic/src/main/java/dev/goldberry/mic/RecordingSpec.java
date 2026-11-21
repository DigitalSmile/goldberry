package dev.goldberry.mic;

import java.time.Duration;
import java.util.Objects;

import dev.goldberry.mic.source.SampleFormat;

/// What a recording is asked for: its format, how much it hands over at a time,
/// and how much it keeps for a caller that pulls.
///
/// @param format      the rate and channels samples arrive in; SDL converts
///                    whatever the device runs at
/// @param blockFrames frames in each block handed to listeners
/// @param buffer      how much recorded sound [Recording#read] can still take
///                    when the caller falls behind; older sound is dropped
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public record RecordingSpec(SampleFormat format, int blockFrames, Duration buffer) {

    /// How long a block lasts by default.
    public static final Duration BLOCK = Duration.ofMillis(10);

    /// How much is kept for a caller that pulls, by default.
    public static final Duration BUFFER = Duration.ofSeconds(2);

    public RecordingSpec {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(buffer, "buffer");
        if (blockFrames < 1 || blockFrames > format.sampleRate()) {
            throw new IllegalArgumentException("blocks of " + blockFrames + " frames");
        }
        if (format.framesIn(buffer.toNanos()) < blockFrames) {
            throw new IllegalArgumentException("a buffer of " + buffer + " holds less than one block");
        }
    }

    /// `format` in 10 ms blocks, with two seconds kept.
    public static RecordingSpec of(SampleFormat format) {
        return new RecordingSpec(format, (int) format.framesIn(BLOCK.toNanos()), BUFFER);
    }

    /// A voice: 48 kHz mono in 10 ms blocks.
    public static RecordingSpec voice() {
        return of(SampleFormat.VOICE);
    }

    /// The same, in blocks of `block`.
    public RecordingSpec withBlock(Duration block) {
        return new RecordingSpec(format, (int) format.framesIn(block.toNanos()), buffer);
    }

    /// The frames [#buffer] holds.
    public int bufferFrames() {
        return Math.toIntExact(format.framesIn(buffer.toNanos()));
    }
}
