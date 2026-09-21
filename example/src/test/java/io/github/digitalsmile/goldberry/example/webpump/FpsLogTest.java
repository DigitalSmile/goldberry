package io.github.digitalsmile.goldberry.example.webpump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.example.webpump.FpsLog.Verdict;

/// That the probe's summary says what the readings mean.
///
/// The verdict is the whole value of the probe — three numbers that have to be
/// read together, and the reading that matters is the one nobody expects. These
/// are the cases measured on a real engine, written down so that the next
/// machine's readings are interpreted the same way this one's were.
class FpsLogTest {

    @Test
    @DisplayName("frame callbacks arriving with the rendering update is healthy")
    void healthy() {
        var log = new FpsLog();
        log.add(60, 60, 240, true);
        log.add(59, 60, 238, true);
        log.add(60, 61, 240, true);
        assertEquals(Verdict.HEALTHY, log.verdict().orElseThrow());
    }

    @Test
    @DisplayName("a timeline that advances while raf does not blames the engine, not the pump")
    void rafStarvedIsTheEnginesFault() {
        // The reading measured on WebKitGTK 2.52.6: the context is drained, the
        // engine composites, and the page's callbacks never arrive (ADR-0455).
        var log = new FpsLog();
        log.add(1, 63, 125, true);
        log.add(1, 63, 125, true);
        log.add(2, 64, 125, true);
        assertEquals(Verdict.RAF_STARVED, log.verdict().orElseThrow());
    }

    @Test
    @DisplayName("a timeline that does not advance is a stalled engine")
    void renderingStalled() {
        var log = new FpsLog();
        log.add(0, 0, 125, true);
        log.add(1, 1, 125, true);
        log.add(0, 0, 125, true);
        assertEquals(Verdict.RENDERING_STALLED, log.verdict().orElseThrow());
    }

    @Test
    @DisplayName("timers that do not run are the embedder's fault, and outrank the rest")
    void contextStarvedWins() {
        var log = new FpsLog();
        log.add(0, 0, 1, true);
        log.add(0, 0, 2, true);
        log.add(0, 0, 1, true);
        assertEquals(Verdict.CONTEXT_STARVED, log.verdict().orElseThrow());
    }

    @Test
    @DisplayName("the worst second decides, because one starved second is still starved")
    void theWorstReadingWins() {
        var log = new FpsLog();
        log.add(60, 60, 240, true);
        log.add(60, 60, 240, true);
        log.add(60, 60, 240, true);
        log.add(0, 0, 1, true);
        assertEquals(Verdict.CONTEXT_STARVED, log.verdict().orElseThrow());
    }

    @Test
    @DisplayName("the first reading is dropped, because it measures the page's load")
    void theOpeningSecondIsNotTheAnimation() {
        var log = new FpsLog();
        log.add(2, 3, 30, true);
        log.add(60, 60, 240, true);
        log.add(60, 60, 240, true);
        assertEquals(3, log.readings().size());
        assertEquals(2, log.settled().size());
        assertEquals(60, log.medianRaf().orElseThrow());
        assertEquals(Verdict.HEALTHY, log.verdict().orElseThrow());
    }

    @Test
    @DisplayName("a run that measured nothing is empty, not zero")
    void nothingMeasuredIsNotZeroFps() {
        var log = new FpsLog();
        assertTrue(log.medianRaf().isEmpty());
        assertTrue(log.verdict().isEmpty());
        assertTrue(log.describe().contains("too few to summarize"));

        // One reading is still nothing, because the first is the one dropped.
        log.add(60, 60, 240, true);
        assertTrue(log.medianRaf().isEmpty());
        assertTrue(log.describe().contains("too few to summarize"));
    }

    @Test
    @DisplayName("the median of an even number of readings is the middle pair's mean")
    void evenMedian() {
        var log = new FpsLog();
        log.add(0, 0, 0, true);
        log.add(10, 60, 240, true);
        log.add(20, 60, 240, true);
        log.add(30, 60, 240, true);
        log.add(40, 60, 240, true);
        assertEquals(25, log.medianRaf().orElseThrow());
    }

    @Test
    @DisplayName("a page that called itself hidden says so in the summary")
    void hiddenIsReported() {
        var log = new FpsLog();
        log.add(60, 60, 240, true);
        log.add(1, 1, 125, false);
        log.add(1, 1, 125, false);
        var described = log.describe();
        assertTrue(described.contains("hidden in 2"), described);

        var visible = new FpsLog();
        visible.add(60, 60, 240, true);
        visible.add(60, 60, 240, true);
        assertFalse(visible.describe().contains("hidden"), visible.describe());
    }

    @Test
    @DisplayName("the summary names all three clocks, because one of them is never the story")
    void describeCarriesEveryClock() {
        var log = new FpsLog();
        log.add(0, 0, 0, true);
        log.add(1, 63, 125, true);
        log.add(1, 63, 125, true);
        var described = log.describe();
        assertTrue(described.contains("raf/s"), described);
        assertTrue(described.contains("timeline/s"), described);
        assertTrue(described.contains("timer/s"), described);
        assertTrue(described.contains("RAF_STARVED"), described);
    }
}
