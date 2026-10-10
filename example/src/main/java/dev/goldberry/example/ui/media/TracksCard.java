package dev.goldberry.example.ui.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.SubtitleSource;
import dev.goldberry.media.Track;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// The **Tracks** card: every track the probe found, with a button on each one
/// that can be chosen.
///
/// Read more:
/// [Tracks](https://goldberry.dev/docs/components/media.html#tracks-subtitles-and-the-network).
///
/// @param kind   which screen
/// @param player the screen's player, which a button switches
/// @param status what the player reported last
record TracksCard(MediaKind kind, MediaPlayer player, PlayerStatus status) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var lines = new ArrayList<Widget>();
        status.info()
                .ifPresentOrElse(
                        info -> {
                            for (var track : info.tracks()) {
                                lines.add(trackLine(track));
                            }
                            if (info.tracks().isEmpty()) {
                                lines.add(MediaLines.caption("The container holds no tracks."));
                            }
                        },
                        () -> lines.add(MediaLines.caption("Nothing open yet.")));
        var summary = switch (kind) {
            case AUDIO ->
                "The probe lists every track in a source, playable or not. Pick Two voices, one file and"
                        + " switch between them here or in the player's menu; the position is kept.";
            case VIDEO ->
                "The probe lists every track in a source, playable or not. Pick Two angles, two voices and"
                        + " switch here or in the player's menus; a new angle comes in on the picture that"
                        + " covers the position.";
        };
        return new ShowcaseCard(kind.id("tracks"), "Tracks", summary, MediaDocs.TRACKS).of(lines);
    }

    /// One track, and a button that plays or shows it where it can be chosen: an
    /// audio or video track when the source has two or more, and any subtitle
    /// track.
    private Widget trackLine(Track track) {
        var text = new Text(MediaLines.describe(track), Attributes.NONE.classes("mono"));
        var info = status.info().orElseThrow();
        var choosable = status.state().hasMedia()
                && switch (track.type()) {
                    case AUDIO -> info.tracks(MediaType.AUDIO).size() > 1;
                    case VIDEO ->
                        !track.attachedPicture()
                                && info.tracks(MediaType.VIDEO).stream()
                                                .filter(other -> !other.attachedPicture())
                                                .count()
                                        > 1;
                    case SUBTITLE -> true;
                    case ATTACHMENT, DATA -> false;
                };
        if (!choosable) {
            return text;
        }
        var chosen = switch (track.type()) {
            case AUDIO -> status.audioTrack().equals(Optional.of(track));
            case VIDEO -> status.videoTrack().equals(Optional.of(track));
            default ->
                status.subtitles()
                        .filter(SubtitleSource.Embedded.class::isInstance)
                        .map(SubtitleSource.Embedded.class::cast)
                        .filter(embedded -> embedded.track().equals(track))
                        .isPresent();
        };
        var verb = track.type() == MediaType.SUBTITLE ? (chosen ? "Showing" : "Show") : (chosen ? "Playing" : "Play");
        return new Row(
                List.of(
                        new Button(verb, () -> player.selectTrack(track))
                                .disabled(chosen)
                                .id(kind.id("track-" + track.index())),
                        text),
                Attributes.NONE.classes("media-line"));
    }
}
