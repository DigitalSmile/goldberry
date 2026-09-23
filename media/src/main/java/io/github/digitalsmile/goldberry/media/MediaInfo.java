package io.github.digitalsmile.goldberry.media;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.io.Source;

/// What a source holds, as far as its container says without decoding anything.
///
/// @param source   the source that was probed
/// @param duration the whole presentation's length, when known. Empty for a live
///                 stream, or a file whose container does not say
/// @param tracks   every stream, in container order
/// @param seekable whether the source's bytes can be read out of order. Without
///                 that, seeking is impossible whatever the container allows
public record MediaInfo(Source source, Optional<Duration> duration, List<Track> tracks, boolean seekable) {

    public MediaInfo {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(duration, "duration");
        tracks = List.copyOf(tracks);
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
