package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;

/// [VideoStatistics] and what a player with nothing open reports. What a
/// playback counts is in `VideoPlaybackTest`.
@DisplayName("VideoStatistics")
class VideoStatisticsTest {

    @Test
    @DisplayName("dropped is late and passed together, and no count is negative")
    void dropped() {
        var statistics = new VideoStatistics(100, 3, 4, 93);
        assertEquals(7, statistics.dropped());
        assertEquals(0, VideoStatistics.NONE.dropped());
        assertThrows(IllegalArgumentException.class, () -> new VideoStatistics(1, -1, 0, 0));
    }

    @Test
    @DisplayName("a player with nothing open has seen nothing")
    void nothingOpen() {
        try (var player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
                .build()) {
            assertEquals(VideoStatistics.NONE, player.videoStatistics());
        }
    }
}
