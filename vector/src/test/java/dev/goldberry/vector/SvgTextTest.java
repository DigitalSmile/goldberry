package dev.goldberry.vector;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.image.ImageDecodeException;

class SvgTextTest {

    @Test
    void acceptsXmlCharactersThroughoutUnicode() {
        var characters = new int[] {9, 10, 13, 0x20, 0x7F, 0x85, 0xD7FF, 0xE000, 0xFFFD, 0x10000, 0x10FFFF};
        var text = "<svg><!--" + new String(characters, 0, characters.length) + "--></svg>";
        assertDoesNotThrow(() -> SvgText.check(text));
        assertDoesNotThrow(() -> SvgText.check(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aSupplementaryCharacterCanStraddleTwoDecoderBuffers() {
        var text = "東" + "x".repeat(1022) + "🍋" + "y".repeat(1024);
        assertDoesNotThrow(() -> SvgText.check(text.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, 11, 12, 14, 31, 0xFFFE, 0xFFFF})
    void refusesCharactersOutsideXml10InTextAndBytes(int c) {
        var document = "<svg><!--" + Character.toString(c) + "--></svg>";
        var textError = assertThrows(ImageDecodeException.class, () -> Svg.parse(document));
        assertTrue(textError.getMessage().contains("XML 1.0"));
        var bytes = ByteBuffer.wrap(document.getBytes(StandardCharsets.UTF_8));
        var byteError = assertThrows(ImageDecodeException.class, () -> Svg.parse(bytes));
        assertEquals(textError.getMessage(), byteError.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0xD800, 0xDBFF, 0xDC00, 0xDFFF})
    void refusesAnUnpairedJavaSurrogate(int c) {
        var text = "<svg><!--" + (char) c + "--></svg>";
        var error = assertThrows(ImageDecodeException.class, () -> Svg.parse(text));
        assertTrue(error.getMessage().contains("XML 1.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"80", "c0af", "c2", "e08080", "eda080", "f4908080", "ff"})
    void refusesMalformedUtf8InTheSelectedBufferRange(String hex) {
        var bad = HexFormat.of().parseHex(hex);
        var buffer = ByteBuffer.allocateDirect(1 + "<svg>".length() + bad.length + 1);
        buffer.position(1)
                .put("<svg>".getBytes(StandardCharsets.UTF_8))
                .put(bad)
                .flip()
                .position(1);
        var error = assertThrows(ImageDecodeException.class, () -> Svg.parse(buffer));
        assertTrue(error.getMessage().contains("UTF-8"));
        assertEquals(1, buffer.position());
    }
}
