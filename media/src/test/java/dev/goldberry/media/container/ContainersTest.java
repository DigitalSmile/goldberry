package dev.goldberry.media.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.io.MemoryIO;

@DisplayName("Containers")
class ContainersTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "clip-av1.mkv, dev.goldberry.media.container.matroska.MatroskaContainer",
        "clip-vp9.webm, dev.goldberry.media.container.matroska.MatroskaContainer",
        "tone-opus.webm, dev.goldberry.media.container.matroska.MatroskaContainer",
        "tones-two-tracks.mkv, dev.goldberry.media.container.matroska.MatroskaContainer",
        "clip-av1.mp4, dev.goldberry.media.container.mp4.Mp4Container",
        "tone-opus.mp4, dev.goldberry.media.container.mp4.Mp4Container",
        "tone.mp3, dev.goldberry.media.container.mp3.Mp3Container",
        "tone-cover.mp3, dev.goldberry.media.container.mp3.Mp3Container",
        "tone.ogg, dev.goldberry.media.container.ogg.OggContainer",
        "tone.opus, dev.goldberry.media.container.ogg.OggContainer",
        "tone.flac, dev.goldberry.media.container.flac.FlacContainer",
    })
    @DisplayName("opens Matroska, WebM, MP4, MP3 and Ogg by their first bytes")
    void opens(String name, Class<?> type) {
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            assertInstanceOf(type, container);
            assertTrue(container.info().tracks().size() >= 1);
        }
    }

    @Test
    @DisplayName("opens a WAV, whose one track is PCM")
    void opensWav() {
        var wav = dev.goldberry.media.Wav.silence(8_000, 1, 8_000);
        try (var container = Containers.open(ClipFixtures.source("tone.wav"), new MemoryIO(wav))) {
            assertInstanceOf(dev.goldberry.media.container.wav.WavContainer.class, container);
            assertEquals(CodecId.PCM_S16LE, container.info().tracks().getFirst().codec());
        }
        assertEquals(List.of("matroska", "webm", "mp4", "ogg", "flac", "wav", "mp3"), Containers.readable());
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
        "clip-xvid-ac3.avi, AVI",
        "tone-mp3.avi, AVI",
        "clip-mpeg2.ts, MPEG-TS",
        "clip-flv1.flv, FLV",
    })
    @DisplayName("names a container it recognises and does not read yet")
    void namesTheRest(String name, String format) {
        var e = assertThrows(
                MediaException.class, () -> Containers.open(ClipFixtures.source(name), ClipFixtures.io(name)));
        var error = assertInstanceOf(MediaError.UnsupportedContainer.class, e.error());
        assertEquals(format, error.format());
        assertEquals(Optional.of(format), Containers.identify(ClipFixtures.bytes(name)));
    }

    @Test
    @DisplayName("refuses bytes no container begins with, as invalid data")
    void garbage() {
        var bytes = "hello, this is not a media file at all".getBytes(StandardCharsets.UTF_8);
        var e = assertThrows(
                MediaException.class, () -> Containers.open(ClipFixtures.source("hello.txt"), new MemoryIO(bytes)));
        assertInstanceOf(MediaError.InvalidData.class, e.error());
        assertEquals(Optional.empty(), Containers.identify(bytes));
    }

    @Test
    @DisplayName("a source that cannot seek is read from its first byte all the same")
    void unseekable() {
        var io = ClipFixtures.io("tone-opus.webm");
        io.seekable = false;
        try (var container = Containers.open(ClipFixtures.source("tone-opus.webm"), io)) {
            assertEquals(CodecId.OPUS, container.info().tracks().getFirst().codec());
            var packet = container.read();
            assertTrue(packet != null && packet.data().byteSize() > 0);
            packet.close();
        }
    }
}
