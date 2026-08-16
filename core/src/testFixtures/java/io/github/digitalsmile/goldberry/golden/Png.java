package io.github.digitalsmile.goldberry.golden;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/// A PNG reader and writer, in `java.base`.
///
/// `ImageIO` would do this in two lines and lives in `java.desktop` — a module
/// the toolkit does not require and should not start requiring for a test
/// helper. Goldberry's whole point is that it does not go through AWT
/// (ADR-0003); a golden-image harness that drags AWT in to compare its output
/// would be a strange thing to have.
///
/// Deliberately the narrowest PNG that is still a real PNG: 8-bit RGBA, no
/// interlacing, one `IDAT`. That is what is written, and — because a golden file
/// is only ever one this class wrote — it is all that has to be read.
final class Png {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private Png() {
    }

    /// An image as `0xAARRGGBB` pixels, row-major.
    record Image(int width, int height, int[] argb) {

        int pixel(int x, int y) {
            return argb[y * width + x];
        }
    }

    static void write(Path file, Image image) {
        try {
            Files.createDirectories(file.getParent());
            var png = new ByteArrayOutputStream();
            png.write(SIGNATURE);

            var header = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
            header.putInt(image.width());
            header.putInt(image.height());
            header.put((byte) 8);   // bit depth
            header.put((byte) 6);   // colour type: RGBA
            header.put((byte) 0);   // deflate
            header.put((byte) 0);   // adaptive filtering
            header.put((byte) 0);   // no interlace
            chunk(png, "IHDR", header.array());

            chunk(png, "IDAT", deflate(scanlines(image)));
            chunk(png, "IEND", new byte[0]);

            Files.write(file, png.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
    }

    static Image read(Path file) {
        try {
            var bytes = Files.readAllBytes(file);
            var in = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            in.position(SIGNATURE.length);

            var width = 0;
            var height = 0;
            var idat = new ByteArrayOutputStream();
            while (in.remaining() >= 8) {
                var length = in.getInt();
                var type = new byte[4];
                in.get(type);
                var name = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
                var data = new byte[length];
                in.get(data);
                in.getInt(); // CRC, not verified: this file is one we wrote.

                switch (name) {
                    case "IHDR" -> {
                        var header = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
                        width = header.getInt();
                        height = header.getInt();
                    }
                    case "IDAT" -> idat.write(data);
                    default -> { }
                }
            }
            return fromScanlines(width, height, inflate(idat.toByteArray(), height * (width * 4 + 1)));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    /// Each row prefixed with its filter byte. Filter 0 — none — throughout:
    /// a golden file is written once and read by a test, so a smaller file is
    /// worth less than a simpler one that cannot be wrong.
    private static byte[] scanlines(Image image) {
        var out = new byte[image.height() * (image.width() * 4 + 1)];
        var at = 0;
        for (var y = 0; y < image.height(); y++) {
            out[at++] = 0;
            for (var x = 0; x < image.width(); x++) {
                var argb = image.pixel(x, y);
                out[at++] = (byte) (argb >>> 16);
                out[at++] = (byte) (argb >>> 8);
                out[at++] = (byte) argb;
                out[at++] = (byte) (argb >>> 24);
            }
        }
        return out;
    }

    private static Image fromScanlines(int width, int height, byte[] raw) {
        var argb = new int[width * height];
        var at = 0;
        for (var y = 0; y < height; y++) {
            var filter = raw[at++];
            if (filter != 0) {
                throw new IllegalStateException("golden PNGs are written unfiltered, got filter " + filter);
            }
            for (var x = 0; x < width; x++) {
                var r = raw[at++] & 0xFF;
                var g = raw[at++] & 0xFF;
                var b = raw[at++] & 0xFF;
                var a = raw[at++] & 0xFF;
                argb[y * width + x] = a << 24 | r << 16 | g << 8 | b;
            }
        }
        return new Image(width, height, argb);
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        var name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.length).array());
        out.write(name);
        out.write(data);

        var crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int) crc.getValue()).array());
    }

    private static byte[] deflate(byte[] data) {
        var deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            var out = new ByteArrayOutputStream();
            var chunk = new byte[16384];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] data, int expected) {
        var inflater = new Inflater();
        try {
            inflater.setInput(data);
            var out = new byte[expected];
            var at = 0;
            while (at < expected && !inflater.finished()) {
                var read = inflater.inflate(out, at, expected - at);
                if (read == 0) {
                    break;
                }
                at += read;
            }
            return out;
        } catch (java.util.zip.DataFormatException e) {
            throw new IllegalStateException("corrupt golden PNG", e);
        } finally {
            inflater.end();
        }
    }
}
