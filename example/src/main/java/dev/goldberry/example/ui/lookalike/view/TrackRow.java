package dev.goldberry.example.ui.lookalike.view;

import java.util.List;

import dev.goldberry.example.ui.lookalike.library.Times;
import dev.goldberry.example.ui.lookalike.library.Track;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;

/// One line of a playlist's table: number, title and artist, album, heart, length.
///
/// The heart is a pressable **beside** the row's own rather than inside it, so a
/// press on it likes the song and does not also start it.
///
/// @param number  where the track is in the list, from 1
/// @param track   the song
/// @param state   whether it is the current track, and whether that is playing
/// @param liked   whether it has a heart
/// @param play    what a press on the row does
/// @param like    what a press on the heart does
record TrackRow(int number, Track track, Now state, boolean liked, Runnable play, Runnable like)
        implements Widget.Stateless {

    /// Where this row stands against the player.
    enum Now {
        /// Some other track is current.
        IDLE,
        /// This track is current and paused.
        PAUSED,
        /// This track is current and playing.
        PLAYING
    }

    @Override
    public Widget build(BuildContext context) {
        var current = state != Now.IDLE;
        Widget lead = state == Now.PLAYING
                ? Parts.icon("audio-lines", "now-icon")
                : Parts.text(Integer.toString(number), Parts.classes(current, "now", "track-number"));
        var title = Parts.column(
                List.of(
                        Parts.text(track.title(), Parts.classes(current, "now", "track-title", "one-line")),
                        Parts.text(track.artist(), "track-artist", "one-line")),
                "track-name");
        var body = new Pressable(
                        "Play " + track.title() + " by " + track.artist(),
                        play,
                        false,
                        List.of(Parts.row(
                                List.of(
                                        Parts.row(List.of(lead), "track-lead"),
                                        title,
                                        Parts.text(track.album(), "track-album", "one-line")),
                                "track-body-row")),
                        Attributes.NONE.classes("track-body"))
                .id("track-" + number);
        var heart = Parts.iconButton(
                liked ? "Remove from Liked Songs" : "Save to Liked Songs",
                "heart",
                like,
                Parts.classes(liked, "liked", "icon-button", "track-heart"));
        return Parts.row(
                        List.of(body, heart, Parts.text(Times.clock(track.length()), "track-length")),
                        Parts.classes(current, "current", "track-row"))
                .keyed(track.id());
    }
}
