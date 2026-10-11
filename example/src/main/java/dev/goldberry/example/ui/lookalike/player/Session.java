package dev.goldberry.example.ui.lookalike.player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import dev.goldberry.example.ui.lookalike.library.Library;
import dev.goldberry.example.ui.lookalike.library.Playlist;
import dev.goldberry.example.ui.lookalike.library.Track;

/// Everything the player is doing, as one value: what is shown, what plays, how
/// loud, and what has a heart on it.
///
/// Immutable. A control asks for a [Command] and [#apply] answers with the next
/// session, so every rule of the player is a pure function a test can call
/// without a window, and the widget holding a session does nothing but swap it.
///
/// @param viewing  the playlist the main view shows
/// @param queue    the playlist the player plays from
/// @param index    which of the queue's tracks is current
/// @param playing  whether time is passing
/// @param position how far into the current track
/// @param volume   from `0` to `1`, kept while muted
/// @param muted    whether the volume is silenced
/// @param shuffle  whether [Command.Next] picks from a shuffled order
/// @param repeat   what the end of a track does
/// @param liked    the ids of the tracks with a heart
/// @param panel    whether the now-playing panel is open
public record Session(
        String viewing,
        String queue,
        int index,
        boolean playing,
        Duration position,
        double volume,
        boolean muted,
        boolean shuffle,
        Repeat repeat,
        Set<String> liked,
        boolean panel) {

    /// How far into a track `Previous` restarts it rather than going back one.
    public static final Duration RESTART_AFTER = Duration.ofSeconds(3);

    public Session {
        Objects.requireNonNull(viewing, "viewing");
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(repeat, "repeat");
        liked = Set.copyOf(liked);
        if (index < 0) {
            throw new IllegalArgumentException("a queue position is never negative, not " + index);
        }
        if (position.isNegative()) {
            throw new IllegalArgumentException("a position is never negative, not " + position);
        }
        volume = Math.clamp(volume, 0.0, 1.0);
    }

    /// A paused player showing and queueing `playlist`, at `index` and
    /// `position` into it.
    public static Session start(String playlist, int index, Duration position, Set<String> liked) {
        return new Session(playlist, playlist, index, false, position, 0.7, false, false, Repeat.OFF, liked, false);
    }

    /// The tracks the player is playing from.
    public List<Track> queued(Library library) {
        return library.playlist(queue, liked).tracks();
    }

    /// The playlist the main view shows, with [Library#LIKED] answered.
    public Playlist shown(Library library) {
        return library.playlist(viewing, liked);
    }

    /// The current track, or empty when the queue has none.
    public Optional<Track> current(Library library) {
        var tracks = queued(library);
        return index < tracks.size() ? Optional.of(tracks.get(index)) : Optional.empty();
    }

    /// How far through the current track, from `0` to `1`.
    public double progress(Library library) {
        return current(library)
                .map(track ->
                        Math.clamp((double) position.toMillis() / track.length().toMillis(), 0.0, 1.0))
                .orElse(0.0);
    }

    /// The volume the speaker hears: nothing while muted.
    public double audible() {
        return muted ? 0 : volume;
    }

    /// Whether `trackId` has a heart.
    public boolean likes(String trackId) {
        return liked.contains(trackId);
    }

    /// The session after `command`.
    public Session apply(Command command, Library library) {
        return switch (command) {
            case Command.Toggle() ->
                current(library).isPresent() ? with(index, !playing, position) : with(index, false, position);
            case Command.Next() -> skip(library, true);
            case Command.Previous() -> back(library);
            case Command.Tick(var elapsed) -> tick(library, elapsed);
            case Command.Seek(var fraction) ->
                current(library)
                        .map(track -> with(
                                index,
                                playing,
                                Duration.ofMillis(
                                        Math.round(track.length().toMillis() * Math.clamp(fraction, 0.0, 1.0)))))
                        .orElse(this);
            case Command.Volume(var level) ->
                new Session(viewing, queue, index, playing, position, level, false, shuffle, repeat, liked, panel);
            case Command.Mute() ->
                new Session(viewing, queue, index, playing, position, volume, !muted, shuffle, repeat, liked, panel);
            case Command.Like(var trackId) -> like(library, trackId);
            case Command.Shuffle() ->
                new Session(viewing, queue, index, playing, position, volume, muted, !shuffle, repeat, liked, panel);
            case Command.CycleRepeat() ->
                new Session(
                        viewing, queue, index, playing, position, volume, muted, shuffle, repeat.next(), liked, panel);
            case Command.Play(var playlistId, var at) -> play(library, playlistId, at);
            case Command.Open(var playlistId) -> {
                var _ = library.playlist(playlistId, liked);
                yield new Session(
                        playlistId, queue, index, playing, position, volume, muted, shuffle, repeat, liked, panel);
            }
            case Command.NowPlayingPanel() ->
                new Session(viewing, queue, index, playing, position, volume, muted, shuffle, repeat, liked, !panel);
        };
    }

    /// The order `Next` walks the queue in: the queue's own, or a shuffle of it
    /// seeded by the queue's id, so the same playlist shuffles the same way and
    /// a test can say what comes next.
    public List<Integer> order(int size) {
        var order = new ArrayList<>(IntStream.range(0, size).boxed().toList());
        if (shuffle) {
            Collections.shuffle(order, new Random(queue.hashCode()));
        }
        return List.copyOf(order);
    }

    private Session with(int at, boolean play, Duration time) {
        return new Session(viewing, queue, at, play, time, volume, muted, shuffle, repeat, liked, panel);
    }

    /// One track on: pressed, or the end of the last one reached.
    ///
    /// Repeat-one holds only for a track that ended — a press always moves on.
    /// Off the end of the order, `ALL` goes round and `OFF` stops at the start.
    private Session skip(Library library, boolean pressed) {
        var size = queued(library).size();
        if (size == 0) {
            return with(0, false, Duration.ZERO);
        }
        if (!pressed && repeat == Repeat.ONE) {
            return with(index, playing, Duration.ZERO);
        }
        var order = order(size);
        var place = order.indexOf(Math.min(index, size - 1));
        if (place + 1 < size) {
            return with(order.get(place + 1), playing, Duration.ZERO);
        }
        return with(order.getFirst(), playing && repeat != Repeat.OFF, Duration.ZERO);
    }

    private Session back(Library library) {
        var size = queued(library).size();
        if (size == 0 || position.compareTo(RESTART_AFTER) >= 0) {
            return with(index, playing, Duration.ZERO);
        }
        var order = order(size);
        var place = order.indexOf(Math.min(index, size - 1));
        if (place > 0) {
            return with(order.get(place - 1), playing, Duration.ZERO);
        }
        return with(repeat == Repeat.OFF ? index : order.getLast(), playing, Duration.ZERO);
    }

    private Session tick(Library library, Duration elapsed) {
        var track = current(library);
        if (!playing || track.isEmpty()) {
            return this;
        }
        var moved = position.plus(elapsed);
        return moved.compareTo(track.get().length()) < 0 ? with(index, true, moved) : skip(library, false);
    }

    /// A heart on or off, keeping the current track current when the queue is
    /// the liked list that just changed under it.
    private Session like(Library library, String trackId) {
        if (library.track(trackId).isEmpty()) {
            throw new IllegalArgumentException("no track is called \"" + trackId + "\"");
        }
        var playingId = current(library).map(Track::id);
        var hearts = new HashSet<>(liked);
        if (!hearts.remove(trackId)) {
            hearts.add(trackId);
        }
        var next = new Session(viewing, queue, index, playing, position, volume, muted, shuffle, repeat, hearts, panel);
        if (!queue.equals(Library.LIKED) || playingId.isEmpty()) {
            return next;
        }
        var tracks = next.queued(library);
        var still = IntStream.range(0, tracks.size())
                .filter(at -> tracks.get(at).id().equals(playingId.get()))
                .findFirst();
        if (still.isPresent()) {
            return next.with(still.getAsInt(), playing, position);
        }
        return tracks.isEmpty()
                ? next.with(0, false, Duration.ZERO)
                : next.with(Math.min(index, tracks.size() - 1), playing, Duration.ZERO);
    }

    private Session play(Library library, String playlistId, int at) {
        var tracks = library.playlist(playlistId, liked).tracks();
        if (tracks.isEmpty()) {
            return new Session(
                    viewing, playlistId, 0, false, Duration.ZERO, volume, muted, shuffle, repeat, liked, panel);
        }
        if (at < 0 || at >= tracks.size()) {
            throw new IllegalArgumentException(
                    playlistId + " has " + tracks.size() + " tracks, so there is no track " + at);
        }
        return new Session(viewing, playlistId, at, true, Duration.ZERO, volume, muted, shuffle, repeat, liked, panel);
    }
}
