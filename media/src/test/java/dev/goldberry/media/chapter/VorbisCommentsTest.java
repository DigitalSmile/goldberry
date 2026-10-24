package dev.goldberry.media.chapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.Chapter;

@DisplayName("VorbisComments")
class VorbisCommentsTest {

    /// A comment block: the vendor, the count and the comments, little-endian.
    private static byte[] block(String vendor, String... comments) {
        var out = new ByteArrayOutputStream();
        writeString(out, vendor);
        writeInt(out, comments.length);
        for (var comment : comments) {
            writeString(out, comment);
        }
        return out.toByteArray();
    }

    private static void writeString(ByteArrayOutputStream out, String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >> 8);
        out.write(value >> 16);
        out.write(value >> 24);
    }

    @Test
    @DisplayName("parse: the comments after the vendor, from an offset")
    void parse() {
        var block = block("vendor", "TITLE=Tone", "ARTIST=Nobody");
        var prefixed = new byte[block.length + 3];
        System.arraycopy(block, 0, prefixed, 3, block.length);
        assertEquals(List.of("TITLE=Tone", "ARTIST=Nobody"), VorbisComments.parse(prefixed, 3));
    }

    @Test
    @DisplayName("parse: a block cut short keeps the whole comments, one with a bad vendor none")
    void truncated() {
        var block = block("v", "A=1", "B=2");
        assertEquals(List.of("A=1"), VorbisComments.parse(Arrays.copyOf(block, block.length - 2), 0));
        var lying = block.clone();
        lying[0] = (byte) 0xFF;
        assertEquals(List.of(), VorbisComments.parse(lying, 0));
        assertEquals(List.of(), VorbisComments.parse(new byte[2], 0));
    }

    @Test
    @DisplayName("chapters: numbers pair starts with names, in any order and case")
    void chapters() {
        var chapters = VorbisComments.chapters(List.of(
                "chapter002name=Third",
                "CHAPTER001=00:00:30.5",
                "CHAPTER000=00:00:00.000",
                "CHAPTER000NAME=First",
                "CHAPTER002=01:02:03.250",
                "TITLE=not a chapter"));
        assertEquals(
                List.of(
                        new Chapter(Duration.ZERO, Optional.empty(), Optional.of("First")),
                        new Chapter(Duration.ofMillis(30_500), Optional.empty(), Optional.empty()),
                        new Chapter(Duration.ofMillis(3_723_250), Optional.empty(), Optional.of("Third"))),
                chapters);
    }

    @Test
    @DisplayName("chapters: a start that does not parse drops its chapter")
    void badStart() {
        assertEquals(
                List.of(), VorbisComments.chapters(List.of("CHAPTER001=soon", "CHAPTER001NAME=Never", "CHAPTER9=")));
    }

    @Test
    @DisplayName("time: HH:MM:SS, MM:SS and SS, with up to nine digits of fraction")
    void time() {
        assertEquals(Optional.of(Duration.ofSeconds(3723)), VorbisComments.time("1:02:03"));
        assertEquals(Optional.of(Duration.ofSeconds(125)), VorbisComments.time("02:05"));
        assertEquals(Optional.of(Duration.ofMillis(7_100)), VorbisComments.time("7.1"));
        assertEquals(Optional.of(Duration.ofNanos(1_000_000_001)), VorbisComments.time("00:00:01.000000001"));
        assertEquals(Optional.empty(), VorbisComments.time("1:2:3:4"));
        assertEquals(Optional.empty(), VorbisComments.time(""));
    }
}
