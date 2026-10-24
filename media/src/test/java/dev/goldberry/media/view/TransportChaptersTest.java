package dev.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.media.Chapter;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.controls.slider.Slider;
import dev.goldberry.widgets.text.Text;

/// What the transport makes of a source's chapters: notches on the seek bar,
/// the title of the one playing, and `Page Up` and `Page Down`.
@DisplayName("the transport's chapters")
@Timeout(60)
class TransportChaptersTest {

    private static final Source SOURCE = Source.of(URI.create("file:///book.m4b"));

    private static final List<Chapter> CHAPTERS = List.of(
            new Chapter(Duration.ZERO, "Prologue"),
            new Chapter(Duration.ofSeconds(30), "The road"),
            new Chapter(Duration.ofSeconds(75), Optional.empty(), Optional.empty()));

    private MediaPlayer player;

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    private static PlayerStatus status(Duration position, List<Chapter> chapters) {
        var info = new MediaInfo(SOURCE, Optional.of(Duration.ofSeconds(100)), List.of(), true, false, chapters);
        return new PlayerStatus(
                PlaybackState.PAUSED,
                position,
                Optional.of(info),
                Optional.empty(),
                1f,
                false,
                1f,
                Optional.empty(),
                Optional.empty(),
                Duration.ZERO,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false);
    }

    private static Optional<String> chapterLabel(PlayerStatus status) {
        return Transport.chapterTitle(status).map(widget -> ((Text) widget).content());
    }

    @Test
    @DisplayName("each chapter start but the first is a mark on the seek bar, in seconds")
    void marks() {
        assertEquals(List.of(30.0, 75.0), Transport.chapterMarks(status(Duration.ZERO, CHAPTERS)));
        assertEquals(List.of(), Transport.chapterMarks(status(Duration.ZERO, List.of())));
        assertEquals(List.of(), Transport.chapterMarks(PlayerStatus.IDLE));
    }

    @Test
    @DisplayName("the seek bar the controls build carries the marks")
    void seekBarMarks() {
        player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .build();
        var controls = new Transport().controls(player, status(Duration.ofSeconds(40), CHAPTERS));
        var seek = controls.stream()
                .filter(Slider.class::isInstance)
                .map(Slider.class::cast)
                .filter(slider -> slider.attributes().classes().contains("media-seek"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(30.0, 75.0), seek.marks());
        assertTrue(controls.stream().anyMatch(TransportChaptersTest::isChapterLabel), "the chapter's title is shown");
    }

    private static boolean isChapterLabel(Widget widget) {
        return widget instanceof Text text && text.attributes().classes().contains("media-chapter");
    }

    @Test
    @DisplayName("the label is the title of the chapter playing, and none for an untitled one or none at all")
    void title() {
        assertEquals(Optional.of("Prologue"), chapterLabel(status(Duration.ofSeconds(5), CHAPTERS)));
        assertEquals(Optional.of("The road"), chapterLabel(status(Duration.ofSeconds(30), CHAPTERS)));
        assertEquals(Optional.empty(), chapterLabel(status(Duration.ofSeconds(80), CHAPTERS)), "untitled");
        assertEquals(Optional.empty(), chapterLabel(status(Duration.ofSeconds(5), List.of())));
        assertEquals(Optional.empty(), chapterLabel(PlayerStatus.IDLE));
    }

    /// Serves one byte array under `mem:`.
    private record MemoryProtocol(byte[] data) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return new MemoryIO(data);
        }
    }

    private PlayerStatus await(Predicate<PlayerStatus> condition) {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var status = player.status();
            if (condition.test(status)) {
                return status;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("timed out; last status " + player.status());
    }

    private static KeyEvent press(Key key) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
    }

    @Test
    @DisplayName("Page Down and Page Up step through a playing source's chapters, and are left alone without")
    void keys() {
        MediaNativesRequirement.enforce();
        player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new MemoryProtocol(ClipFixtures.bytes("tone-chapters.mkv"))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///tone-chapters.mkv")));
        await(status -> status.state().hasMedia());
        player.pause();
        player.seek(Duration.ZERO);
        await(status ->
                status.state() == PlaybackState.PAUSED && status.position().isZero());

        var transport = new Transport();
        var down = press(Key.PAGE_DOWN);
        transport.onKey(player, down);
        assertTrue(down.isConsumed());
        await(status -> status.position().equals(Duration.ofMillis(300)));
        var up = press(Key.PAGE_UP);
        transport.onKey(player, up);
        assertTrue(up.isConsumed());
        await(status -> status.position().isZero());

        player.close();
        player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new MemoryProtocol(ClipFixtures.bytes("tone-flac.mkv"))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///tone-flac.mkv")));
        await(status -> status.state().hasMedia());
        var unanswered = press(Key.PAGE_DOWN);
        transport.onKey(player, unanswered);
        assertFalse(unanswered.isConsumed(), "a page key with no chapters scrolls whatever is around the player");
    }
}
