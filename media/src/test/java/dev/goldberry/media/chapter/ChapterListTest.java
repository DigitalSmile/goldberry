package dev.goldberry.media.chapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.Chapter;

@DisplayName("ChapterList")
class ChapterListTest {

    private static Duration s(double seconds) {
        return Duration.ofMillis(Math.round(seconds * 1000));
    }

    private static Chapter at(double seconds, String title) {
        return new Chapter(s(seconds), title);
    }

    private static final List<Chapter> THREE =
            ChapterList.settle(List.of(at(0, "One"), at(60, "Two"), at(120, "Three")), Optional.of(s(180)));

    @Nested
    @DisplayName("settle")
    class Settle {

        @Test
        @DisplayName("fills each unstated end from the next start, the last from the duration")
        void ends() {
            assertEquals(
                    List.of(
                            new Chapter(s(0), Optional.of(s(60)), Optional.of("One")),
                            new Chapter(s(60), Optional.of(s(120)), Optional.of("Two")),
                            new Chapter(s(120), Optional.of(s(180)), Optional.of("Three"))),
                    THREE);
        }

        @Test
        @DisplayName("keeps an end that was stated, and leaves the last open with no duration")
        void statedEnds() {
            var settled = ChapterList.settle(
                    List.of(new Chapter(s(0), Optional.of(s(10)), Optional.empty()), at(30, "Open")), Optional.empty());
            assertEquals(Optional.of(s(10)), settled.get(0).end());
            assertEquals(Optional.empty(), settled.get(1).end());
        }

        @Test
        @DisplayName("sorts by start, drops the second of two that start together and those past the end")
        void orderAndDuplicates() {
            var settled = ChapterList.settle(
                    List.of(at(20, "Late"), at(0, "First"), at(20, "Again"), at(500, "Beyond")), Optional.of(s(100)));
            assertEquals(
                    List.of(Optional.of("First"), Optional.of("Late")),
                    settled.stream().map(Chapter::title).toList());
            assertEquals(Optional.of(s(100)), settled.getLast().end());
        }

        @Test
        @DisplayName("is idempotent, so a MediaInfo built from a settled list keeps it")
        void idempotent() {
            assertEquals(THREE, ChapterList.settle(THREE, Optional.of(s(180))));
        }
    }

    @Test
    @DisplayName("at: the chapter a position is in, none past the last end")
    void chapterAt() {
        assertEquals(Optional.of("One"), ChapterList.at(THREE, s(0)).flatMap(Chapter::title));
        assertEquals(Optional.of("One"), ChapterList.at(THREE, s(59.999)).flatMap(Chapter::title));
        assertEquals(Optional.of("Two"), ChapterList.at(THREE, s(60)).flatMap(Chapter::title));
        assertEquals(Optional.empty(), ChapterList.at(THREE, s(180)));
        assertEquals(Optional.empty(), ChapterList.at(List.of(), s(1)));
    }

    @Test
    @DisplayName("at: none before a first chapter that starts late")
    void beforeTheFirst() {
        var late = ChapterList.settle(List.of(at(10, "Late")), Optional.of(s(20)));
        assertEquals(Optional.empty(), ChapterList.at(late, s(5)));
    }

    @Test
    @DisplayName("next: the first start after the position, none in the last chapter")
    void next() {
        assertEquals(Optional.of(s(60)), ChapterList.next(THREE, s(0)));
        assertEquals(Optional.of(s(120)), ChapterList.next(THREE, s(60)));
        assertEquals(Optional.empty(), ChapterList.next(THREE, s(150)));
        assertEquals(Optional.empty(), ChapterList.next(List.of(), s(0)));
    }

    @Test
    @DisplayName("previous: this chapter's start once it has played a while, else the one before")
    void previous() {
        assertEquals(Optional.of(s(60)), ChapterList.previous(THREE, s(90)), "well into Two: back to its start");
        assertEquals(Optional.of(s(0)), ChapterList.previous(THREE, s(61)), "just into Two: back to One");
        assertEquals(
                Optional.of(s(60)),
                ChapterList.previous(THREE, s(60).plus(ChapterList.RESTART_AFTER)),
                "exactly the restart time in: this chapter");
        assertEquals(Optional.of(s(0)), ChapterList.previous(THREE, s(1)), "in the first: its own start");
        assertEquals(Optional.empty(), ChapterList.previous(List.of(), s(1)));
    }

    @Test
    @DisplayName("a chapter refuses to end before it starts, and a blank title is none")
    void chapterRecord() {
        assertThrows(IllegalArgumentException.class, () -> new Chapter(s(2), Optional.of(s(1)), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new Chapter(s(-1), "Before"));
        assertEquals(Optional.empty(), new Chapter(s(0), "  ").title());
    }
}
