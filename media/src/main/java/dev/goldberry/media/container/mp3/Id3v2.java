package dev.goldberry.media.container.mp3;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.container.ByteReader;

/// The ID3v2 tag at the start of an MP3: how far it reaches, which is what the
/// demuxer must skip, and the pictures it carries, which are the cover art.
///
/// Versions 2.2, 2.3 and 2.4 are read: the tag's syncsafe size and footer,
/// the extended header, unsynchronisation of the whole tag (2.3) or of a frame
/// (2.4), and the `APIC` frame, `PIC` in 2.2. Text frames such as the title
/// and the artist are passed over: nothing here has a place for them yet. A
/// frame that is compressed or encrypted is skipped. Every other frame is
/// skipped by its size, so an unknown one costs nothing.
final class Id3v2 {

    private static final int HEADER = 10;
    private static final int FLAG_UNSYNCHRONISED = 0x80;
    private static final int FLAG_EXTENDED_HEADER = 0x40;
    private static final int FLAG_FOOTER = 0x10;
    /// A tag longer than this is skipped unread: no cover is that big, and the
    /// whole tag is held on the heap while its frames are walked.
    private static final int LARGEST_READ = 64 * 1024 * 1024;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /// A picture an `APIC` frame carried.
    ///
    /// @param codec       [CodecId#PNG] or [CodecId#MJPEG]
    /// @param description the frame's description, when it has one
    /// @param data        the encoded picture
    @SuppressWarnings("ArrayRecordComponent")
    record Picture(CodecId codec, Optional<String> description, byte[] data) {}

    /// One tag, read.
    ///
    /// @param end      the stream position just after the tag, footer included
    /// @param pictures its pictures, in tag order
    record Tag(long end, List<Picture> pictures) {}

    private Id3v2() {}

    /// Whether a tag starts at `in`'s position. Looks one byte ahead where it
    /// can decide on that, since a frame header never starts with `I`, and
    /// reads the ten header bytes and steps back only when it cannot.
    static boolean starts(ByteReader in) {
        if (in.atEnd() || in.peek() != 'I') {
            return false;
        }
        var at = in.position();
        var head = new byte[3];
        for (var i = 0; i < head.length; i++) {
            if (in.atEnd()) {
                in.seek(at);
                return false;
            }
            head[i] = (byte) in.u8();
        }
        in.seek(at);
        return head[1] == 'D' && head[2] == '3';
    }

    /// Reads the tag at `in`'s position, leaving `in` after it.
    static Tag read(ByteReader in) {
        var header = in.bytes(HEADER);
        var major = header[3] & 0xFF;
        var flags = header[5] & 0xFF;
        var size = syncsafe(header, 6);
        var footer = (flags & FLAG_FOOTER) != 0 ? HEADER : 0;
        if (size > LARGEST_READ || major < 2 || major > 4) {
            in.skip(size + footer);
            return new Tag(in.position(), List.of());
        }
        var body = in.bytes(size);
        in.skip(footer);
        var end = in.position();
        var unsynchronised = (flags & FLAG_UNSYNCHRONISED) != 0;
        if (major == 2 && (flags & FLAG_EXTENDED_HEADER) != 0) {
            // In 2.2 that bit means the tag is compressed, which nobody wrote.
            return new Tag(end, List.of());
        }
        if (unsynchronised && major <= 3) {
            body = resynchronise(body);
        }
        var offset = 0;
        if ((flags & FLAG_EXTENDED_HEADER) != 0 && body.length >= 4) {
            offset = major == 3 ? 4 + (int) u32(body, 0) : syncsafe(body, 0);
        }
        var pictures = new ArrayList<Picture>();
        var idLength = major == 2 ? 3 : 4;
        var frameHeader = major == 2 ? 6 : 10;
        while (offset >= 0 && offset + frameHeader <= body.length && body[offset] != 0) {
            var id = new String(body, offset, idLength, StandardCharsets.ISO_8859_1);
            var frameSize = switch (major) {
                case 2 -> (body[offset + 3] & 0xFF) << 16 | (body[offset + 4] & 0xFF) << 8 | (body[offset + 5] & 0xFF);
                case 3 -> (int) u32(body, offset + 4);
                default -> syncsafe(body, offset + 4);
            };
            var dataStart = offset + frameHeader;
            if (frameSize < 0 || dataStart + frameSize > body.length) {
                break;
            }
            if (id.equals("APIC") || id.equals("PIC")) {
                var picture = picture(major, body, dataStart, frameSize, major == 2 ? 0 : body[offset + 9] & 0xFF);
                if (picture != null) {
                    pictures.add(picture);
                }
            }
            offset = dataStart + frameSize;
        }
        return new Tag(end, List.copyOf(pictures));
    }

