package dev.goldberry.media.container.mp4;

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

@DisplayName("Mp4Chapters")
class Mp4ChaptersTest {

    /// A `chpl` payload of `version`, from the version byte: the starts in
    /// 100 ns units, each with its title.
    private static byte[] chpl(int version, long[] units, String... titles) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) version, 0, 0, 0});
        if (version != 0) {
            out.writeBytes(new byte[4]);
        }
        out.write(units.length);
        for (var i = 0; i < units.length; i++) {
            for (var shift = 56; shift >= 0; shift -= 8) {
                out.write((int) (units[i] >>> shift));
            }
            var title = titles[i].getBytes(StandardCharsets.UTF_8);
            out.write(title.length);
            out.writeBytes(title);
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("chpl: both versions, starts in 100 ns, no ends")
    void chpl() {
        var expected = List.of(
                new Chapter(Duration.ZERO, Optional.empty(), Optional.of("Start")),
                new Chapter(Duration.ofMillis(1500), Optional.empty(), Optional.of("Später")));
        var units = new long[] {0, 15_000_000};
        assertEquals(expected, Mp4Chapters.chpl(chpl(1, units, "Start", "Später")));
        assertEquals(expected, Mp4Chapters.chpl(chpl(0, units, "Start", "Später")));
    }

    @Test
    @DisplayName("chpl: as many as were whole, and none from a payload too short for a count")
    void chplTruncated() {
        var whole = chpl(1, new long[] {0, 10_000_000}, "One", "Two");
        assertEquals(1, Mp4Chapters.chpl(Arrays.copyOf(whole, whole.length - 2)).size());
        assertEquals(List.of(), Mp4Chapters.chpl(new byte[] {1, 0, 0}));
    }

    @Test
    @DisplayName("text: UTF-8 after the length, UTF-16 by its mark, and styling boxes after it ignored")
    void text() {
        var utf8 = "Intro".getBytes(StandardCharsets.UTF_8);
        var sample = new ByteArrayOutputStream();
        sample.write(0);
        sample.write(utf8.length);
        sample.writeBytes(utf8);
        sample.writeBytes(new byte[] {0, 0, 0, 12, 'e', 'n', 'c', 'd', 0, 0, 1, 0});
        assertEquals(Optional.of("Intro"), Mp4Chapters.text(sample.toByteArray()));

        var utf16 = "\uFEFFZwei".getBytes(StandardCharsets.UTF_16BE);
        var wide = new byte[2 + utf16.length];
        wide[1] = (byte) utf16.length;
        System.arraycopy(utf16, 0, wide, 2, utf16.length);
        assertEquals(Optional.of("Zwei"), Mp4Chapters.text(wide));

        assertEquals(Optional.empty(), Mp4Chapters.text(new byte[] {0}));
        assertEquals(Optional.of("ab"), Mp4Chapters.text(new byte[] {0, 9, 'a', 'b'}), "a length past the end");
    }
}
