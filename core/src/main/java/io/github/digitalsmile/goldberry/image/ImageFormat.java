package io.github.digitalsmile.goldberry.image;

import java.nio.ByteBuffer;
import java.util.Objects;

/// What a run of bytes is, decided by looking at them — `docs/gaps.md` G35a,
/// [ADR-0329].
///
/// ## Why the toolkit sniffs at all
///
/// Three of the five formats [Image#decode(byte[])] reads are the rasterizer's
/// own and it identifies them itself; two are not, because the rasterizer is
/// compiled without them. So something has to decide which decoder a run of bytes
/// goes to, and the only honest basis is the bytes: a file with the wrong
/// extension decodes anyway, and a `Content-Type` nobody set does not matter.
///
/// It reads at most twelve bytes and decodes nothing, so asking is free.
///
/// ## Naming a format and routing it are different things
///
/// [#PNG], [#JPEG] and [#QOI] are the rasterizer's own and are listed here
/// anyway, because this answers *what these bytes are* and that question has an
/// answer whoever decodes them. Only [#GIF] and [#WEBP] change where the bytes
/// go.
///
/// ## [#UNKNOWN] is not an error
///
/// It means "none of the signatures matched", which for a format this enum does
/// not list is the same answer. The bytes still go to the rasterizer, which has
/// the final word — the sniff decides *routing*, not validity, and a format added
/// to the rasterizer later needs no entry here.
public enum ImageFormat {

    /// `89 50 4E 47 0D 0A 1A 0A` — the signature designed to survive a
    /// text-mode transfer, which is why it is eight bytes.
    PNG,

    /// `FF D8 FF` — a JPEG's start-of-image followed by the first marker.
    JPEG,

    /// `GIF87a` or `GIF89a`. Decoded by
    /// [io.github.digitalsmile.goldberry.image.gif.GifDecoder], which is Java.
    GIF,

    /// `RIFF????WEBP`. Decoded by libwebp, which is linked into the same native
    /// library as the rasterizer.
    WEBP,

    /// `qoif` — the Quite OK Image format, a lossless codec of about three
    /// hundred lines that the rasterizer ships and nothing else here has to do
    /// anything about. Listed for [#PNG]'s reason: this names what the bytes are.
    QOI,

    /// Something else — a format nothing here lists. The rasterizer is asked, and
    /// it has the final word.
    UNKNOWN;

    /// What `bytes` look like. The buffer's position is not moved.
    public static ImageFormat of(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        var at = bytes.position();
        var length = bytes.remaining();
        if (length >= 8 && matches(bytes, at, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (length >= 3 && matches(bytes, at, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (length >= 6 && matches(bytes, at, 'G', 'I', 'F', '8')) {
            return GIF;
        }
        if (length >= 14 && matches(bytes, at, 'q', 'o', 'i', 'f')) {
            return QOI;
        }
        // RIFF, four bytes of container length that say nothing about the format,
        // then the form type. Both halves are needed: a WAV is RIFF too.
        if (length >= 12 && matches(bytes, at, 'R', 'I', 'F', 'F') && matches(bytes, at + 8, 'W', 'E', 'B', 'P')) {
            return WEBP;
        }
        return UNKNOWN;
    }

    private static boolean matches(ByteBuffer bytes, int at, int... signature) {
        for (var i = 0; i < signature.length; i++) {
            if ((bytes.get(at + i) & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
