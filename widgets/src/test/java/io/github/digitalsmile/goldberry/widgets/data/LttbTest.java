package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Downsampling that keeps the shape — `content-widgets.md` §3.1.
///
/// The claim being tested is not "fewer points". It is that **the point that
/// matters survives**, which is the whole difference between this and taking
/// every *n*th sample, and the reason a spike in a 100k-point series is visible
/// in a 200px sparkline at all.
class LttbTest {

    private static List<Double> ramp(int n) {
        var values = new ArrayList<Double>(n);
        for (var i = 0; i < n; i++) {
            values.add((double) i);
        }
        return values;
    }

    @Test
    @DisplayName("keeps everything when there is nothing to gain")
    void shortSeriesAreUntouched() {
        assertEquals(List.of(1.0, 2.0, 3.0), Lttb.downsample(List.of(1.0, 2.0, 3.0), 10));
        assertEquals(List.of(1.0, 2.0, 3.0), Lttb.downsample(List.of(1.0, 2.0, 3.0), 3));
        // Below three there is no middle to choose from, so choosing is not a
        // thing this can do and the input comes back.
        assertEquals(4, Lttb.indices(ramp(4), 2).length);
    }

    @Test
    @DisplayName("keeps the ends, so the series is drawn over the range it has")
    void endsAreAlwaysKept() {
        var kept = Lttb.indices(ramp(1000), 50);

        assertEquals(50, kept.length);
        assertEquals(0, kept[0]);
        assertEquals(999, kept[kept.length - 1]);
    }

    @Test
    @DisplayName("returns indices in order, without repeats")
    void indicesAreOrdered() {
        var kept = Lttb.indices(ramp(10_000), 300);

        for (var i = 1; i < kept.length; i++) {
            assertTrue(kept[i] > kept[i - 1], "index " + i + " went backwards: " + kept[i - 1] + " then " + kept[i]);
        }
    }

    @Test
    @DisplayName("keeps a one-sample spike that a stride would step over")
    void theSpikeSurvives() {
        // The test this algorithm exists for. A flat series with one sample
        // twenty times the rest, at an index no stride of 100 would land on.
        var values = new ArrayList<Double>();
        for (var i = 0; i < 10_000; i++) {
            values.add(1.0);
        }
        values.set(4_237, 20.0);

        var kept = Lttb.downsample(values, 100);

        assertTrue(kept.contains(20.0), "the spike is the one point a sparkline exists to show, and it was dropped");

        // And the comparison that makes the point: every hundredth sample, which
        // is what "just take fewer points" means, misses it entirely.
        var strided = new ArrayList<Double>();
        for (var i = 0; i < values.size(); i += 100) {
            strided.add(values.get(i));
        }
        assertTrue(!strided.contains(20.0), "if a stride caught the spike this test is not testing what it claims");
    }

    @Test
    @DisplayName("keeps both ends of a spike pair rather than averaging them away")
    void bothExtremesSurvive() {
        var values = new ArrayList<Double>();
        for (var i = 0; i < 5_000; i++) {
            values.add(0.0);
        }
        values.set(1_111, 50.0);
        values.set(3_777, -50.0);

        var kept = Lttb.downsample(values, 200);

        assertTrue(kept.contains(50.0), "the maximum");
        assertTrue(kept.contains(-50.0), "and the minimum");
    }

    @Test
    @DisplayName("never asks for a point the series has not got")
    void indicesAreInRange() {
        // The bucket arithmetic is in doubles and the last bucket can land
        // exactly on the end, which is where an off-by-one would be.
        for (var n : List.of(101, 1_000, 4_096, 9_999)) {
            for (var threshold : List.of(3, 7, 100, 999)) {
                if (threshold >= n) {
                    continue;
                }
                for (var index : Lttb.indices(ramp(n), threshold)) {
                    assertTrue(
                            index >= 0 && index < n,
                            "index " + index + " is outside a series of " + n + " at threshold " + threshold);
                }
            }
        }
    }
}
