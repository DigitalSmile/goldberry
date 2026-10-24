package dev.goldberry.media.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.Chapter;
import dev.goldberry.media.codec.MediaType;

/// Every container that states chapters, on the same three: `Intro` at 0,
/// `Middle` at 0.3 s and `Coda` at 0.7 s, in a tone of one second. What
/// differs between the files is only how each container writes them.
@DisplayName("Chapters in every container")
class ContainerChaptersTest {

    private static final List<String> TITLES = List.of("Intro", "Middle", "Coda");
    private static final List<Duration> STARTS = List.of(Duration.ZERO, Duration.ofMillis(300), Duration.ofMillis(700));

    /// The encoders round the end of a one-second tone their own ways: Opus by
    /// its pre-roll, AAC by its frames.
    private static final Duration END_SLACK = Duration.ofMillis(50);

    private static List<Chapter> chapters(String name) {
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            return container.info().chapters();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "tone-chapters.mkv",
                "tone-chapters.m4a",
                "tone-chapters.mp3",
                "tone-chapters.opus",
                "tone-chapters.flac"
            })
    @DisplayName("each states three chapters, their starts, titles and ends")
    void threeChapters(String name) {
        var chapters = chapters(name);
        assertEquals(3, chapters.size(), name + ": " + chapters);
        for (var i = 0; i < 3; i++) {
            var chapter = chapters.get(i);
            assertEquals(Optional.of(TITLES.get(i)), chapter.title(), name + ": title " + i);
            assertEquals(STARTS.get(i), chapter.start(), name + ": start " + i);
        }
        assertEquals(Optional.of(Duration.ofMillis(300)), chapters.get(0).end(), name + ": end of the first");
        assertEquals(Optional.of(Duration.ofMillis(700)), chapters.get(1).end(), name + ": end of the second");
        var last = chapters.get(2).end().orElseThrow();
        assertTrue(
                last.minus(Duration.ofSeconds(1)).abs().compareTo(END_SLACK) <= 0,
                name + ": the last ends with the tone, not at " + last);
    }

    @Test
    @DisplayName("an MP4's chapter track is chapters, not a subtitle track")
    void chapterTrackIsNotATrack() {
        try (var container =
                Containers.open(ClipFixtures.source("tone-chapters.m4a"), ClipFixtures.io("tone-chapters.m4a"))) {
            var info = container.info();
            assertEquals(1, info.tracks().size(), "tracks: " + info.tracks());
            assertEquals(List.of(), info.tracks(MediaType.SUBTITLE));
        }
    }

    @Test
    @DisplayName("a file that states none has none")
    void none() {
        assertEquals(List.of(), chapters("tone-flac.mkv"));
        assertEquals(List.of(), chapters("tone.mp3"));
        assertEquals(List.of(), chapters("tone.opus"));
    }

    @Test
    @DisplayName("the chapter playing is the one a position is in")
    void chapterAt() {
        try (var container =
                Containers.open(ClipFixtures.source("tone-chapters.mkv"), ClipFixtures.io("tone-chapters.mkv"))) {
            var info = container.info();
            assertEquals(Optional.of("Intro"), info.chapterAt(Duration.ZERO).flatMap(Chapter::title));
            assertEquals(
                    Optional.of("Middle"),
                    info.chapterAt(Duration.ofMillis(300)).flatMap(Chapter::title));
            assertEquals(
                    Optional.of("Middle"),
                    info.chapterAt(Duration.ofMillis(699)).flatMap(Chapter::title));
            assertEquals(
                    Optional.of("Coda"), info.chapterAt(Duration.ofMillis(999)).flatMap(Chapter::title));
            assertEquals(Optional.empty(), info.chapterAt(Duration.ofSeconds(2)));
        }
    }
}
