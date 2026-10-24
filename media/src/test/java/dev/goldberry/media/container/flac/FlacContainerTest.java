package dev.goldberry.media.container.flac;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.Wav;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.decoder.FrameHashes;
import dev.goldberry.media.decoder.flac.FlacProvider;

@DisplayName("FlacContainer")
class FlacContainerTest {

    private static FlacContainer open(String name) {
        return FlacContainer.open(ClipFixtures.source(name), ClipFixtures.io(name));
    }

    private static List<ClipFixtures.Expected> drain(FlacContainer container) {
        var packets = new ArrayList<ClipFixtures.Expected>();
        for (var next = container.read(); next != null; next = container.read()) {
            try (var packet = next) {
                packets.add(new ClipFixtures.Expected(
                        packet.streamIndex(),
                        packet.pts(),
                        packet.duration(),
                        packet.data().byteSize(),
                        packet.keyframe()));
            }
        }
        return packets;
    }

    @Test
    @DisplayName("hands over the frames ffprobe listed: time, size and length, in order")
    void packetsAsFfprobe() {
        try (var container = open("tone.flac")) {
            assertEquals(new Rational(1, 48_000), container.timeBase(0));
            assertEquals(ClipFixtures.packets("tone.flac"), drain(container));
        }
    }

    @Test
    @DisplayName("describes the stream from STREAMINFO: FLAC, 48 kHz stereo 16-bit, one second")
    void describes() {
        try (var container = open("tone.flac")) {
            var info = container.info();
            assertEquals(Optional.of(Duration.ofSeconds(1)), info.duration());
            assertEquals(1, info.tracks().size());
            var track = info.tracks().getFirst();
            assertEquals(CodecId.FLAC, track.codec());
            var audio = assertInstanceOf(TrackParams.Audio.class, track.params());
            assertEquals(48_000, audio.sampleRate());
            assertEquals(2, audio.channels());
            assertEquals(Optional.of("s16"), audio.sampleFormat());
            var record = container.request(0).extradata().toArray(ValueLayout.JAVA_BYTE);
            assertEquals(42, record.length);
            assertEquals("fLaC", new String(record, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
            assertTrue(FlacContainer.isMagic(ClipFixtures.bytes("tone.flac")));
            assertFalse(FlacContainer.isMagic(ClipFixtures.bytes("tone.ogg")));
        }
    }

    @Test
    @DisplayName("seeks by the seek table to the frame at or before the target, and back to the start")
    void seeks() {
        try (var container = open("tone.flac")) {
            var all = drain(container);
            container.seek(Duration.ofMillis(500).toNanos());
            var tail = drain(container);
            assertFalse(tail.isEmpty());
            assertTrue(
                    tail.getFirst().pts() <= 24_000,
                    "landed at " + tail.getFirst().pts());
            assertEquals(all.subList(all.size() - tail.size(), all.size()), tail);
            container.seek(0);
            assertEquals(all, drain(container));
        }
    }

    @Test
    @DisplayName("a PICTURE block is the cover art: an attached-picture track, one PNG packet, sent first")
    void cover() {
        try (var container = open("tone-cover.flac")) {
            var tracks = container.info().tracks();
            assertEquals(2, tracks.size());
            assertTrue(tracks.get(1).attachedPicture());
            assertEquals(CodecId.PNG, tracks.get(1).codec());
            var first = container.read();
            assertNotNull(first);
            try (first) {
                assertEquals(1, first.streamIndex());
                var head = first.data().asSlice(0, 4).toArray(ValueLayout.JAVA_BYTE);
                assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, head);
            }
            assertEquals(
                    ClipFixtures.packets("tone-cover.flac").stream()
                            .filter(p -> p.stream() == 0)
                            .toList(),
                    drain(container));
            container.select(Set.of(0));
            container.seek(0);
            assertEquals(0, container.read().streamIndex(), "the picture is not sent when it is not selected");
        }
    }

    @Test
    @DisplayName("decodes through the FLAC decoder to every sample of the sine")
    void decodes() {
        MediaNativesRequirement.enforce();
        try (var container = open("tone.flac")) {
            var samples = new ArrayList<Short>();
            try (var decoder = new FlacProvider().open(container.request(0))) {
                FrameHashes.drain(container, decoder, frame -> {
                    var audio = assertInstanceOf(AudioFrame.class, frame);
                    var left = audio.planes().getFirst();
                    for (var i = 0; i < audio.samples(); i++) {
                        samples.add(left.get(ValueLayout.JAVA_SHORT, i * 2L));
                    }
                });
            }
            assertEquals(48_000, samples.size());
            for (var i = 0; i < samples.size(); i++) {
                assertEquals(Wav.sineSample(48_000, i, 440, 12_000), samples.get(i), "sample " + i);
            }
        }
    }

    @Test
    @DisplayName("the header's CRC-8 is FLAC's")
    void crc() {
        assertEquals(0, FrameScanner.crc8(new byte[] {0}, 0, 1));
        assertEquals(0x07, FrameScanner.crc8(new byte[] {1}, 0, 1));
        assertEquals(0xF4, FrameScanner.crc8("123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, 9));
    }
}
