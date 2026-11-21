package dev.goldberry.mic.analysis;

import java.util.Arrays;

/// The magnitude of each frequency bin, from DC to half the sample rate, in
/// dBFS: what [SpectrumAnalyzer] reports, and what a spectrum view draws.
///
/// A full-scale sine at a bin's centre reads 0 dB in that bin.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class Spectrum {

    private final double[] decibels;
    private final int sampleRate;
    private final int size;

    Spectrum(double[] decibels, int sampleRate, int size) {
        this.decibels = decibels;
        this.sampleRate = sampleRate;
        this.size = size;
    }

    /// A spectrum of a `size`-point transform, every bin at the floor: what nothing heard reads.
    static Spectrum silent(int size, int sampleRate) {
        var floor = new double[size / 2 + 1];
        Arrays.fill(floor, Levels.FLOOR_DECIBELS);
        return new Spectrum(floor, sampleRate, size);
    }

    /// How many bins: half the transform's size, plus DC.
    public int bins() {
        return decibels.length;
    }

    /// The frequency at the centre of bin `bin`.
    public double hertz(int bin) {
        return (double) bin * sampleRate / size;
    }

    /// The bin nearest `hertz`.
    public int binOf(double hertz) {
        return (int) Math.max(0, Math.min(bins() - 1, Math.round(hertz * size / sampleRate)));
    }

    /// Bin `bin`'s magnitude, in dBFS.
    public double decibels(int bin) {
        return decibels[bin];
    }

    /// `count` bands spaced evenly in log frequency between `lowHertz` and
    /// `highHertz`, each the loudest bin inside it: the bars of a spectrum
    /// view. A band narrower than one bin takes the bin under its centre.
    public double[] bands(int count, double lowHertz, double highHertz) {
        if (count < 1 || !(lowHertz > 0) || !(highHertz > lowHertz)) {
            throw new IllegalArgumentException(count + " bands from " + lowHertz + " to " + highHertz + " Hz");
        }
        var bands = new double[count];
        var ratio = Math.log(highHertz / lowHertz);
        for (var band = 0; band < count; band++) {
            var from = lowHertz * Math.exp(ratio * band / count);
            var to = lowHertz * Math.exp(ratio * (band + 1) / count);
            var first = binOf(from);
            var last = Math.max(first, binOf(to) - 1);
            var loudest = Levels.FLOOR_DECIBELS;
            for (var bin = first; bin <= last; bin++) {
                loudest = Math.max(loudest, decibels[bin]);
            }
            bands[band] = loudest;
        }
        return bands;
    }
}
