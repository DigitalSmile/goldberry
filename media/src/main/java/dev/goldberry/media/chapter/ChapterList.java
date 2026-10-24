package dev.goldberry.media.chapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.Chapter;

/// The arithmetic over a source's chapters: putting what a container stated in
/// order, finding the one playing, and the starts a player's next and previous
/// keys go to.
///
/// Every container states chapters its own way, and most leave something out:
/// Nero's `chpl` and Ogg's comments give only starts, MP4's chapter track gives
/// durations, Matroska and ID3 give both or either. [#settle] is where they all
/// become the same list.
public final class ChapterList {

    /// How far into a chapter "previous" goes back to that chapter's own start
    /// rather than to the one before it: a CD player's rule, which a listener a
    /// second into a chapter expects to mean "the one before".
    public static final Duration RESTART_AFTER = Duration.ofSeconds(3);

    private ChapterList() {}

    /// What a container stated, made into a [dev.goldberry.media.MediaInfo]'s
    /// list: in order of their starts, the second of two that start together
    /// dropped, those starting at or past `duration` dropped, and every end
    /// that was not stated, or was stated at or before its start, filled in from
    /// the next start or the presentation's end.
    public static List<Chapter> settle(List<Chapter> stated, Optional<Duration> duration) {
        var sorted = new ArrayList<>(stated);
        sorted.sort(Comparator.comparing(Chapter::start));
        var kept = new ArrayList<Chapter>(sorted.size());
        for (var chapter : sorted) {
            if (duration.isPresent()
                    && chapter.start().compareTo(duration.get()) >= 0
                    && !chapter.start().isZero()) {
                continue;
            }
            if (!kept.isEmpty() && kept.getLast().start().equals(chapter.start())) {
                continue;
            }
            kept.add(chapter);
        }
        var settled = new ArrayList<Chapter>(kept.size());
        for (var i = 0; i < kept.size(); i++) {
            var chapter = kept.get(i);
            var stop = chapter.end().filter(end -> end.compareTo(chapter.start()) > 0);
            if (stop.isEmpty()) {
                stop = i + 1 < kept.size() ? Optional.of(kept.get(i + 1).start()) : duration;
            }
            settled.add(new Chapter(chapter.start(), stop, chapter.title()));
        }
        return List.copyOf(settled);
    }

    /// The chapter playing at `position`: the last to start at or before it, when
    /// `position` is before that chapter's end.
    public static Optional<Chapter> at(List<Chapter> chapters, Duration position) {
        Chapter found = null;
        for (var chapter : chapters) {
            if (chapter.start().compareTo(position) > 0) {
                break;
            }
            found = chapter;
        }
        return Optional.ofNullable(found).filter(chapter -> chapter.contains(position));
    }

    /// Where "next chapter" goes from `position`: the first start after it. Empty
    /// in the last chapter, and with none.
    public static Optional<Duration> next(List<Chapter> chapters, Duration position) {
        return chapters.stream()
                .map(Chapter::start)
                .filter(start -> start.compareTo(position) > 0)
                .findFirst();
    }

    /// Where "previous chapter" goes from `position`: the start of the chapter
    /// playing, once it has played for [#RESTART_AFTER], else the start of the
    /// one before it, else the first chapter's. Empty with no chapters.
    public static Optional<Duration> previous(List<Chapter> chapters, Duration position) {
        if (chapters.isEmpty()) {
            return Optional.empty();
        }
        var current = -1;
        for (var i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).start().compareTo(position) <= 0) {
                current = i;
            }
        }
        if (current < 0) {
            return Optional.of(chapters.getFirst().start());
        }
        var start = chapters.get(current).start();
        if (position.minus(start).compareTo(RESTART_AFTER) >= 0 || current == 0) {
            return Optional.of(start);
        }
        return Optional.of(chapters.get(current - 1).start());
    }
}
