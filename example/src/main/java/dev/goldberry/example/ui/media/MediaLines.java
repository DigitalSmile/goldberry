package dev.goldberry.example.ui.media;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.SubtitleSource;
import dev.goldberry.media.TimeRange;
import dev.goldberry.media.Track;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.view.MediaTime;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// The small pieces the media cards are made of: a labelled line, a row of
/// buttons, a caption, and the way a speed, a track and a stretch of time are
/// written.
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
public final class MediaLines {

    private MediaLines() {}

    /// A label and its value, on one line.
    static Widget line(String label, String value) {
        return new Row(
                List.of(
                        new Text(label, Attributes.NONE.classes("caption", "media-label")),
                        new Text(value, Attributes.NONE.classes("media-value"))),
                Attributes.NONE.classes("media-line"));
    }

    /// Buttons that belong together, wrapping when the card is narrow.
    static Widget actions(List<? extends Widget> buttons) {
        return new Row(List.copyOf(buttons), Attributes.NONE.classes("media-actions"));
    }

    /// A line of secondary text.
    static Text caption(String text) {
        return new Text(text, Attributes.NONE.classes("caption"));
    }

    /// A speed as the buttons and the Status card write it: `0.5×`, `2×`.
    public static String speedLabel(float speed) {
        return new BigDecimal(Float.toString(speed)).stripTrailingZeros().toPlainString() + "×";
    }

    /// A track as the Status card names it: `#2 opus "Concert pitch" (eng)`.
    public static String trackName(Track track) {
        return "#" + track.index() + " " + track.codecName()
                + track.title().map(title -> " \"" + title + "\"").orElse("")
                + track.language().map(language -> " (" + language + ")").orElse("");
    }

    /// A track as the Tracks card lists it: its index, type, codec, shape and
    /// flags.
    static String describe(Track track) {
        var details =
                switch (track.params()) {
                    case TrackParams.Audio audio -> audio.sampleRate() + " Hz, " + audio.channels() + " ch";
                    case TrackParams.Video video -> video.width() + "×" + video.height();
                    case TrackParams.Subtitle _ -> "subtitles";
                    case TrackParams.Other other -> other.type().name().toLowerCase(Locale.ROOT);
                };
        var flags = new ArrayList<String>();
        if (track.isDefault()) {
            flags.add("default");
        }
        if (track.attachedPicture()) {
            flags.add("cover art");
        }
        return "#" + track.index() + "  " + track.type().name().toLowerCase(Locale.ROOT) + "  "
                + track.codecName() + "  " + details
                + (flags.isEmpty() ? "" : "  (" + String.join(", ", flags) + ")");
    }

    /// The subtitles showing: a track, a file, or `off`.
    static String subtitlesName(PlayerStatus status) {
        return switch (status.subtitles().orElse(null)) {
            case SubtitleSource.Embedded(var track) -> "track " + trackName(track);
            case SubtitleSource.External(var file) ->
                "file " + file.fileName().orElse(file.uri().toString());
            case null -> "off";
        };
    }

    /// A duration in seconds, to a tenth.
    static String seconds(Duration duration) {
        return String.format(Locale.ROOT, "%.1f s", duration.toMillis() / 1000.0);
    }

    /// The fetched stretches, as `0:00–0:04, 0:06–0:08`.
    static String ranges(List<TimeRange> ranges) {
        return ranges.stream()
                .map(range -> MediaTime.format(range.start()) + "–" + MediaTime.format(range.end()))
                .collect(Collectors.joining(", "));
    }
}
