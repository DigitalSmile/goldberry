package dev.goldberry.example.ui.lookalike.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.lookalike.library.Library;
import dev.goldberry.example.ui.lookalike.library.SampleLibrary;
import dev.goldberry.example.ui.lookalike.library.Track;

/// The player's rules, one command at a time, with no window.
@DisplayName("the player session")
class SessionTest {

    private static final Library LIBRARY = SampleLibrary.create();

    /// Night Drive, nine tracks; the third, Midnight Arcade, is 3:27.
    private static final String NIGHT_DRIVE = "night-drive";

    private static Session at(int index, Duration position) {
        return Session.start(NIGHT_DRIVE, index, position, SampleLibrary.initiallyLiked());
    }

    private static Session playing(int index, Duration position) {
        return at(index, position).apply(new Command.Toggle(), LIBRARY);
    }

    private static Session apply(Session session, Command... commands) {
        var result = session;
        for (var command : commands) {
            result = result.apply(command, LIBRARY);
        }
        return result;
    }

    private static String title(Session session) {
        return session.current(LIBRARY).map(Track::title).orElse("(nothing)");
    }

    @Nested
    @DisplayName("play and pause")
    class Toggle {

        @Test
        @DisplayName("toggles, keeping the position")
        void toggles() {
            var start = at(2, Duration.ofSeconds(76));
            var on = apply(start, new Command.Toggle());
            var off = apply(on, new Command.Toggle());

            assertTrue(on.playing());
            assertFalse(off.playing());
            assertEquals(Duration.ofSeconds(76), off.position());
        }

        @Test
        @DisplayName("will not play an empty queue")
        void emptyQueue() {
            var empty = Session.start(Library.LIKED, 0, Duration.ZERO, Set.of());

            assertFalse(apply(empty, new Command.Toggle()).playing());
        }

        @Test
        @DisplayName("plays a playlist from the track asked for, at its start")
        void play() {
            var session = apply(at(2, Duration.ofSeconds(76)), new Command.Play("deep-focus", 1));

            assertEquals("deep-focus", session.queue());
            assertEquals("Rain on Tin", title(session));
            assertEquals(Duration.ZERO, session.position());
            assertTrue(session.playing());
            assertEquals(NIGHT_DRIVE, session.viewing(), "playing a list does not open it");
        }

