package dev.goldberry.media.picture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;

import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.YuvReference.Chroma;
import dev.goldberry.media.picture.YuvReference.Reading;

/// Holds a present path's picture to [YuvReference], and when it is off, says
/// which mistake would have drawn it.
///
/// A failure that only says "differs by 150 levels" sends the reader to a
/// debugger. Every way of misreading a picture leaves its own picture, so the
/// misreadings are drawn too and the closest is named: Cb and Cr swapped, red
/// and blue swapped, the wrong matrix, the wrong range, chroma repeated or
/// sited left. A picture that fits none of them is said to be something else.
public final class YuvDiagnosis {

    /// The closest reading, and how far the picture is from it.
    ///
    /// @param reading the reading
    /// @param mean    the mean channel difference from it
    /// @param worst   the largest channel difference from it
    public record Fit(Reading reading, double mean, int worst) {}

    private YuvDiagnosis() {}

    /// Fails unless `actual` is `source` read its own way, within `tolerance`
    /// levels a channel; the failure names the misreading `actual` looks like.
    ///
    /// @param actual `source`'s picture as `0xAARRGGBB`, row by row
    /// @param what   what the picture is, for the message
    public static void assertReadsAsTagged(YuvSource source, int[] actual, int tolerance, String what) {
        mismatch(source, actual, tolerance).ifPresent(message -> Assertions.fail(what + ": " + message));
    }

    /// Why `actual` is not `source` read its own way within `tolerance`, or
    /// empty when it is.
    public static Optional<String> mismatch(YuvSource source, int[] actual, int tolerance) {
        var expected = Reading.of(source);
        var worst = YuvReference.worst(YuvReference.argb(source, expected), actual);
        if (worst <= tolerance) {
            return Optional.empty();
        }
        var best = closest(source, actual);
        var differences = differences(expected, best.reading());
        var verdict = differences.isEmpty()
                ? "no misreading explains it better than the right reading does (mean "
                        + String.format("%.1f", best.mean()) + " levels): look at the arithmetic, not the plumbing"
                : "it looks like " + String.join(", and ", differences) + " (that reading is within " + best.worst()
                        + " levels, mean " + String.format("%.1f", best.mean()) + ")";
        return Optional.of("differs from the " + describe(expected) + " reference by " + worst + " levels; " + verdict);
    }

    /// The reading `actual` is closest to, of every matrix, range, chroma and
    /// swap.
    public static Fit closest(YuvSource source, int[] actual) {
        var expected = Reading.of(source);
        var fits = new ArrayList<Fit>();
        for (var reading : readings()) {
            var picture = YuvReference.argb(source, reading);
            fits.add(new Fit(reading, YuvReference.mean(picture, actual), YuvReference.worst(picture, actual)));
        }
        // A picture that cannot tell two readings apart (a grey one cannot tell
        // the matrices apart) is put down to the one with the fewest mistakes.
        return fits.stream()
                .min(Comparator.comparingDouble(Fit::mean)
                        .thenComparingInt(
                                fit -> differences(expected, fit.reading()).size()))
                .orElseThrow();
    }

    /// What `actual` gets wrong against `expected`, in words; empty when they
    /// are the same reading.
    static List<String> differences(Reading expected, Reading actual) {
        var words = new ArrayList<String>();
        if (actual.chromaSwapped() != expected.chromaSwapped()) {
            words.add("Cb and Cr swapped");
        }
        if (actual.redBlueSwapped() != expected.redBlueSwapped()) {
            words.add("red and blue swapped (RGBA where BGRA is read)");
        }
        if (actual.matrix() != expected.matrix()) {
            words.add(actual.matrix() + " where the picture says " + expected.matrix());
        }
        if (actual.fullRange() != expected.fullRange()) {
            words.add((actual.fullRange() ? "full" : "limited") + " range where the picture says "
                    + (expected.fullRange() ? "full" : "limited"));
        }
        if (actual.chroma() != expected.chroma()) {
            words.add(
                    switch (actual.chroma()) {
                        case REPEATED -> "chroma repeated over each 2×2 block instead of blended";
                        case BLENDED_LEFT -> "chroma sited on the left column instead of centred";
                        case BLENDED -> "chroma blended where " + expected.chroma() + " was expected";
                    });
        }
        return words;
    }

    private static String describe(Reading reading) {
        return reading.matrix() + (reading.fullRange() ? " full-range" : " limited-range") + ", chroma "
                + reading.chroma().name().toLowerCase(Locale.ROOT);
    }

    /// Every reading.
    private static List<Reading> readings() {
        var all = new ArrayList<Reading>();
        for (var chroma : Chroma.values()) {
            for (var matrix : VideoFrame.ColorMatrix.values()) {
                for (var fullRange : new boolean[] {false, true}) {
                    for (var chromaSwapped : new boolean[] {false, true}) {
                        for (var redBlueSwapped : new boolean[] {false, true}) {
                            all.add(new Reading(matrix, fullRange, chroma, chromaSwapped, redBlueSwapped));
                        }
                    }
                }
            }
        }
        return all;
    }
}
