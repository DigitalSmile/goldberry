package dev.goldberry.media.container.matroska;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// The Matroska writer, read back by this module's own demuxer: what one writes,
/// the other reads, as an unseekable stream of unknown length, which is how a
/// pipe delivers it.
@DisplayName("MatroskaWriter")
class MatroskaWriterTest {

    private static final byte[] AVC_C = {1, 100, 0, 30, (byte) 0xFF, (byte) 0xE1, 0, 4, 0x67, 0x64, 0, 30, 1, 0, 4, 0x68
    };

    /// A frame as written: its bytes, time and flag.
    private record Written(byte[] data, long nanos, boolean keyframe) {}

    private static byte[] write(MatroskaWriter.Track track, List<Written> frames) {
        var out = new ByteArrayOutputStream();
        var writer = new MatroskaWriter(out);
        try {
            writer.writeHeader(track);
            for (var frame : frames) {
                writer.writeFrame(frame.data(), frame.nanos(), frame.keyframe());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /// `bytes` opened as a pipe would be: no seeking, no size.
    private static MatroskaContainer read(byte[] bytes) {
        var io = new MemoryIO(bytes);
        io.seekable = false;
        io.sizeKnown = false;
        io.chunk = 777;
        return MatroskaContainer.open(Source.of(URI.create("mem:///written.mkv")), io);
    }

    private static byte[] frame(int size, int seed) {
        var data = new byte[size];
        for (var i = 0; i < size; i++) {
            data[i] = (byte) (seed * 31 + i);
        }
        return data;
    }

    @Test
    @DisplayName("an H.264 track: its codec, size and CodecPrivate, then every frame, time and flag")
    void video() {
        // Decoding order with pictures reordered, a gap far past a Cluster's
        // reach, and times between whole milliseconds.
        var frames = List.of(
                new Written(frame(1500, 1), 1_000_000_000L, true),
                new Written(frame(300, 2), 1_120_000_000L, false),
                new Written(frame(200, 3), 1_040_000_000L, false),
                new Written(frame(250, 4), 1_080_000_000L, false),
                new Written(frame(70_000, 5), 9_000_123_000L, true),
                new Written(frame(10, 6), 9_000_124_000L, false),
                new Written(frame(10, 7), 8_999_000_001L, false));
        try (var container = read(write(new MatroskaWriter.Video("V_MPEG4/ISO/AVC", AVC_C, 320, 180), frames))) {
            var track = container.info().tracks().getFirst();
            assertEquals(1, container.info().tracks().size());
            assertEquals(CodecId.H264, track.codec());
            assertEquals("h264", track.codecName());
            var video = assertInstanceOf(TrackParams.Video.class, track.params());
            assertEquals(320, video.width());
            assertEquals(180, video.height());
            var request = container.request(0);
            assertArrayEquals(AVC_C, request.extradata().toArray(JAVA_BYTE));
            assertEquals(new Rational(1, 1_000_000), container.timeBase(0));
            assertFalse(container.info().seekable());

            var read = new ArrayList<Written>();
            for (var next = container.read(); next != null; next = container.read()) {
                try (var packet = next) {
                    read.add(new Written(packet.data().toArray(JAVA_BYTE), packet.ptsNanos(), packet.keyframe()));
                }
            }
            assertEquals(frames.size(), read.size());
            for (var i = 0; i < frames.size(); i++) {
                assertArrayEquals(frames.get(i).data(), read.get(i).data(), "the bytes of frame " + i);
                // A microsecond is the most a time keeps.
                assertEquals(
                        Math.round(frames.get(i).nanos() / 1000.0) * 1000,
                        read.get(i).nanos(),
                        "frame " + i);
                assertEquals(frames.get(i).keyframe(), read.get(i).keyframe(), "the flag of frame " + i);
            }
        }
    }

    @Test
    @DisplayName("an audio track: its rate, channels and depth")
    void audio() {
        var asc = new byte[] {0x11, (byte) 0x90};
        var bytes = write(
                new MatroskaWriter.Audio("A_AAC", asc, 48_000, 6, 0),
                List.of(new Written(frame(400, 1), 0, true), new Written(frame(400, 2), 21_333_333, true)));
        try (var container = read(bytes)) {
            var track = container.info().tracks().getFirst();
            assertEquals(CodecId.AAC, track.codec());
            assertEquals(MediaType.AUDIO, track.params().type());
            var audio = assertInstanceOf(TrackParams.Audio.class, track.params());
            assertEquals(48_000, audio.sampleRate());
            assertEquals(6, audio.channels());
            assertArrayEquals(asc, container.request(0).extradata().toArray(JAVA_BYTE));
            try (var first = container.read();
                    var second = container.read()) {
                assertEquals(0, first.ptsNanos());
                assertEquals(21_333_000, second.ptsNanos());
            }
            assertEquals(null, container.read());
        }
        var pcm = write(new MatroskaWriter.Audio("A_PCM/FLOAT/IEEE", new byte[0], 44_100, 2, 32), List.of());
        try (var container = read(pcm)) {
            var track = container.info().tracks().getFirst();
            assertEquals(CodecId.PCM_F32LE, track.codec());
            assertEquals(Optional.of("flt"), ((TrackParams.Audio) track.params()).sampleFormat());
        }
    }

    @Test
    @DisplayName("a Matroska ID for every codec Matroska names, and one way back")
    void matroskaIds() {
        for (var codec : CodecId.values()) {
            var id = MatroskaCodecs.matroskaId(codec);
            if (id.isEmpty()) {
                assertTrue(
                        List.of(CodecId.MOV_TEXT, CodecId.PNG, CodecId.MJPEG, CodecId.UNKNOWN)
                                .contains(codec),
                        codec + " has no Matroska ID");
                continue;
            }
            var named = MatroskaCodecs.isPcm(id.get())
                    ? MatroskaCodecs.pcm(
                            id.get(),
                            switch (codec) {
                                case PCM_S24LE -> 24;
                                case PCM_S16LE -> 16;
                                default -> 32;
                            })
                    : MatroskaCodecs.of(id.get());
            assertEquals(codec, named.codec(), id.get());
        }
        assertEquals(new MatroskaCodecs.Named(CodecId.UNKNOWN, "rawvideo"), MatroskaCodecs.of("V_UNCOMPRESSED"));
    }

    @Test
    @DisplayName("a time before zero, and a frame before the header, are refused")
    void refused() {
        var writer = new MatroskaWriter(new ByteArrayOutputStream());
        assertThrows(IllegalStateException.class, () -> writer.writeFrame(new byte[1], 0, true));
        assertThrows(IllegalArgumentException.class, () -> {
            writer.writeHeader(new MatroskaWriter.Video("V_MPEG2", new byte[0], 0, 0));
            writer.writeFrame(new byte[1], -1, true);
        });
        assertThrows(
                IllegalStateException.class,
                () -> writer.writeHeader(new MatroskaWriter.Video("V_MPEG2", new byte[0], 0, 0)));
    }
}
