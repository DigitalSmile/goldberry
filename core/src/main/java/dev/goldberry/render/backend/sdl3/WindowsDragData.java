package dev.goldberry.render.backend.sdl3;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.goldberry.image.Image;
import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.natives.desktop.dragout.OleFormat;
import dev.goldberry.render.dragout.OfferedData;

/// An offer in Windows' clipboard formats, the way Explorer, browsers and
/// Office read a drag:
///
/// - files as `CF_HDROP`, a `DROPFILES` and the paths in UTF-16;
/// - the first link as `UniformResourceLocatorW`, which browsers take as a
///   link rather than as text;
/// - text, or the links one per line where there is none, as
///   `CF_UNICODETEXT` with Windows' line ends;
/// - the image as `PNG`, the registered format Office and browsers read, and
///   as `CF_DIBV5` for everything older;
/// - the application's own MIME types, each registered under its own name.
///
/// Everything but the paths is made when a target asks for it.
final class WindowsDragData {

    /// The registered format browsers read a link from.
    static final String URL = "UniformResourceLocatorW";

    /// The registered format Office and browsers read a PNG from.
    static final String PNG = "PNG";

    /// `sizeof(DROPFILES)`: `pFiles`, a `POINT`, `fNC`, `fWide`.
    static final int DROP_FILES_SIZE = 20;

    /// `sizeof(BITMAPV5HEADER)`.
    static final int BITMAP_V5_HEADER_SIZE = 124;

    /// `BI_BITFIELDS`: the channel masks in the header say where each is.
    private static final int BITFIELDS = 3;

    /// `LCS_sRGB`.
    private static final int SRGB = 0x73524742;

    /// `LCS_GM_IMAGES`.
    private static final int FOR_IMAGES = 4;

    private WindowsDragData() {}

    /// The formats of `offer`, most specific first. Its image should already
    /// be written to a file, so that Explorer, which takes only files, can
    /// take it.
    static List<OleFormat> formats(DragOffer offer) {
        var formats = new ArrayList<OleFormat>();
        var data = OfferedData.of(offer);
        if (!offer.files().isEmpty()) {
            var files = dropFiles(offer.files());
            formats.add(OleFormat.standard(OleFormat.FILES, () -> files));
        }
        if (!offer.links().isEmpty()) {
            var link = offer.links().getFirst();
            formats.add(OleFormat.registered(URL, () -> unicode(link.toString())));
        }
        if (offer.text() != null || !offer.links().isEmpty()) {
            formats.add(OleFormat.standard(OleFormat.UNICODE_TEXT, () -> unicode(windowsLines(data.text()))));
        }
        var image = offer.image();
        if (image != null) {
            formats.add(OleFormat.registered(PNG, image));
            formats.add(OleFormat.standard(OleFormat.BITMAP_V5, () -> bitmap(Image.decode(image.get()))));
        }
        for (var entry : offer.data().entrySet()) {
            formats.add(OleFormat.registered(entry.getKey(), entry.getValue()));
        }
        return formats;
    }

    /// `text` in UTF-16, little endian, with the NUL C expects after it.
    static byte[] unicode(String text) {
        return (text + '\0').getBytes(StandardCharsets.UTF_16LE);
    }

    /// `text` with every line ended as Windows ends one.
    static String windowsLines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n");
    }

    /// A `DROPFILES` for `paths`: wide paths after the header, each ended by a
    /// NUL, and the list by one more.
    static byte[] dropFiles(List<Path> paths) {
        var names = new ByteArrayOutputStream();
        for (var path : paths) {
            names.writeBytes(unicode(path.toString()));
        }
        names.writeBytes(new byte[2]);
        var header = ByteBuffer.allocate(DROP_FILES_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(DROP_FILES_SIZE); // pFiles: the paths start after the header
        header.putInt(0).putInt(0); // pt
        header.putInt(0); // fNC
        header.putInt(1); // fWide
        var bytes = new byte[DROP_FILES_SIZE + names.size()];
        System.arraycopy(header.array(), 0, bytes, 0, DROP_FILES_SIZE);
        System.arraycopy(names.toByteArray(), 0, bytes, DROP_FILES_SIZE, names.size());
        return bytes;
    }

    /// `image` as a `CF_DIBV5`.
    static byte[] bitmap(Image image) {
        var pixels = image.pixels();
        return dibV5(pixels.size().width(), pixels.size().height(), pixels.stride(), pixels.pixels());
    }

    /// A `BITMAPV5HEADER` and the pixels after it: bottom-up rows, as most
    /// readers expect, BGRA with the masks saying so, and alpha straight,
    /// since a DIB's colour channels are not premultiplied.
    ///
    /// @param premultiplied premultiplied BGRA rows of `stride` bytes, the
    ///                      toolkit's own pixels
    static byte[] dibV5(int width, int height, int stride, ByteBuffer premultiplied) {
        var size = width * height * 4;
        var out = ByteBuffer.allocate(BITMAP_V5_HEADER_SIZE + size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(BITMAP_V5_HEADER_SIZE);
        out.putInt(width);
        out.putInt(height); // positive: the last row first
        out.putShort((short) 1);
        out.putShort((short) 32);
        out.putInt(BITFIELDS);
        out.putInt(size);
        out.putInt(0).putInt(0).putInt(0).putInt(0); // resolution and palette
        out.putInt(0x00FF0000).putInt(0x0000FF00).putInt(0x000000FF).putInt(0xFF000000);
        out.putInt(SRGB);
        out.position(out.position() + 36 + 12); // endpoints and gamma, unused for sRGB
        out.putInt(FOR_IMAGES);
        out.putInt(0).putInt(0).putInt(0); // no profile, reserved
        var source = premultiplied.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        var base = source.position();
        for (var row = height - 1; row >= 0; row--) {
            for (var column = 0; column < width; column++) {
                var at = base + row * stride + column * 4;
                var alpha = source.get(at + 3) & 0xFF;
                for (var channel = 0; channel < 3; channel++) {
                    var value = source.get(at + channel) & 0xFF;
                    out.put((byte) (alpha == 0 ? 0 : Math.min(255, (value * 255 + alpha / 2) / alpha)));
                }
                out.put((byte) alpha);
            }
        }
        return out.array();
    }
}
