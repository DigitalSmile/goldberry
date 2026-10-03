package dev.goldberry.example.ui.media;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.media.ShowcaseMedia;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.controls.button.Button;

/// The **Subtitles** card: what shows, the cues at the clock, and the bundled and
/// chosen files a reader can load over a source.
///
/// Read more:
/// [Subtitles](https://goldberry.dev/docs/components/media.html#tracks-subtitles-and-the-network).
///
/// @param kind    which screen
/// @param player  the screen's player, which Hide turns off
/// @param status  what the player reported last
/// @param cues    the cues showing at the clock, joined, or empty
/// @param note    how the last load went, or empty
/// @param dialogs whether this window can ask for a file
/// @param desk    what a press asks for
record SubtitlesCard(
        MediaKind kind,
        MediaPlayer player,
        PlayerStatus status,
        String cues,
        String note,
        boolean dialogs,
        MediaDesk desk)
        implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var open = status.state().hasMedia();
        var files = ShowcaseMedia.SUBTITLE_FILES.stream()
                .map(file -> (Widget) new Button("Load " + file.title(), () -> desk.loadSubtitles(file))
                        .disabled(!open)
                        .id(kind.id("subtitles-" + file.key())))
                .toList();
        var lines = new ArrayList<Widget>(List.of(
                MediaLines.line("Showing", MediaLines.subtitlesName(status)),
                MediaLines.line("Now", cues.isEmpty() ? "—" : cues),
                MediaLines.actions(files),
                MediaLines.actions(List.of(
                        new Button("Load a file…", desk::openSubtitleFile)
                                .disabled(!open || !dialogs)
                                .id(kind.id("subtitles-file")),
                        new Button("Hide", player::hideSubtitles)
                                .disabled(status.subtitles().isEmpty())
                                .id(kind.id("subtitles-hide"))))));
        if (!note.isEmpty()) {
            lines.add(MediaLines.caption(note).id(kind.id("subtitles-note")));
        }
        return new ShowcaseCard(
                        kind.id("subtitles"),
                        "Subtitles",
                        "Text subtitles are read in Java from a SubRip, WebVTT, ASS or MP4 text track, or from a"
                                + " SubRip or WebVTT file. Pick the subtitled clip, then show a track or load a"
                                + " file over it.",
                        MediaDocs.TRACKS)
                .of(lines);
    }
}
