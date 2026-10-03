package dev.goldberry.example.ui.media;

import java.util.ArrayList;
import java.util.Locale;

import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.view.MediaTime;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Status** card: the player's last `PlayerStatus`, line by line.
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
///
/// @param kind   which screen; the Video screen adds the subtitles line
/// @param status what the player reported last
record StatusCard(MediaKind kind, PlayerStatus status) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var position = MediaTime.format(status.position())
                + status.duration().map(d -> " of " + MediaTime.format(d)).orElse("");
        var lines = new ArrayList<Widget>();
        lines.add(MediaLines.line("State", status.state().name().toLowerCase(Locale.ROOT)));
        lines.add(MediaLines.line("Position", position));
        lines.add(MediaLines.line("Audio", status.audioDecoder().orElse("—")));
        lines.add(MediaLines.line("Video", status.videoDecoder().orElse("—")));
        lines.add(MediaLines.line(
                "Seekable", status.state().hasMedia() ? (status.seekable() ? "yes" : "no: live") : "—"));
        lines.add(
                MediaLines.line("Volume", Math.round(status.volume() * 100) + "%" + (status.muted() ? ", muted" : "")));
        lines.add(MediaLines.line("Speed", MediaLines.speedLabel(status.rate())));
        status.audioTrack().ifPresent(track -> lines.add(MediaLines.line("Audio track", MediaLines.trackName(track))));
        status.videoTrack().ifPresent(track -> lines.add(MediaLines.line("Video track", MediaLines.trackName(track))));
        if (kind == MediaKind.VIDEO) {
            lines.add(MediaLines.line("Subtitles", MediaLines.subtitlesName(status)));
        }
        if (status.state().hasMedia() && !status.bufferedRanges().isEmpty()) {
            lines.add(MediaLines.line(
                    "Ahead", MediaLines.seconds(status.bufferedAhead()) + " demuxed past the position"));
            lines.add(MediaLines.line("Fetched", MediaLines.ranges(status.bufferedRanges())));
        }
        status.nowPlaying().ifPresent(title -> lines.add(MediaLines.line("Now playing", title)));
        status.error().ifPresent(error -> lines.add(MediaLines.line("Error", error.message())));
        return new ShowcaseCard(
                        kind.id("status"),
                        "Status",
                        "A player reports one immutable PlayerStatus: the state, the position, the decoders, the"
                                + " volume, the speed and what has buffered. It is pushed on each change and read"
                                + " for the position while playing.",
                        MediaDocs.PLAYER)
                .of(lines);
    }
}
