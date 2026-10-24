package dev.goldberry.media.container.matroska;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.Chapter;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.container.ebml.EbmlReader;
import dev.goldberry.media.container.ebml.EbmlReader.Element;

/// A Matroska `Chapters` element: the editions in it, and the chapter atoms of
/// the one a player shows.
///
/// The edition is the one flagged default, else the first that is not
/// hidden, else the first. Its top-level atoms are the chapters; a nested atom
/// is a sub-chapter, which a seek bar does not mark. An atom flagged hidden or
/// not enabled is left out. Times are nanoseconds whatever the
/// `TimestampScale`; the title is the atom's first `ChapterDisplay`. An
/// ordered edition's atoms are listed by their times as any other's, though
/// a player plays the file in its own order.
final class MatroskaChapters {

    private record Edition(boolean isDefault, boolean hidden, List<Chapter> chapters) {}

    private MatroskaChapters() {}

    /// The chapters of the edition a player shows, read from `chapters`, the
    /// element whose header was just read.
    static List<Chapter> read(EbmlReader ebml, ByteReader in, Element chapters) {
        var editions = new ArrayList<Edition>();
        var end = chapters.dataEnd();
        while (in.position() < end) {
            var element = ebml.readHeader();
            if (element.id() == MatroskaIds.EDITION_ENTRY) {
                editions.add(edition(ebml, in, element));
            } else {
                ebml.skip(element);
            }
        }
        return editions.stream()
                .filter(Edition::isDefault)
                .findFirst()
                .or(() -> editions.stream().filter(edition -> !edition.hidden()).findFirst())
                .or(() -> editions.stream().findFirst())
                .map(Edition::chapters)
                .orElse(List.of());
    }

    private static Edition edition(EbmlReader ebml, ByteReader in, Element edition) {
        var isDefault = false;
        var hidden = false;
        var chapters = new ArrayList<Chapter>();
        var end = edition.dataEnd();
        while (in.position() < end) {
            var child = ebml.readHeader();
            if (child.id() == MatroskaIds.EDITION_FLAG_DEFAULT) {
                isDefault = ebml.readUnsigned(child) != 0;
            } else if (child.id() == MatroskaIds.EDITION_FLAG_HIDDEN) {
                hidden = ebml.readUnsigned(child) != 0;
            } else if (child.id() == MatroskaIds.CHAPTER_ATOM) {
                atom(ebml, in, child).ifPresent(chapters::add);
            } else {
                ebml.skip(child);
            }
        }
        return new Edition(isDefault, hidden, List.copyOf(chapters));
    }

    private static Optional<Chapter> atom(EbmlReader ebml, ByteReader in, Element atom) {
        long start = -1;
        long stop = -1;
        var shown = true;
        Optional<String> title = Optional.empty();
        var end = atom.dataEnd();
        while (in.position() < end) {
            var child = ebml.readHeader();
            if (child.id() == MatroskaIds.CHAPTER_TIME_START) {
                start = ebml.readUnsigned(child);
            } else if (child.id() == MatroskaIds.CHAPTER_TIME_END) {
                stop = ebml.readUnsigned(child);
            } else if (child.id() == MatroskaIds.CHAPTER_FLAG_HIDDEN) {
                shown &= ebml.readUnsigned(child) == 0;
            } else if (child.id() == MatroskaIds.CHAPTER_FLAG_ENABLED) {
                shown &= ebml.readUnsigned(child) != 0;
            } else if (child.id() == MatroskaIds.CHAPTER_DISPLAY && title.isEmpty()) {
                title = display(ebml, in, child);
            } else {
                ebml.skip(child);
            }
        }
        if (start < 0 || !shown) {
            return Optional.empty();
        }
        var from = Duration.ofNanos(start);
        var to = stop > start ? Optional.of(Duration.ofNanos(stop)) : Optional.<Duration>empty();
        return Optional.of(new Chapter(from, to, title));
    }

    private static Optional<String> display(EbmlReader ebml, ByteReader in, Element display) {
        Optional<String> text = Optional.empty();
        var end = display.dataEnd();
        while (in.position() < end) {
            var child = ebml.readHeader();
            if (child.id() == MatroskaIds.CHAP_STRING && text.isEmpty()) {
                text = Optional.of(ebml.readString(child));
            } else {
                ebml.skip(child);
            }
        }
        return text;
    }
}
