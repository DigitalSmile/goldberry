package dev.goldberry.mic.analysis;

import dev.goldberry.mic.source.SampleBlock;

/// How loud one block is: its peak and its RMS, linear, over every channel.
///
/// @param peak the largest absolute sample, 0 and up (1 is full scale)
/// @param rms  the root of the mean square, 0 and up
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public record Levels(float peak, float rms) {

    /// What silence reads in decibels: a floor rather than minus infinity.
    public static final double FLOOR_DECIBELS = -120;

    /// No sound at all.
    public static final Levels SILENCE = new Levels(0, 0);

    public Levels {
        if (!(peak >= 0) || !(rms >= 0)) {
            throw new IllegalArgumentException("peak " + peak + ", rms " + rms);
        }
    }

    /// The levels of `block`.
    public static Levels of(SampleBlock block) {
        var count = block.frames() * block.format().channels();
        if (count == 0) {
            return SILENCE;
        }
        var samples = block.samples();
        var peak = 0f;
        var squares = 0d;
        for (var index = 0; index < count; index++) {
            var sample = samples.get(index);
            peak = Math.max(peak, Math.abs(sample));
            squares += (double) sample * sample;
        }
        return new Levels(peak, (float) Math.sqrt(squares / count));
    }

    /// The peak in dBFS.
    public double peakDecibels() {
        return decibels(peak);
    }

    /// The RMS in dBFS.
    public double rmsDecibels() {
        return decibels(rms);
    }

    /// `linear` in decibels relative to full scale, never below [#FLOOR_DECIBELS].
    public static double decibels(double linear) {
        return linear <= 0 ? FLOOR_DECIBELS : Math.max(FLOOR_DECIBELS, 20 * Math.log10(linear));
    }
}
