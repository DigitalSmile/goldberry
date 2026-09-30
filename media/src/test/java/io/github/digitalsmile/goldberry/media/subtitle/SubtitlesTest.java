package io.github.digitalsmile.goldberry.media.subtitle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// Text subtitles, read in Java: SubRip and WebVTT files, and a container's
/// subtitle packets, down to plain lines.
@DisplayName("Subtitles")
class SubtitlesTest {

    private static Duration ms(long millis) {
        return Duration.ofMillis(millis);
    }

    @Nested
    @DisplayName("SubRip")
    class SubRip {

        @Test
        @DisplayName("reads numbered cues, with their tags taken out and their lines kept")
        void reads() {
            var cues = Subtitles.parse("""
                    1
                    00:00:01,000 --> 00:00:02,500
                    <i>Hello</i>, <font color="#ff0">world</font>!

                    2
                    00:00:03,000 --> 00:00:04,000 X1:10 X2:100 Y1:10 Y2:50
                    Two lines,
                    <b>one</b> cue &amp; more
                    """);
            assertEquals(
                    List.of(
                            new Cue(ms(1_000), ms(2_500), "Hello, world!"),
                            new Cue(ms(3_000), ms(4_000), "Two lines,\none cue & more")),
                    cues);
        }

        @Test
        @DisplayName("takes CRLF, long hours, short fractions, and cues out of order, and skips broken blocks")
        void tolerant() {
            var cues = Subtitles.parse("2\r\n101:00:00,5 --> 101:00:01,000\r\nLate\r\n\r\n"
                    + "broken\r\nno timing here\r\n\r\n"
                    + "1\r\n00:00:00,000 --> 00:00:01,000\r\nEarly\r\n\r\n"
                    + "3\r\n00:00:05,000 --> 00:00:04,000\r\nBackwards\r\n\r\n"
                    + "4\r\n00:00:06,000 --> 00:00:07,000\r\n\r\n");
            assertEquals(
                    List.of(
                            new Cue(ms(0), ms(1_000), "Early"),
                            new Cue(
                                    Duration.ofHours(101).plusMillis(500),
                                    Duration.ofHours(101).plusSeconds(1),
                                    "Late")),
                    cues);
        }
    }

    @Nested
    @DisplayName("WebVTT")
    class WebVtt {

        @Test
        @DisplayName("reads cues after the header, with or without an id or hours, and skips NOTE and STYLE blocks")
        void reads() {
            var cues = Subtitles.parse("""
                    WEBVTT - a test

                    NOTE this is a comment

                    STYLE
                    ::cue { color: yellow }

                    intro
                    00:01.000 --> 00:02.000 align:start line:0
                    <v Roger>Hi &lt;there&gt;</v>

                    01:00:00.000 --> 01:00:01.250
                    <c.yellow>Karaoke</c> <00:00:00.500>time
                    """);
            assertEquals(
                    List.of(
                            new Cue(ms(1_000), ms(2_000), "Hi <there>"),
                            new Cue(Duration.ofHours(1), Duration.ofHours(1).plusMillis(1_250), "Karaoke time")),
                    cues);
        }
    }

    @Test
    @DisplayName("detects the format by the header, then the name, then a timing line")
    void detects() {
        assertEquals(Optional.of(Subtitles.Format.WEBVTT), Subtitles.detect(Optional.empty(), "WEBVTT\n\n"));
        assertEquals(Optional.of(Subtitles.Format.WEBVTT), Subtitles.detect(Optional.of("a.VTT"), ""));
        assertEquals(Optional.of(Subtitles.Format.SUBRIP), Subtitles.detect(Optional.of("a.srt"), ""));
        assertEquals(
                Optional.of(Subtitles.Format.SUBRIP),
                Subtitles.detect(Optional.of("subs.txt"), "1\n00:00:01,000 --> 00:00:02,000\nx\n"));
        assertEquals(Optional.empty(), Subtitles.detect(Optional.of("notes.txt"), "just words"));
    }

