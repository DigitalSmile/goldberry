package dev.goldberry.media;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.media.chapter.ChapterList;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.io.Source;

/// What a source holds, as far as its container says without decoding anything.
///
/// @param source   the source that was probed
/// @param duration the whole presentation's length, when known. Empty for a live
///                 stream, or a file whose container does not say
/// @param tracks   every stream, in container order
/// @param seekable whether the source's bytes can be read out of order. Without
///                 that, seeking is impossible whatever the container allows
/// @param live     whether the source is live, such as an internet radio
///                 station: it has only a "now", and a player shows `LIVE` for it
///                 ([dev.goldberry.media.io.MediaIO#isLive()])
/// @param chapters the source's chapters in order of their starts, each with its
///                 end filled in where the container left it out; none when the
///                 container states none
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
public record MediaInfo(
        Source source,
        Optional<Duration> duration,
        List<Track> tracks,
        boolean seekable,
        boolean live,
        List<Chapter> chapters) {

    public MediaInfo {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(duration, "duration");
        tracks = List.copyOf(tracks);
        chapters = ChapterList.settle(chapters, duration);
    }

    /// A source with no chapters: every component but those.
    public MediaInfo(Source source, Optional<Duration> duration, List<Track> tracks, boolean seekable, boolean live) {
        this(source, duration, tracks, seekable, live, List.of());
    }

    /// The chapter playing at `position`, when the source has chapters and one
    /// covers it.
    public Optional<Chapter> chapterAt(Duration position) {
        return ChapterList.at(chapters, position);
    }

    /// The tracks of one kind, in container order.
    public List<Track> tracks(MediaType type) {
        return tracks.stream().filter(track -> track.type() == type).toList();
    }

    /// The track of `type` a player starts with: the one the container marks as
    /// default, else the first. Never an attached picture.
    public Optional<Track> defaultTrack(MediaType type) {
        var candidates =
                tracks(type).stream().filter(track -> !track.attachedPicture()).toList();
        return candidates.stream()
                .filter(Track::isDefault)
                .findFirst()
                .or(() -> candidates.stream().findFirst());
    }

    /// The cover art, when the source carries one.
    public Optional<Track> attachedPicture() {
        return tracks.stream().filter(Track::attachedPicture).findFirst();
    }
}
