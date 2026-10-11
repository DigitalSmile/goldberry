package dev.goldberry.example.ui.lookalike.library;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Everything the player can play: the user's playlists and a shelf of albums.
///
/// One playlist is not stored at all. [#LIKED] is every track the user has
/// liked, in the order the library first lists them, so a heart pressed on any
/// row adds a song to it and a heart pressed again takes it away.
///
/// @param playlists the sidebar's list, `Liked Songs` first
/// @param albums    the shelf under a playlist's tracks
public record Library(List<Playlist> playlists, List<Album> albums) {

    /// The id of the playlist made of liked songs.
    public static final String LIKED = "liked";

    public Library {
        playlists = List.copyOf(playlists);
        albums = List.copyOf(albums);
        if (playlists.isEmpty()) {
            throw new IllegalArgumentException("a library has at least one playlist to show");
        }
        var ids = playlists.stream().map(Playlist::id).toList();
        if (Set.copyOf(ids).size() != ids.size()) {
            throw new IllegalArgumentException("two playlists share an id: " + ids);
        }
    }

    /// The playlist called `id`, with `liked` answering what [#LIKED] holds.
    ///
    /// @throws IllegalArgumentException for an id the library does not have,
    ///         which is a sidebar row that opens nothing
    public Playlist playlist(String id, Set<String> liked) {
        var found = find(id).orElseThrow(() -> new IllegalArgumentException("no playlist is called \"" + id
                + "\"; the library has " + playlists.stream().map(Playlist::id).toList()));
        if (!found.id().equals(LIKED)) {
            return found;
        }
        return found.withTracks(everyTrack().stream()
                .filter(track -> liked.contains(track.id()))
                .toList());
    }

    /// The playlist called `id`, as stored.
    public Optional<Playlist> find(String id) {
        Objects.requireNonNull(id, "id");
        return playlists.stream().filter(list -> list.id().equals(id)).findFirst();
    }

    /// Every track in the library once, in the order the playlists first list it.
    public List<Track> everyTrack() {
        var tracks = new LinkedHashMap<String, Track>();
        playlists.forEach(list -> list.tracks().forEach(track -> tracks.putIfAbsent(track.id(), track)));
        return List.copyOf(tracks.values());
    }

    /// The track called `id`.
    public Optional<Track> track(String id) {
        return everyTrack().stream().filter(track -> track.id().equals(id)).findFirst();
    }
}
