package dev.goldberry.mic.analysis;

import dev.goldberry.mic.source.SampleBlock;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.SampleListener;

/// The spectrum of the last `size` frames heard, their channels averaged,
/// through a Hann window.
///
/// Fed on the source's thread; [#spectrum()] is safe from any thread and
/// transforms only when something new has been heard since the last call.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class SpectrumAnalyzer implements SampleListener {

    private final SampleFormat format;
    private final Fft fft;
    private final double[] window;
    private final float[] history;
    private final double[] real;
    private final double[] imaginary;
    private final double windowGain;
    private int next;
    private boolean changed;
    private Spectrum last;

    /// An analyzer of `size` points, a power of two, for samples in `format`.
    public SpectrumAnalyzer(SampleFormat format, int size) {
        this.format = format;
        this.fft = new Fft(size);
        this.window = new double[size];
        var sum = 0d;
        for (var index = 0; index < size; index++) {
            window[index] = 0.5 - 0.5 * Math.cos(2 * Math.PI * index / size);
            sum += window[index];
        }
        this.windowGain = sum;
        this.history = new float[size];
        this.real = new double[size];
        this.imaginary = new double[size];
        this.last = Spectrum.silent(size, format.sampleRate());
    }

    @Override
    public synchronized void onSamples(SampleBlock block) {
        for (var frame = 0; frame < block.frames(); frame++) {
            history[next] = block.mono(frame);
            next = (next + 1) % history.length;
        }
        changed |= block.frames() > 0;
    }

    /// The spectrum of the last [Fft#size()] frames, oldest first.
    public synchronized Spectrum spectrum() {
        if (!changed) {
            return last;
        }
        changed = false;
        var size = history.length;
        for (var index = 0; index < size; index++) {
            real[index] = history[(next + index) % size] * window[index];
            imaginary[index] = 0;
        }
        fft.transform(real, imaginary);
        var decibels = new double[size / 2 + 1];
        for (var bin = 0; bin < decibels.length; bin++) {
            // Two sides of the spectrum folded into one, except DC and Nyquist,
            // and scaled by the window's gain, so a full-scale sine reads 0 dB.
            var magnitude = Math.hypot(real[bin], imaginary[bin]) / windowGain;
            if (bin != 0 && bin != size / 2) {
                magnitude *= 2;
            }
            decibels[bin] = Levels.decibels(magnitude);
        }
        last = new Spectrum(decibels, format.sampleRate(), size);
        return last;
    }
}
