package dev.goldberry.vector.decode;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.image.Image;
import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.image.ImageFormat;
import dev.goldberry.image.scalable.VectorDecoders;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.vector.Svg;
import dev.goldberry.vector.VectorNativesRequirement;

/// The decoder this module provides, found by `:core` through the service
/// loader as an application finds it.
@DisplayName("SvgDecoder, as Image.decode's")
class SvgDecoderTest {

    private static final String HALVES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" width="8" height="4" viewBox="0 0 8 4">
              <rect x="0" y="0" width="8" height="2" fill="#ff0000"/>
              <rect x="0" y="2" width="8" height="2" fill="#0000ff"/>
            </svg>
            """;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
        VectorDecoders.use(null);
    }

    @Test
    @DisplayName("is the decoder :core finds on the path")
    void found() {
        assertInstanceOf(SvgDecoder.class, VectorDecoders.find().orElseThrow());
    }

    @Test
    @DisplayName("turns SVG bytes into an image at the natural size, carrying its document")
    void decodes() {
        var bytes = HALVES.getBytes(StandardCharsets.UTF_8);
        assertEquals(ImageFormat.SVG, ImageFormat.of(ByteBuffer.wrap(bytes)));
        var image = Image.decode(bytes);
        assertAll(
                () -> assertEquals(new PhysicalSize(8, 4), image.size()),
                () -> assertInstanceOf(Svg.class, image.scalable().orElseThrow()),
                () -> assertEquals(0xFFFF0000, image.argb(1, 0)),
                () -> assertEquals(0xFF0000FF, image.argb(1, 3)));
    }

    @Test
    @DisplayName("refuses a document ThorVG will not read with ImageDecodeException")
    void refuses() {
        var thrown = assertThrows(
                ImageDecodeException.class,
                () -> Image.decode("<svg width=\"4\" height=\"4\"".getBytes(StandardCharsets.UTF_8)));
        assertTrue(thrown.getMessage().contains("SVG"), thrown.getMessage());
    }
}
