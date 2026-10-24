package dev.goldberry.media.chapter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import dev.goldberry.media.Chapter;

/// A Vorbis comment block (Vorbis I §5), the tags of Ogg Opus, Vorbis and FLAC,
/// and the chapters written in it.
///
/// The block is a vendor string and a list of `NAME=value` comments, every
/// length a little-endian 32-bit count. Chapters are the Xiph convention
/// `CHAPTER001=00:01:30.250` and `CHAPTER001NAME=…`: the number pairs a start
/// with its title, and the comments may come in any order. Nothing states an
/// end, so [ChapterList#settle] gives each the next one's start.
public final class VorbisComments {

    /// `CHAPTERnnn` and `CHAPTERnnnNAME`, any case, any count of digits.
    private static final Pattern CHAPTER = Pattern.compile("CHAPTER(\\d+)(NAME)?", Pattern.CASE_INSENSITIVE);

    /// `HH:MM:SS`, `MM:SS` or `SS`, each with an optional fraction.
    private static final Pattern TIME = Pattern.compile("(?:(?:(\\d+):)?(\\d+):)?(\\d+)(?:\\.(\\d{1,9}))?");

    private VorbisComments() {}

    /// The comments of the block that starts at `offset` in `data`. As many as
    /// were whole: a block cut short gives the comments before the cut, and one
    /// whose counts are past its end gives none.
    public static List<String> parse(byte[] data, int offset) {
        var comments = new ArrayList<String>();
        var at = offset;
        var vendor = u32(data, at);
        if (vendor < 0 || at + 4 + vendor + 4 > data.length) {
            return List.of();
        }
        at += 4 + (int) vendor;
        var count = u32(data, at);
        at += 4;
        for (long i = 0; i < count && at + 4 <= data.length; i++) {
            var length = u32(data, at);
            at += 4;
            if (length < 0 || length > data.length - at) {
                break;
            }
            comments.add(new String(data, at, (int) length, StandardCharsets.UTF_8));
            at += (int) length;
        }
        return List.copyOf(comments);
    }

    /// The chapters `comments` state, in order of their numbers. A start that
    /// does not parse drops its chapter; a title with no start is ignored.
    public static List<Chapter> chapters(List<String> comments) {
        var starts = new TreeMap<Integer, Duration>();
        var titles = new TreeMap<Integer, String>();
        for (var comment : comments) {
            var equals = comment.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            var key = CHAPTER.matcher(comment.substring(0, equals));
            if (!key.matches() || key.group(1).length() > 6) {
                continue;
            }
            var number = Integer.parseInt(key.group(1));
            var value = comment.substring(equals + 1).strip();
            if (key.group(2) != null) {
                titles.putIfAbsent(number, value);
            } else {
                time(value).ifPresent(start -> starts.putIfAbsent(number, start));
            }
        }
        var chapters = new ArrayList<Chapter>(starts.size());
        for (var entry : starts.entrySet()) {
            chapters.add(
                    new Chapter(entry.getValue(), Optional.empty(), Optional.ofNullable(titles.get(entry.getKey()))));
        }
        return List.copyOf(chapters);
    }

    /// A Xiph chapter time as a duration, or empty when it is not one.
    static Optional<Duration> time(String text) {
        var matcher = TIME.matcher(text.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            var hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
            var minutes = matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2));
            var seconds = Long.parseLong(matcher.group(3));
            var fraction = matcher.group(4) == null ? "0" : matcher.group(4);
            var nanos = Long.parseLong((fraction + "000000000").substring(0, 9));
            return Optional.of(Duration.ofHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds)
                    .plusNanos(nanos));
        } catch (NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }

    private static long u32(byte[] data, int at) {
        if (at < 0 || at + 4 > data.length) {
            return -1;
        }
        return (data[at] & 0xFFL)
                | (data[at + 1] & 0xFFL) << 8
                | (data[at + 2] & 0xFFL) << 16
                | (data[at + 3] & 0xFFL) << 24;
    }
}
