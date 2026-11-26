package dev.goldberry.vector;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import dev.goldberry.image.ImageDecodeException;

/// Checks the XML 1.0 character repertoire before a document goes to the native reader.
/// The reader still decides what to draw from the markup.
final class SvgText {

    private SvgText() {}

    static void check(byte[] bytes) {
        for (var i = 0; i < bytes.length; i++) {
            var b = bytes[i];
            if (b < 0) {
                checkUtf8(bytes, i);
                return;
            }
            requireCharacter(b);
        }
    }

    private static void checkUtf8(byte[] bytes, int start) {
        var input = ByteBuffer.wrap(bytes, start, bytes.length - start);
        var decoder = StandardCharsets.UTF_8.newDecoder();
        var chars = CharBuffer.allocate(Math.min(1024, input.remaining()));
        while (input.hasRemaining()) {
            var result = decoder.decode(input, chars, true);
            if (result.isError()) {
                throw new ImageDecodeException("invalid UTF-8 in the SVG document at byte " + input.position());
            }
            chars.flip();
            while (chars.hasRemaining()) {
                // The UTF-8 decoder has already verified any surrogate pair.
                var c = chars.get();
                if (!Character.isSurrogate(c)) {
                    requireCharacter(c);
                }
            }
            chars.clear();
        }
    }

    static void check(String text) {
        // String.getBytes would silently replace an unpaired surrogate.
        for (var i = 0; i < text.length(); ) {
            var codePoint = text.codePointAt(i);
            requireCharacter(codePoint);
            i += Character.charCount(codePoint);
        }
    }

    private static void requireCharacter(int c) {
        if ((c < 0x20 && c != '\t' && c != '\n' && c != '\r')
                || (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)
                || c == 0xFFFE
                || c == 0xFFFF) {
            throw new ImageDecodeException("character U+%04X is not allowed in XML 1.0".formatted(c));
        }
    }
}