    @Test
    @DisplayName("reads a file through any protocol, past a byte-order mark, and refuses what is not subtitles")
    void readsAFile() throws IOException {
        var srt = "﻿1\n00:00:01,000 --> 00:00:02,000\nÇa va?\n".getBytes(StandardCharsets.UTF_8);
        var cues = Subtitles.read(Source.of(URI.create("mem:///film.srt")), List.of(serving(srt)));
        assertEquals(List.of(new Cue(ms(1_000), ms(2_000), "Ça va?")), cues);
        var notes = "nothing timed".getBytes(StandardCharsets.UTF_8);
        assertThrows(
                IOException.class,
                () -> Subtitles.read(Source.of(URI.create("mem:///notes.txt")), List.of(serving(notes))));
    }

    @Nested
    @DisplayName("a container's packets")
    class Packets {

        private Optional<Cue> packet(CodecId codec, byte[] payload) {
            return Subtitles.fromPacket(codec, payload, ms(1_000), ms(2_000));
        }

        private byte[] utf8(String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("SubRip and WebVTT packets are the cue's text")
        void text() {
            assertEquals(
                    "Hello\nworld",
                    packet(CodecId.SUBRIP, utf8("<i>Hello</i>\r\nworld\0"))
                            .orElseThrow()
                            .text());
            assertEquals(
                    "Hi",
                    packet(CodecId.WEBVTT, utf8("<v Roger>Hi</v>"))
                            .orElseThrow()
                            .text());
        }

        @Test
        @DisplayName("an ASS event is its text field, with the overrides out and \\N as a break")
        void ass() {
            var cue = packet(CodecId.ASS, utf8("3,0,Default,Bob,0,0,0,,{\\i1}Hello,{\\c&H00FF00&} you\\Nthere"));
            assertEquals("Hello, you\nthere", cue.orElseThrow().text());
            assertEquals(
                    "No fields",
                    packet(CodecId.ASS, utf8("No fields")).orElseThrow().text());
        }

        @Test
        @DisplayName("an MP4 text sample is its length-prefixed text, and its style boxes are passed over")
        void movText() {
            var text = utf8("Tx3g");
            var payload = new byte[2 + text.length + 4];
            payload[1] = (byte) text.length;
            System.arraycopy(text, 0, payload, 2, text.length);
            assertEquals("Tx3g", packet(CodecId.MOV_TEXT, payload).orElseThrow().text());
            assertTrue(packet(CodecId.MOV_TEXT, new byte[] {0}).isEmpty());
        }

        @Test
        @DisplayName("nothing comes of a blank packet, one with no length of time, or a codec that is not text")
        void nothing() {
            assertTrue(packet(CodecId.SUBRIP, utf8("  \n ")).isEmpty());
            assertTrue(Subtitles.fromPacket(CodecId.SUBRIP, utf8("x"), ms(5), ms(5))
                    .isEmpty());
            assertTrue(packet(CodecId.OPUS, utf8("x")).isEmpty());
        }
    }

    @Test
    @DisplayName("a cue shows from its start up to its end, and is refused empty or backwards")
    void cue() {
        var cue = new Cue(ms(1_000), ms(2_000), "x");
        assertTrue(cue.showsAt(ms(1_000)));
        assertTrue(cue.showsAt(ms(1_999)));
        assertFalse(cue.showsAt(ms(2_000)));
        assertFalse(cue.showsAt(ms(999)));
        assertThrows(IllegalArgumentException.class, () -> new Cue(ms(2), ms(1), "x"));
        assertThrows(IllegalArgumentException.class, () -> new Cue(ms(1), ms(2), " "));
    }

    private static MediaIOProvider serving(byte[] bytes) {
        return new MediaIOProvider() {
            @Override
            public Set<String> schemes() {
                return Set.of("mem");
            }

            @Override
            public MediaIO open(Source source) {
                return new MemoryIO(bytes);
            }
        };
    }
}
