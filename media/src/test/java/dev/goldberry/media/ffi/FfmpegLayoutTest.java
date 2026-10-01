package dev.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FfmpegLayout")
class FfmpegLayoutTest {

    private static FfmpegLayout parse(String text) throws IOException {
        return FfmpegLayout.parse(new StringReader(text));
    }

    @Test
    @DisplayName("reads the four kinds of key, skipping comments and blank lines")
    void reads() throws IOException {
        var layout = parse("""
                # a comment

                version.avcodec.major=62
                struct.AVPacket.sizeof=104
                struct.AVPacket.alignof=8
                field.AVPacket.pts.offset=8
                field.AVPacket.pts.sizeof=8
                const.AVERROR_EAGAIN=-35
                """);
        assertEquals(Optional.of(62), layout.major(FfmpegLibrary.AVCODEC));
        assertEquals(Optional.of(new FfmpegLayout.Extent(104, 8)), layout.struct("AVPacket"));
        assertEquals(Optional.of(new FfmpegLayout.Extent(8, 8)), layout.field("AVPacket", "pts"));
        assertEquals(OptionalLong.of(-35), layout.constant("AVERROR_EAGAIN"));
        assertEquals(OptionalLong.empty(), layout.constant("AVERROR_EOF"));
    }

    @Test
    @DisplayName("splits a field key on its last dot, so a field name may contain one")
    void dottedField() throws IOException {
        var layout = parse("field.AVChannelLayout.u.mask.offset=8\nfield.AVChannelLayout.u.mask.sizeof=8\n");
        assertEquals(Optional.of(new FfmpegLayout.Extent(8, 8)), layout.field("AVChannelLayout", "u.mask"));
    }

    @Test
    @DisplayName("keeps the probe's order")
    void order() throws IOException {
        var layout = parse("const.B=2\nconst.A=1\n");
        assertEquals(List.of("B", "A"), List.copyOf(layout.constants().keySet()));
    }

    @Test
    @DisplayName("refuses a malformed line, naming its number")
    void malformed() {
        var noValue = assertThrows(IllegalArgumentException.class, () -> parse("const.A=1\nnonsense\n"));
        assertTrue(noValue.getMessage().contains("line 2"), noValue.getMessage());
        assertThrows(IllegalArgumentException.class, () -> parse("const.A=x\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("thing.A=1\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("version.avcodec=62\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("field.S.a.alignof=4\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("=4\n"));
    }

    @Test
    @DisplayName("refuses a struct or field with only half of its pair")
    void halves() {
        var error = assertThrows(IllegalArgumentException.class, () -> parse("struct.S.sizeof=4\n"));
        assertTrue(error.getMessage().contains("only half of S"), error.getMessage());
    }

    @Test
    @DisplayName("the committed fixture parses and has every constant the Engine reads")
    void fixture() {
        var constants = LayoutFixture.constants();
        assertEquals(1_000_000L, constants.timeBase());
        assertEquals(Long.MIN_VALUE, constants.noPtsValue());
        assertEquals(0x10000, constants.avseekSize());
        // -35 on macOS; the value that makes reading it necessary.
        assertEquals(-35, constants.averrorEagain());
    }

    @Test
    @DisplayName("constants name every one the layout lacks, together")
    void missingConstants() throws IOException {
        var error = assertThrows(
                IllegalArgumentException.class, () -> FfmpegConstants.from(parse("const.AVERROR_EOF=-1\n")));
        assertTrue(error.getMessage().contains("AVERROR_EXIT"), error.getMessage());
        assertTrue(error.getMessage().contains("AV_LOG_WARNING"), error.getMessage());
    }
}
