package dev.goldberry.media.platform.fixtures;

/// What a test measures of the fixtures' tones once a decoder has decoded
/// them: which frequency is loudest, and how loud.
public final class Tones {

    private Tones() {}

    /// The whole frequency between `from` and `to` Hz with the most power in
    /// `samples` at `rate`, by Goertzel's algorithm at each.
    public static int dominant(float[] samples, int rate, int from, int to) {
        var best = from;
        var bestPower = -1.0;
        for (var frequency = from; frequency <= to; frequency++) {
            var coefficient = 2 * Math.cos(2 * Math.PI * frequency / rate);
            double previous = 0;
            double beforePrevious = 0;
            for (var sample : samples) {
                var current = sample + coefficient * previous - beforePrevious;
                beforePrevious = previous;
                previous = current;
            }
            var power = previous * previous + beforePrevious * beforePrevious - coefficient * previous * beforePrevious;
            if (power > bestPower) {
                bestPower = power;
                best = frequency;
            }
        }
        return best;
    }

    /// The root mean square of `samples`.
    public static double rms(float[] samples) {
        var sum = 0.0;
        for (var sample : samples) {
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }
}
