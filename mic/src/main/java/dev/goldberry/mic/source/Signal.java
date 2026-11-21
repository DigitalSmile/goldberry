package dev.goldberry.mic.source;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// What a [SyntheticSource] plays: silence, a tone, noise, a clip, or a mix of
/// them. Every signal is a pure function of the frame number, so the same
/// frames come out however the source is advanced, which is what makes a
/// golden image of a level meter or a spectrum repeatable.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public sealed interface Signal {

    /// Nothing.
    record Silence() implements Signal {}

    /// A sine of `hertz` at peak `amplitude`, starting at phase zero.
    record Tone(double hertz, double amplitude) implements Signal {
        public Tone {
            if (!(hertz > 0) || !(amplitude >= 0)) {
                throw new IllegalArgumentException(hertz + " Hz at " + amplitude);
            }
        }
    }

    /// White noise, uniform in `-amplitude..amplitude`, the same for the same
    /// `seed`.
    record Noise(double amplitude, long seed) implements Signal {
        public Noise {
            if (!(amplitude >= 0)) {
                throw new IllegalArgumentException("amplitude " + amplitude);
            }
        }
    }

    /// `samples`, mono, then silence, or again from the start when `loop`.
    final class Clip implements Signal {

        private final float[] samples;
        private final boolean loop;

        /// A clip of `samples`, copied.
        public Clip(float[] samples, boolean loop) {
            this.samples = samples.clone();
            this.loop = loop;
        }

        /// The samples, copied.
        public float[] samples() {
            return samples.clone();
        }

        /// Whether it plays again from the start when it ends.
        public boolean loop() {
            return loop;
        }

        float at(long frame) {
            if (samples.length == 0 || (!loop && frame >= samples.length)) {
                return 0f;
            }
            return samples[(int) (frame % samples.length)];
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Clip clip && loop == clip.loop && Arrays.equals(samples, clip.samples);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(samples) + Boolean.hashCode(loop);
        }

        @Override
        public String toString() {
            return "Clip[" + samples.length + " samples" + (loop ? ", looped]" : "]");
        }
    }

    /// The signals added together.
    record Mix(List<Signal> signals) implements Signal {
        public Mix {
            signals = List.copyOf(signals);
        }
    }

    /// The value of this signal at `frame`, sampled at `sampleRate`.
    default float valueAt(long frame, int sampleRate) {
        return switch (this) {
            case Silence _ -> 0f;
            case Tone(var hertz, var amplitude) -> {
                // Whole seconds and the rest apart, so an hour in the phase
                // keeps the precision of its first second.
                long seconds = frame / sampleRate;
                var cycles = (hertz * seconds) % 1.0 + hertz * (frame % sampleRate) / sampleRate;
                yield (float) (amplitude * Math.sin(2 * Math.PI * cycles));
            }
            case Noise(var amplitude, var seed) -> (float) (amplitude * (2 * unit(seed, frame) - 1));
            case Clip clip -> clip.at(frame);
            case Mix(var signals) -> {
                var sum = 0f;
                for (var signal : signals) {
                    sum += signal.valueAt(frame, sampleRate);
                }
                yield sum;
            }
        };
    }

    /// A number in `[0, 1)` from `seed` and `frame` alone: SplitMix64's finaliser.
    private static double unit(long seed, long frame) {
        var z = seed + frame * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    /// Silence.
    static Signal silence() {
        return new Silence();
    }

    /// A tone.
    static Signal tone(double hertz, double amplitude) {
        return new Tone(hertz, amplitude);
    }

    /// The signals added together.
    static Signal mix(Signal... signals) {
        return new Mix(List.of(Objects.requireNonNull(signals, "signals")));
    }
}
