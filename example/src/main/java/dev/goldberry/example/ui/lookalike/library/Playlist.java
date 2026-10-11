package dev.goldberry.example.ui.lookalike.library;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/// A named list of tracks.
///
/// @param id          what the sidebar and the queue remember it by
/// @param name        its title
/// @param kind        what the sidebar says under the name: `Playlist`, `Album`
/// @param owner       who made it
/// @param description a line under the title
/// @param cover       its picture
/// @param tracks      the songs, in order
public record Playlist(
        String id, String name, String kind, String owner, String description, Cover cover, List<Track> tracks) {

    public Playlist {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(cover, "cover");
        tracks = List.copyOf(tracks);
    }

    /// This playlist holding `songs` instead.
    public Playlist withTracks(List<Track> songs) {
        return new Playlist(id, name, kind, owner, description, cover, songs);
    }

    /// How long the whole list plays for.
    public Duration length() {
        return tracks.stream().map(Track::length).reduce(Duration.ZERO, Duration::plus);
    }
}
