package dev.goldberry.mic.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Fft")
class FftTest {

    @Test
    @DisplayName("an impulse is flat, and a cosine on bin k is all in bins k and n - k")
    void knownTransforms() {
        var fft = new Fft(16);
        var re = new double[16];
        var im = new double[16];
        re[0] = 1;
        fft.transform(re, im);
        for (var bin = 0; bin < 16; bin++) {
            assertEquals(1, Math.hypot(re[bin], im[bin]), 1e-12, "impulse bin " + bin);
        }
        re = new double[16];
        im = new double[16];
        for (var index = 0; index < 16; index++) {
            re[index] = Math.cos(2 * Math.PI * 3 * index / 16);
        }
        fft.transform(re, im);
        for (var bin = 0; bin < 16; bin++) {
            var expected = bin == 3 || bin == 13 ? 8 : 0;
            assertEquals(expected, Math.hypot(re[bin], im[bin]), 1e-9, "cosine bin " + bin);
        }
    }

    @Test
    @DisplayName("keeps the energy (Parseval) of an arbitrary signal")
    void parseval() {
        var fft = new Fft(1_024);
        var re = new double[1_024];
        var im = new double[1_024];
        var time = 0d;
        for (var index = 0; index < re.length; index++) {
            re[index] = Math.sin(index * 0.37) + 0.25 * Math.cos(index * 1.9);
            time += re[index] * re[index];
        }
        fft.transform(re, im);
        var frequency = 0d;
        for (var bin = 0; bin < re.length; bin++) {
            frequency += re[bin] * re[bin] + im[bin] * im[bin];
        }
        assertEquals(time, frequency / re.length, 1e-6 * time);
    }

    @Test
    @DisplayName("refuses a size that is not a power of two, and arrays of another size")
    void refuses() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Fft(12)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Fft(1)),
                () -> assertThrows(
                        IllegalArgumentException.class, () -> new Fft(8).transform(new double[4], new double[8])));
    }
}