        @Test
        @DisplayName("refuses a track the playlist does not have")
        void playOutOfRange() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> apply(at(0, Duration.ZERO), new Command.Play(NIGHT_DRIVE, 9)));
        }
    }

    @Nested
    @DisplayName("time")
    class Time {

        @Test
        @DisplayName("moves only while playing")
        void ticksWhilePlaying() {
            var paused = at(2, Duration.ofSeconds(10));

            assertSame(paused, apply(paused, new Command.Tick(Duration.ofSeconds(1))));
            assertEquals(
                    Duration.ofSeconds(11),
                    apply(playing(2, Duration.ofSeconds(10)), new Command.Tick(Duration.ofSeconds(1)))
                            .position());
        }

        @Test
        @DisplayName("moves on to the next track at the end of one")
        void endOfTrack() {
            var session = apply(playing(2, Duration.ofSeconds(206)), new Command.Tick(Duration.ofSeconds(1)));

            assertEquals("Glasshouse", title(session));
            assertEquals(Duration.ZERO, session.position());
            assertTrue(session.playing());
        }

        @Test
        @DisplayName("plays a track again on repeat-one")
        void repeatOne() {
            var session = apply(
                    playing(2, Duration.ofSeconds(206)),
                    new Command.CycleRepeat(),
                    new Command.CycleRepeat(),
                    new Command.Tick(Duration.ofSeconds(1)));

            assertEquals(Repeat.ONE, session.repeat());
            assertEquals("Midnight Arcade", title(session));
            assertEquals(Duration.ZERO, session.position());
        }

        @Test
        @DisplayName("stops at the top of the list after the last track, with repeat off")
        void stopsAtTheEnd() {
            var session = apply(playing(8, Duration.ofSeconds(117)), new Command.Tick(Duration.ofSeconds(1)));

            assertFalse(session.playing());
            assertEquals(0, session.index());
        }

        @Test
        @DisplayName("goes round with repeat on")
        void goesRound() {
            var session = apply(
                    playing(8, Duration.ofSeconds(117)),
                    new Command.CycleRepeat(),
                    new Command.Tick(Duration.ofSeconds(1)));

            assertTrue(session.playing());
            assertEquals(0, session.index());
        }

        @Test
        @DisplayName("seeks to a fraction of the track, held to its ends")
        void seeks() {
            var start = at(2, Duration.ZERO);

            assertEquals(
                    Duration.ofMillis(103_500),
                    apply(start, new Command.Seek(0.5)).position());
            assertEquals(Duration.ZERO, apply(start, new Command.Seek(-3)).position());
            assertEquals(
                    Duration.ofSeconds(207), apply(start, new Command.Seek(7)).position());
            assertEquals(0.5, apply(start, new Command.Seek(0.5)).progress(LIBRARY), 1e-9);
        }

        @Test
        @DisplayName("refuses time running backwards")
        void noNegativeTick() {
            assertThrows(IllegalArgumentException.class, () -> new Command.Tick(Duration.ofSeconds(-1)));
        }
    }

    @Nested
    @DisplayName("next and previous")
    class Skipping {

        @Test
        @DisplayName("next moves on even on repeat-one, which holds only for a track that ended")
        void nextIgnoresRepeatOne() {
            var session = apply(
                    playing(2, Duration.ofSeconds(30)),
                    new Command.CycleRepeat(),
                    new Command.CycleRepeat(),
                    new Command.Next());

            assertEquals("Glasshouse", title(session));
        }

        @Test
        @DisplayName("previous restarts a track that has been playing a while")
        void previousRestarts() {
            var session = apply(playing(2, Session.RESTART_AFTER), new Command.Previous());

            assertEquals("Midnight Arcade", title(session));
            assertEquals(Duration.ZERO, session.position());
        }

        @Test
        @DisplayName("previous goes back one from the start of a track")
        void previousGoesBack() {
            assertEquals("Slow Satellite", title(apply(playing(2, Duration.ofSeconds(1)), new Command.Previous())));
        }

        @Test
        @DisplayName("previous from the first track stays, or wraps on repeat")
        void previousAtTheTop() {
            assertEquals(0, apply(at(0, Duration.ZERO), new Command.Previous()).index());
            assertEquals(
                    8,
                    apply(at(0, Duration.ZERO), new Command.CycleRepeat(), new Command.Previous())
                            .index());
        }
    }

    @Nested
    @DisplayName("shuffle")
    class Shuffle {

        @Test
        @DisplayName("walks every track once, in an order that is the same every time")
        void aPermutation() {
            var shuffled = apply(at(0, Duration.ZERO), new Command.Shuffle());
            var order = shuffled.order(9);

            assertEquals(
                    List.copyOf(IntStream.range(0, 9).boxed().toList()),
                    order.stream().sorted().toList());
            assertNotEquals(IntStream.range(0, 9).boxed().toList(), order, "a shuffle of nine that changed nothing");
            assertEquals(
                    order, apply(at(4, Duration.ZERO), new Command.Shuffle()).order(9));
        }

        @Test
        @DisplayName("next follows the shuffled order")
        void nextFollowsIt() {
            var shuffled = apply(playing(0, Duration.ZERO), new Command.Shuffle());
            var order = shuffled.order(9);
            var start = apply(shuffled, new Command.Play(NIGHT_DRIVE, order.getFirst()));

            assertEquals(order.get(1), apply(start, new Command.Next()).index());
        }

        @Test
        @DisplayName("off, walks the list in order")
        void offIsInOrder() {
            assertEquals(
                    IntStream.range(0, 5).boxed().toList(), at(0, Duration.ZERO).order(5));
        }
    }

    @Nested
    @DisplayName("hearts")
    class Hearts {

        @Test
        @DisplayName("a heart goes on and comes off")
        void toggles() {
            var on = apply(at(0, Duration.ZERO), new Command.Like("t02"));
            var off = apply(on, new Command.Like("t02"));

            assertTrue(on.likes("t02"));
            assertFalse(off.likes("t02"));
            assertEquals(9, on.shown(LIBRARY).tracks().size(), "Night Drive is unchanged by a heart");
            assertEquals(6, LIBRARY.playlist(Library.LIKED, on.liked()).tracks().size());
        }

        @Test
        @DisplayName("keeps the playing track current when Liked Songs changes under it")
        void keepsTheCurrentTrack() {
            var playingLiked = apply(at(0, Duration.ZERO), new Command.Play(Library.LIKED, 2));
            var current = title(playingLiked);
            var session = apply(playingLiked, new Command.Seek(0.5), new Command.Like("t01"));

            assertEquals(current, title(session), "unliking an earlier song moved the queue under the player");
            assertTrue(session.position().compareTo(Duration.ZERO) > 0, "and the position was kept");
        }

        @Test
        @DisplayName("moves to a neighbour when the playing track loses its heart in Liked Songs")
        void losesTheCurrentTrack() {
            var playingLiked = apply(at(0, Duration.ZERO), new Command.Play(Library.LIKED, 1));
            var gone = title(playingLiked);
            var session = apply(
                    playingLiked,
                    new Command.Like(playingLiked.current(LIBRARY).orElseThrow().id()));

            assertNotEquals(gone, title(session));
            assertEquals(1, session.index());
            assertEquals(Duration.ZERO, session.position());
        }

        @Test
        @DisplayName("stops when the last heart in a playing Liked Songs comes off")
        void lastHeart() {
            var one = Session.start(Library.LIKED, 0, Duration.ZERO, Set.of("t01"));
            var session = apply(one, new Command.Toggle(), new Command.Like("t01"));

            assertFalse(session.playing());
            assertTrue(session.current(LIBRARY).isEmpty());
        }

        @Test
        @DisplayName("refuses a heart on a track the library does not have")
        void unknownTrack() {
            assertThrows(IllegalArgumentException.class, () -> apply(at(0, Duration.ZERO), new Command.Like("t99")));
        }
    }

    @Nested
    @DisplayName("everything else")
    class Rest {

        @Test
        @DisplayName("repeat moves off, all, one and round again")
        void repeatCycles() {
            assertEquals(
                    List.of(Repeat.ALL, Repeat.ONE, Repeat.OFF),
                    List.of(Repeat.OFF.next(), Repeat.ALL.next(), Repeat.ONE.next()));
        }

        @Test
        @DisplayName("mute silences and gives the volume back; a new volume unmutes")
        void volume() {
            var muted = apply(at(0, Duration.ZERO), new Command.Volume(0.4), new Command.Mute());

            assertEquals(0, muted.audible());
            assertEquals(0.4, apply(muted, new Command.Mute()).audible(), 1e-9);
            assertEquals(0.9, apply(muted, new Command.Volume(0.9)).audible(), 1e-9);
            assertEquals(1, apply(muted, new Command.Volume(4)).volume());
        }

        @Test
        @DisplayName("opening a playlist shows it and leaves what plays alone")
        void open() {
            var playing = playing(2, Duration.ofSeconds(76));
            var session = apply(playing, new Command.Open("deep-focus"));

            assertEquals("deep-focus", session.viewing());
            assertEquals(NIGHT_DRIVE, session.queue());
            assertEquals("Midnight Arcade", title(session));
            assertTrue(session.playing());
        }

        @Test
        @DisplayName("refuses to open a playlist the library does not have")
        void openUnknown() {
            assertThrows(IllegalArgumentException.class, () -> apply(at(0, Duration.ZERO), new Command.Open("polka")));
        }

        @Test
        @DisplayName("the now-playing panel opens and closes")
        void panel() {
            var open = apply(at(0, Duration.ZERO), new Command.NowPlayingPanel());

            assertTrue(open.panel());
            assertFalse(apply(open, new Command.NowPlayingPanel()).panel());
        }
    }
}
