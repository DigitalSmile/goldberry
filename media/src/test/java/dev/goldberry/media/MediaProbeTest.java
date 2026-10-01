package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// Phase 1's exit criterion: Java probes a file through MediaIO and lists its
/// tracks (`docs/goldberry-media.md` §8).
///
/// The input is a WAV written by [Wav] rather than a fixture file, so its
/// duration, rate and channel count are known exactly.
@DisplayName("MediaProbe, against FFmpeg")
class MediaProbeTest {

    @TempDir
    Path directory;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
    }

    @Test
    @DisplayName("lists the one audio track of a WAV file, with its rate, channels and duration")
    void wavFile() throws IOException {
        var path = Files.write(directory.resolve("tone.wav"), Wav.silence(48_000, 2, 96_000));
        var info = MediaProbe.probe(Source.of(path), List.of());

        assertEquals(1, info.tracks().size());
        var track = info.tracks().getFirst();
        assertEquals(CodecId.PCM_S16LE, track.codec());
        assertEquals(MediaType.AUDIO, track.type());
        var audio = assertInstanceOf(TrackParams.Audio.class, track.params());
        assertEquals(48_000, audio.sampleRate());
        assertEquals(2, audio.channels());
        assertEquals(Optional.of("s16"), audio.sampleFormat());
        assertEquals(Optional.of(Duration.ofSeconds(2)), info.duration());
        assertTrue(info.seekable());
    }

    @Test
    @DisplayName("probes through any MediaIO, including one that reads a byte at a time and is not seekable")
    void awkwardStream() {
        var io = new MemoryIO(Wav.silence(8_000, 1, 8_000));
        io.chunk = 1;
        io.seekable = false;
        var info = MediaProbe.probe(Source.of(URI.create("mem:///tone.wav")), io);
        assertEquals(1, info.tracks(MediaType.AUDIO).size());
        assertEquals(8_000, ((TrackParams.Audio) info.tracks().getFirst().params()).sampleRate());
    }

    @Test
    @DisplayName("bytes that are not media are InvalidData")
    void notMedia() {
        var io = new MemoryIO(
                "this is a text file and not a film".repeat(100).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var error =
                assertThrows(MediaException.class, () -> MediaProbe.probe(Source.of(URI.create("mem:///x.bin")), io));
        assertInstanceOf(MediaError.InvalidData.class, error.error());
    }

    @Test
    @DisplayName("a failed read is an Io error carrying the MediaIO's own message")
    void readFailure() {
        var io = new MemoryIO(Wav.silence(8_000, 1, 8_000));
        io.readFailure = new IOException("connection reset by peer");
        var error =
                assertThrows(MediaException.class, () -> MediaProbe.probe(Source.of(URI.create("mem:///x.wav")), io));
        assertEquals(new MediaError.Io("connection reset by peer"), error.error());
    }

    @Test
    @DisplayName("a stream closed before the probe reads it is Aborted")
    void aborted() {
        var io = new MemoryIO(Wav.silence(8_000, 1, 8_000));
        io.close();
        var error =
                assertThrows(MediaException.class, () -> MediaProbe.probe(Source.of(URI.create("mem:///x.wav")), io));
        assertInstanceOf(MediaError.Aborted.class, error.error());
    }

    @Test
    @DisplayName("a scheme nothing opens is UnsupportedScheme")
    void scheme() {
        var error = assertThrows(
                MediaException.class, () -> MediaProbe.probe(Source.of(URI.create("s3://bucket/a.webm")), List.of()));
        assertEquals(new MediaError.UnsupportedScheme("s3"), error.error());
    }

    @Test
    @DisplayName("a file that is not there is an Io error")
    void missingFile() {
        var error = assertThrows(
                MediaException.class, () -> MediaProbe.probe(Source.of(directory.resolve("absent.webm")), List.of()));
        assertInstanceOf(MediaError.Io.class, error.error());
    }
}
