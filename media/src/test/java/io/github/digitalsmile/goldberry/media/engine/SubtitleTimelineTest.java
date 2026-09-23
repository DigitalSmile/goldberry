package io.github.digitalsmile.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.subtitle.Cue;

/// The cues held for the subtitles showing: what shows when, overlaps, a cue
/// long enough to start well before the ones around it, and a cue read twice.
@DisplayName("SubtitleTimeline")
class SubtitleTimelineTest {

    private static Cue cue(long from, long to, String text) {
        return new Cue(Duration.ofMillis(from), Duration.ofMillis(to), text);
    }

    private static List<String> at(SubtitleTimeline timeline, long millis) {
        return timeline.showing(Duration.ofMillis(millis)).stream()
                .map(Cue::text)
                .toList();
    }

    @Test
    @DisplayName("shows what covers the position, overlaps in the order they start, and nothing in a gap")
    void showing() {
        var timeline = new SubtitleTimeline();
        timeline.add(cue(1_000, 3_000, "one"));
        timeline.add(cue(2_000, 4_000, "two"));
        timeline.add(cue(6_000, 7_000, "three"));
        assertEquals(List.of(), at(timeline, 999));
        assertEquals(List.of("one"), at(timeline, 1_000));
        assertEquals(List.of("one", "two"), at(timeline, 2_500));
        assertEquals(List.of("two"), at(timeline, 3_000));
        assertEquals(List.of(), at(timeline, 5_000));
        assertEquals(List.of("three"), at(timeline, 6_999));
    }

    @Test
    @DisplayName("finds a long cue that started long before the short ones around it")
    void longCue() {
        var timeline = new SubtitleTimeline();
        timeline.add(cue(0, 60_000, "a sign on the wall"));
        for (var i = 1; i < 50; i++) {
            timeline.add(cue(i * 1_000, i * 1_000 + 500, "line " + i));
        }
        assertEquals(List.of("a sign on the wall", "line 30"), at(timeline, 30_200));
        assertEquals(List.of("a sign on the wall"), at(timeline, 30_700));
    }

    @Test
    @DisplayName("keeps a cue read twice once, and replaces or clears everything at once")
    void dedupeAndReplace() {
        var timeline = new SubtitleTimeline();
        timeline.add(cue(0, 1_000, "x"));
        timeline.add(cue(0, 1_000, "x"));
        timeline.add(cue(0, 1_000, "y"));
        assertEquals(2, timeline.size());
        timeline.replace(List.of(cue(5_000, 6_000, "z")));
        assertEquals(List.of(), at(timeline, 500));
        assertEquals(List.of("z"), at(timeline, 5_500));
        timeline.clear();
        assertEquals(0, timeline.size());
    }
}
