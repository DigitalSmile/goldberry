package dev.goldberry.mic.analysis;

/// A radix-2 fast Fourier transform of one size, in place, with its tables
/// made once.
///
/// Not thread-safe: one transform at a time. Allocation-free after
/// construction, so it can run once a block on a source's thread.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class Fft {

    private final int size;
    private final int[] reversed;
    private final double[] cos;
    private final double[] sin;

    /// A transform of `size` points, a power of two from 2 to 65536.
    public Fft(int size) {
        if (size < 2 || size > 65_536 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("a size of " + size + " is not a power of two from 2 to 65536");
        }
        this.size = size;
        var bits = Integer.numberOfTrailingZeros(size);
        reversed = new int[size];
        for (var index = 0; index < size; index++) {
            reversed[index] = Integer.reverse(index) >>> (Integer.SIZE - bits);
        }
        cos = new double[size / 2];
        sin = new double[size / 2];
        for (var index = 0; index < size / 2; index++) {
            var angle = -2 * Math.PI * index / size;
            cos[index] = Math.cos(angle);
            sin[index] = Math.sin(angle);
        }
    }

    /// The number of points.
    public int size() {
        return size;
    }

    /// Transforms `real` and `imaginary` in place: on return they hold the
    /// spectrum, bin `k` at index `k`, unscaled.
    ///
    /// @throws IllegalArgumentException when an array is not [#size()] long
    public void transform(double[] real, double[] imaginary) {
        if (real.length != size || imaginary.length != size) {
            throw new IllegalArgumentException(
                    "arrays of " + real.length + " and " + imaginary.length + " for a transform of " + size);
        }
        for (var index = 0; index < size; index++) {
            var other = reversed[index];
            if (other > index) {
                swap(real, index, other);
                swap(imaginary, index, other);
            }
        }
        for (var length = 2; length <= size; length <<= 1) {
            var half = length / 2;
            var step = size / length;
            for (var start = 0; start < size; start += length) {
                for (var offset = 0; offset < half; offset++) {
                    var w = offset * step;
                    var even = start + offset;
                    var odd = even + half;
                    var re = real[odd] * cos[w] - imaginary[odd] * sin[w];
                    var im = real[odd] * sin[w] + imaginary[odd] * cos[w];
                    real[odd] = real[even] - re;
                    imaginary[odd] = imaginary[even] - im;
                    real[even] += re;
                    imaginary[even] += im;
                }
            }
        }
    }

    private static void swap(double[] values, int a, int b) {
        var kept = values[a];
        values[a] = values[b];
        values[b] = kept;
    }
}
