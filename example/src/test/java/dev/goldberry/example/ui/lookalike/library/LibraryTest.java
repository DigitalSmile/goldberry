package dev.goldberry.example.ui.lookalike.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The library: playlists, the liked list made from hearts, and the sample the
/// screen opens on.
@DisplayName("the music library")
class LibraryTest {

    private final Library library = SampleLibrary.create();

    @Test
    @DisplayName("makes Liked Songs from the hearts, in the order the library lists the tracks")
    void likedIsMadeFromHearts() {
        var liked = library.playlist(Library.LIKED, Set.of("t15", "t01", "t04"));

        assertEquals(
                List.of("t01", "t04", "t15"),
                liked.tracks().stream().map(Track::id).toList());
        assertEquals("Liked Songs", liked.name());
    }

    @Test
    @DisplayName("has an empty Liked Songs when nothing has a heart")
    void nothingLiked() {
        assertTrue(library.playlist(Library.LIKED, Set.of()).tracks().isEmpty());
    }

    @Test
    @DisplayName("hands a stored playlist back as it is, whatever has a heart")
    void storedPlaylistsIgnoreHearts() {
        var stored = library.find("night-drive").orElseThrow();

        assertEquals(stored, library.playlist("night-drive", Set.of("t01")));
    }

    @Test
    @DisplayName("refuses a playlist it does not have, naming the ones it does")
    void unknownPlaylist() {
        var failure = assertThrows(IllegalArgumentException.class, () -> library.playlist("polka", Set.of()));

        assertTrue(failure.getMessage().contains("polka"), failure.getMessage());
        assertTrue(failure.getMessage().contains("night-drive"), failure.getMessage());
    }

    @Test
    @DisplayName("lists every track once, though playlists share them")
    void everyTrackOnce() {
        var ids = library.everyTrack().stream().map(Track::id).toList();

        assertEquals(Set.copyOf(ids).size(), ids.size(), "a track listed twice: " + ids);
        assertEquals(16, ids.size());
    }

    @Test
    @DisplayName("adds a playlist's lengths up")
    void length() {
        assertEquals(
                Duration.ofMinutes(32).plusSeconds(17),
                library.find("night-drive").orElseThrow().length());
    }

    @Test
    @DisplayName("refuses two playlists with one id, and a library with none")
    void refusesBadShapes() {
        var one = library.playlists().get(1);

        assertThrows(IllegalArgumentException.class, () -> new Library(List.of(one, one), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Library(List.of(), List.of()));
    }

    @Test
    @DisplayName("refuses a track that plays for no time")
    void refusesAnEmptyTrack() {
        assertThrows(
                IllegalArgumentException.class, () -> new Track("x", "Silence", "Nobody", "Nothing", Duration.ZERO, 0));
    }

    @Test
    @DisplayName("starts with hearts only on tracks it has")
    void initialHeartsExist() {
        SampleLibrary.initiallyLiked()
                .forEach(id -> assertTrue(library.track(id).isPresent(), id + " is liked and not in the library"));
        assertFalse(SampleLibrary.initiallyLiked().isEmpty());
    }

    @Test
    @DisplayName("names a cover's classes after it")
    void coverClasses() {
        assertEquals("cover-ember", Cover.EMBER.cssClass());
        assertEquals("tint-liked", Cover.LIKED.tintClass());
    }
}
