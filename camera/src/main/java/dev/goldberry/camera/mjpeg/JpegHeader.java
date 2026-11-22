package dev.goldberry.camera.mjpeg;

import java.nio.ByteBuffer;

import dev.goldberry.image.ImageDecodeException;

/// What the markers before a JPEG's first scan say: where the scan starts,
/// and whether the frame defines its Huffman tables.
///
/// Only the segments' markers and lengths are read, so it costs a few dozen
/// reads a frame, whatever the frame's size.
///
/// @param scanOffset            where the first scan's `FF DA` marker is,
///                              counted from the buffer's position
/// @param definesHuffmanTables  whether a DHT segment comes before it
public record JpegHeader(int scanOffset, boolean definesHuffmanTables) {

    private static final int MARKER = 0xFF;
    private static final int START_OF_IMAGE = 0xD8;
    private static final int END_OF_IMAGE = 0xD9;
    private static final int START_OF_SCAN = 0xDA;
    private static final int HUFFMAN_TABLES = 0xC4;
    private static final int TEMPORARY = 0x01;
    private static final int FIRST_RESTART = 0xD0;
    private static final int LAST_RESTART = 0xD7;

    public JpegHeader {
        if (scanOffset < 2) {
            throw new IllegalArgumentException("a scan at " + scanOffset);
        }
    }

    /// Reads the markers of the JPEG from `jpeg`'s position to its limit,
    /// leaving the buffer as it was.
    ///
    /// @throws ImageDecodeException when the bytes do not start a JPEG, or end
    ///         before a scan
    public static JpegHeader read(ByteBuffer jpeg) {
        var base = jpeg.position();
        var end = jpeg.limit();
        if (end - base < 4 || (jpeg.get(base) & 0xFF) != MARKER || (jpeg.get(base + 1) & 0xFF) != START_OF_IMAGE) {
            throw new ImageDecodeException("a camera frame of " + (end - base) + " bytes that is not a JPEG");
        }
        var tables = false;
        var at = base + 2;
        while (at < end) {
            if ((jpeg.get(at) & 0xFF) != MARKER) {
                throw new ImageDecodeException("a camera frame with no marker at byte " + (at - base));
            }
            var segment = at;
            // Any number of fill bytes may come before a marker.
            while (at < end && (jpeg.get(at) & 0xFF) == MARKER) {
                at++;
            }
            if (at >= end) {
                break;
            }
            var marker = jpeg.get(at) & 0xFF;
            at++;
            switch (marker) {
                case START_OF_SCAN -> {
                    return new JpegHeader(segment - base, tables);
                }
                case START_OF_IMAGE, END_OF_IMAGE ->
                    throw new ImageDecodeException("a camera frame that ends before its first scan");
                default -> {
                    if (marker == TEMPORARY || (marker >= FIRST_RESTART && marker <= LAST_RESTART)) {
                        // Markers with no segment after them.
                        continue;
                    }
                    if (at + 2 > end) {
                        throw new ImageDecodeException("a camera frame that ends inside its header");
                    }
                    var length = ((jpeg.get(at) & 0xFF) << 8) | (jpeg.get(at + 1) & 0xFF);
                    if (length < 2) {
                        throw new ImageDecodeException(
                                "a camera frame with a segment of length " + length + " at byte " + (segment - base));
                    }
                    tables |= marker == HUFFMAN_TABLES;
                    at += length;
                }
            }
        }
        throw new ImageDecodeException("a camera frame of " + (end - base) + " bytes with no scan");
    }
}
