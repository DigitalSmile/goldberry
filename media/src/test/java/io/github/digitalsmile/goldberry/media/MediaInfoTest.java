package io.github.digitalsmile.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.codec.TrackParams;
import io.github.digitalsmile.goldberry.media.io.Source;

@DisplayName("MediaInfo")
class MediaInfoTest {

    private static final Source SOURCE = Source.of(URI.create("file:///clip.mkv"));

    private static Track video(int index, boolean isDefault, boolean picture) {
        return new Track(
                index,
                CodecId.VP9,
                "vp9",
                new TrackParams.Video(
                        640, 360, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty()),
                Optional.empty(),
                isDefault,
                picture);
    }

    private static Track audio(int index, boolean isDefault) {
        return new Track(
                index,
                CodecId.OPUS,
                "opus",
                new TrackParams.Audio(48_000, 2, Optional.empty(), OptionalInt.empty(), OptionalLong.empty()),
                Optional.of(Duration.ofSeconds(2)),
                isDefault,
                false);
    }

    @Test
    @DisplayName("filters tracks by kind, in container order")
    void byType() {
        var info = new MediaInfo(
                SOURCE,
                Optional.empty(),
                List.of(audio(0, false), video(1, false, false), audio(2, false)),
                true,
                false);
        assertEquals(
                List.of(0, 2),
                info.tracks(MediaType.AUDIO).stream().map(Track::index).toList());
        assertEquals(List.of(), info.tracks(MediaType.SUBTITLE));
    }

    @Test
    @DisplayName("starts with the track the container marks as default, else the first")
    void defaultTrack() {
        var marked = new MediaInfo(SOURCE, Optional.empty(), List.of(audio(0, false), audio(1, true)), true, false);
        assertEquals(1, marked.defaultTrack(MediaType.AUDIO).orElseThrow().index());
        var unmarked = new MediaInfo(SOURCE, Optional.empty(), List.of(audio(0, false), audio(1, false)), true, false);
        assertEquals(0, unmarked.defaultTrack(MediaType.AUDIO).orElseThrow().index());
        assertEquals(Optional.empty(), unmarked.defaultTrack(MediaType.VIDEO));
    }

    @Test
    @DisplayName("never starts with cover art as the video, and finds it as the attached picture")
    void coverArt() {
        var info = new MediaInfo(SOURCE, Optional.empty(), List.of(audio(0, true), video(1, true, true)), true, false);
        assertEquals(Optional.empty(), info.defaultTrack(MediaType.VIDEO));
        assertEquals(1, info.attachedPicture().orElseThrow().index());
    }

    @Test
    @DisplayName("keeps a copy of the track list")
    void copies() {
        var tracks = new ArrayList<Track>(List.of(audio(0, true)));
        var info = new MediaInfo(SOURCE, Optional.empty(), tracks, true, false);
        tracks.clear();
        assertEquals(1, info.tracks().size());
    }

    @Test
    @DisplayName("a track refuses a negative index, and reports its params' kind")
    void track() {
        assertThrows(IllegalArgumentException.class, () -> audio(-1, false));
        assertEquals(MediaType.VIDEO, video(0, false, false).type());
    }
}
