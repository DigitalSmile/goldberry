package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Containers;
import dev.goldberry.media.engine.Decoders;
import dev.goldberry.media.io.Source;

/// Pictures past the 8K cap, from each place one can come: an AV1 frame of
/// 36 megapixels, which dav1d refuses before it allocates it; an AV1 frame
/// 8256 wide, which dav1d decodes and the decoder refuses; a VP9 frame 8256
/// wide, which libvpx refuses; a VP9 frame of 36 megapixels, which the decoder
/// refuses; and a track whose container states such a size, which the player
/// refuses as it opens.
@DisplayName("the 8K picture cap")
@Timeout(60)
class MediaPictureCapTest {

    private MediaPlayer player;

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    /// Decodes the video of `name` with the built-in decoders until it fails.
    private static MediaException decodeUntilRefused(String name) {
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            container.select(Set.of(track.index()));
            var decoder = Decoders.open(container, track.index(), List.of(), 0, HardwareDecoding.AUTO)
                    .decoder();
            try (decoder) {
                return assertThrows(MediaException.class, () -> {
                    for (var next = container.read(); next != null; next = container.read()) {
                        try (var packet = next) {
                            while (!decoder.send(packet)) {
                                drain(decoder);
                            }
                        }
                        drain(decoder);
                    }
                    decoder.sendEnd();
                    while (decoder.receive() instanceof Received.Decoded) {
                        // Every picture past the cap is refused before it is handed over.
                    }
                });
            }
        }
    }

    private static void drain(Decoder decoder) {
        while (decoder.receive() instanceof Received.Decoded) {
            // Nothing to keep: the test is whether a picture comes out at all.
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {"clip-av1-36mp.mkv", "clip-av1-8256-wide.mkv", "clip-vp9-8256-wide.webm", "clip-vp9-36mp.webm"})
    @DisplayName("a built-in decoder refuses a picture past the cap as invalid data")
    void decoderRefuses(String name) {
        var refused = decodeUntilRefused(name);
        assertInstanceOf(MediaError.InvalidData.class, refused.error(), refused.getMessage());
        assertTrue(
                refused.getMessage().contains("past") || refused.getMessage().contains("beyond allowed size"),
                "refused for its size, not for something else: " + refused.getMessage());
    }

    @Test
    @DisplayName("the player refuses a track whose container states a size past the cap, naming it")
    void playerRefuses() {
        player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new MediaPlayerTest.MemoryProtocol(ClipFixtures.bytes("clip-vp9-36mp.webm"))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///clip-vp9-36mp.webm")));
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (player.status().state() != PlaybackState.ERROR && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        var status = player.status();
        assertEquals(PlaybackState.ERROR, status.state(), status.toString());
        var error =
                assertInstanceOf(MediaError.InvalidData.class, status.error().orElseThrow());
        assertTrue(error.message().contains("6000×6000"), error.message());
    }
}