    /// The picture of an `APIC` or `PIC` frame whose data is
    /// `body[start..start+size)`, or null when the frame cannot be read or
    /// holds neither a PNG nor a JPEG.
    private static @Nullable Picture picture(int major, byte[] body, int start, int size, int formatFlags) {
        var data = Arrays.copyOfRange(body, start, start + size);
        if (major == 3 && (formatFlags & 0xC0) != 0) {
            return null;
        }
        if (major == 4) {
            if ((formatFlags & 0x0C) != 0) {
                return null;
            }
            var skip = ((formatFlags & 0x40) != 0 ? 1 : 0) + ((formatFlags & 0x01) != 0 ? 4 : 0);
            if (skip >= data.length) {
                return null;
            }
            data = Arrays.copyOfRange(data, skip, data.length);
            if ((formatFlags & 0x02) != 0) {
                data = resynchronise(data);
            }
        } else if (major == 3 && (formatFlags & 0x20) != 0) {
            if (data.length < 1) {
                return null;
            }
            data = Arrays.copyOfRange(data, 1, data.length);
        }
        if (data.length < 2) {
            return null;
        }
        var encoding = data[0] & 0xFF;
        String mime;
        int at;
        if (major == 2) {
            if (data.length < 4) {
                return null;
            }
            mime = new String(data, 1, 3, StandardCharsets.ISO_8859_1);
            at = 4;
        } else {
            var nul = indexOf(data, 1, (byte) 0);
            if (nul < 0) {
                return null;
            }
            mime = new String(data, 1, nul - 1, StandardCharsets.ISO_8859_1);
            at = nul + 1;
        }
        if (at >= data.length) {
            return null;
        }
        at++; // the picture type: a front cover, a back cover, ...; every one is handed over
        var wide = encoding == 1 || encoding == 2;
        var descriptionEnd = wide ? indexOfWideNul(data, at) : indexOf(data, at, (byte) 0);
        if (descriptionEnd < 0) {
            return null;
        }
        var description = new String(data, at, descriptionEnd - at, charset(encoding)).strip();
        var pictureStart = descriptionEnd + (wide ? 2 : 1);
        if (pictureStart >= data.length) {
            return null;
        }
        var picture = Arrays.copyOfRange(data, pictureStart, data.length);
        var codec = codec(mime, picture);
        if (codec == null) {
            return null;
        }
        return new Picture(codec, description.isEmpty() ? Optional.empty() : Optional.of(description), picture);
    }

    /// The codec `mime` names, else the one the first bytes name, else null:
    /// a URL (`-->`) or a format the toolkit does not draw.
    private static @Nullable CodecId codec(String mime, byte[] picture) {
        var type = mime.toLowerCase(Locale.ROOT);
        if (type.equals("image/png") || type.equals("png")) {
            return CodecId.PNG;
        }
        if (type.equals("image/jpeg") || type.equals("image/jpg") || type.equals("jpg")) {
            return CodecId.MJPEG;
        }
        if (startsWith(picture, PNG_SIGNATURE)) {
            return CodecId.PNG;
        }
        if (startsWith(picture, JPEG_SIGNATURE)) {
            return CodecId.MJPEG;
        }
        return null;
    }

    private static Charset charset(int encoding) {
        return switch (encoding) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
    }

    /// `bytes` with the unsynchronisation undone: every `FF 00` is an `FF` the
    /// writer broke so that it would not look like a frame sync.
    static byte[] resynchronise(byte[] bytes) {
        var out = new ByteArrayOutputStream(bytes.length);
        for (var i = 0; i < bytes.length; i++) {
            out.write(bytes[i]);
            if ((bytes[i] & 0xFF) == 0xFF && i + 1 < bytes.length && bytes[i + 1] == 0) {
                i++;
            }
        }
        return out.toByteArray();
    }

    /// A 28-bit size in four bytes of seven bits each, so that no byte is `FF`.
    static int syncsafe(byte[] bytes, int at) {
        return (bytes[at] & 0x7F) << 21
                | (bytes[at + 1] & 0x7F) << 14
                | (bytes[at + 2] & 0x7F) << 7
                | (bytes[at + 3] & 0x7F);
    }

    private static long u32(byte[] bytes, int at) {
        return (bytes[at] & 0xFFL) << 24
                | (bytes[at + 1] & 0xFFL) << 16
                | (bytes[at + 2] & 0xFFL) << 8
                | (bytes[at + 3] & 0xFFL);
    }

    private static int indexOf(byte[] bytes, int from, byte value) {
        for (var i = from; i < bytes.length; i++) {
            if (bytes[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /// The first two-byte NUL at an even distance from `from`: the end of a
    /// UTF-16 string.
    private static int indexOfWideNul(byte[] bytes, int from) {
        for (var i = from; i + 1 < bytes.length; i += 2) {
            if (bytes[i] == 0 && bytes[i + 1] == 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
