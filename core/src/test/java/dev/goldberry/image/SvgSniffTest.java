package dev.goldberry.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// `ImageFormat.SVG`: text with an `<svg>` root, and nothing that only looks like
/// one.
@DisplayName("ImageFormat.SVG")
class SvgSniffTest {

    private static ImageFormat of(String text) {
        return ImageFormat.of(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
                "<svg>",
                "\n\t <svg\nwidth=\"1\">",
                "\uFEFF<svg/>",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<svg/>",
                "<!-- Generator: an editor --><?xml-stylesheet href=\"a.css\"?><svg/>",
                "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/x>y.dtd\"><svg/>",
                "<!DOCTYPE svg [ <!ENTITY e \"<html>\"> ]>\n<svg/>",
            })
    @DisplayName("is an <svg> root after the prologue an editor writes")
    void svg(String text) {
        assertEquals(ImageFormat.SVG, of(text));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<!DOCTYPE html><html><body><svg></svg></body></html>",
                "<html><svg/></html>",
                "<?xml version=\"1.0\"?><feed><svg/></feed>",
                "<svgx/>",
                "<SVG/>",
                "<svg",
                "svg",
                "<!-- an unclosed comment <svg/>",
                "<?xml version=\"1.0\"",
                "",
            })
    @DisplayName("is not a page with an <svg> inside, another XML root, or text that only mentions it")
    void notSvg(String text) {
        assertEquals(ImageFormat.UNKNOWN, of(text));
    }

    @Test
    @DisplayName("does not read past 64 KiB of prologue")
    void limit() {
        var comment = "<!--" + "x".repeat(SvgSniff.LIMIT) + "-->";
        assertEquals(ImageFormat.UNKNOWN, of(comment + "<svg/>"));
        assertEquals(ImageFormat.SVG, of("<!--" + "x".repeat(1000) + "--><svg/>"));
    }

    @Test
    @DisplayName("reads from the buffer's position and leaves it there")
    void position() {
        var bytes = ByteBuffer.wrap("garbage<svg/>".getBytes(StandardCharsets.UTF_8));
        bytes.position(7);
        assertEquals(ImageFormat.SVG, ImageFormat.of(bytes));
        assertEquals(7, bytes.position());
    }

    @Test
    @DisplayName("leaves the binary formats as they were: a PNG signature is a PNG")
    void binary() {
        var png = ByteBuffer.wrap(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        assertEquals(ImageFormat.PNG, ImageFormat.of(png));
    }
}
