package dev.goldberry.media.bitstream;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.codec.FrameRate;
import dev.goldberry.media.codec.Rational;

/// What a Theora stream's headers say before a decoder sees them: the
/// identification header (Theora I §6.2), read for a track's description and
/// for the granule positions of Ogg, whose meaning it sets.
///
/// Theora's three headers start with a type byte with its top bit set, `0x80`
/// for the identification, `0x81` for the comments and `0x82` for the setup,
/// then `theora`; a frame's first bit is clear. Ogg carries them as its first
/// three packets and Matroska Xiph-laced in `CodecPrivate`, and the decoder is
/// handed the laced form from both.
public final class TheoraHeaders {

    /// The identification header's type byte.
    public static final int IDENTIFICATION = 0x80;

    /// The comment header's type byte.
    public static final int COMMENT = 0x81;

    /// The setup header's type byte.
    public static final int SETUP = 0x82;

    /// The bytes before the comment header's Vorbis-style comments.
    public static final int COMMENT_OFFSET = 7;

    private static final byte[] MAGIC = "theora".getBytes(StandardCharsets.US_ASCII);

    /// The identification header's length: 42 bytes, the last two of which
    /// hold the quality, the granule shift and the pixel format in bits.
    private static final int IDENTIFICATION_SIZE = 42;

    /// Bitstream 3.2.1, from which a granule position counts frames from one;
    /// in 3.2.0 it counted them from zero.
    private static final int VERSION_3_2_1 = 0x030201;

    private TheoraHeaders() {}

    /// What the identification header states. Sizes in pixels, the picture's
    /// offset as the header writes it: `picYFromBottom` rows up from the bottom
    /// of the coded frame, which is Theora's own orientation.
    ///
    /// @param version        the bitstream version, `0x030201` for 3.2.1
    /// @param frameWidth     the coded width, a multiple of 16
    /// @param frameHeight    the coded height, a multiple of 16
    /// @param pictureWidth   the width shown
    /// @param pictureHeight  the height shown
    /// @param pictureX       the columns left of the picture
    /// @param picYFromBottom the rows below the picture
    /// @param fpsNumerator   frames, unsigned
    /// @param fpsDenominator in this many seconds, unsigned
    /// @param colorSpace     0 unspecified, 1 Rec. 470M, 2 Rec. 470BG
    /// @param pixelFormat    0 for 4:2:0, 2 for 4:2:2, 3 for 4:4:4
    /// @param granuleShift   the bits of a granule position below the keyframe
    public record Identification(
            int version,
            int frameWidth,
            int frameHeight,
            int pictureWidth,
            int pictureHeight,
            int pictureX,
            int picYFromBottom,
            long fpsNumerator,
            long fpsDenominator,
            int colorSpace,
            int pixelFormat,
            int granuleShift) {

        /// FFmpeg's name for the pixel format, or empty for the reserved one.
        public Optional<String> pixelFormatName() {
            return switch (pixelFormat) {
                case 0 -> Optional.of("yuv420p");
                case 2 -> Optional.of("yuv422p");
                case 3 -> Optional.of("yuv444p");
                default -> Optional.empty();
            };
        }

        /// The frame rate, when both halves are positive and fit an `int`.
        public Optional<FrameRate> frameRate() {
            if (fpsNumerator > Integer.MAX_VALUE || fpsDenominator > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            return FrameRate.known((int) fpsNumerator, (int) fpsDenominator);
        }

        /// One tick a frame: the time base of a Theora stream in Ogg, as FFmpeg
        /// sets it. A rate that is not stated, or does not fit, is taken as 25
        /// a second, which is what FFmpeg assumes too.
        public Rational timeBase() {
            return frameRate().map(TheoraHeaders::tick).orElseGet(() -> new Rational(1, 25));
        }

        /// The frames up to and including the one granule position `granule`
        /// names: the keyframe's number and the frames since it, which in
        /// bitstream 3.2.0 count from zero and so are one short.
        public long frames(long granule) {
            return keyframes(granule) + (granule & ((1L << granuleShift) - 1));
        }

        /// The number, counted from one, of the keyframe `granule` refers back
        /// to: the frame index of that keyframe plus one.
        public long keyframes(long granule) {
            return (granule >>> granuleShift) + (version < VERSION_3_2_1 ? 1 : 0);
        }
    }

    /// Whether `packet` is a Theora header of `type`.
    public static boolean isHeader(byte[] packet, int type) {
        return packet.length >= 1 + MAGIC.length
                && (packet[0] & 0xFF) == type
                && Arrays.equals(packet, 1, 1 + MAGIC.length, MAGIC, 0, MAGIC.length);
    }

    /// Whether `packet`, a frame of a Theora stream, is a keyframe: an intra
    /// frame says so in its second bit. An empty packet repeats the frame
    /// before, and is not one.
    public static boolean isKeyframe(byte[] packet) {
        return packet.length > 0 && (packet[0] & 0xC0) == 0;
    }

    /// The identification header `packet`.
    ///
    /// @throws IllegalArgumentException when it is not one, is cut short, or
    ///                                  states a bitstream other than 3.2.x
    public static Identification identification(byte[] packet) {
        if (!isHeader(packet, IDENTIFICATION)) {
            throw new IllegalArgumentException("not a Theora identification header");
        }
        if (packet.length < IDENTIFICATION_SIZE) {
            throw new IllegalArgumentException("a Theora identification header of " + packet.length + " bytes");
        }
        var version = (int) bigEndian(packet, 7, 3);
        if ((version >>> 8) != 0x0302) {
            throw new IllegalArgumentException("Theora bitstream " + (version >>> 16) + "." + (version >>> 8 & 0xFF)
                    + "." + (version & 0xFF) + ", not 3.2");
        }
        var frameWidth = (int) bigEndian(packet, 10, 2) << 4;
        var frameHeight = (int) bigEndian(packet, 12, 2) << 4;
        var pictureWidth = (int) bigEndian(packet, 14, 3);
        var pictureHeight = (int) bigEndian(packet, 17, 3);
        var pictureX = packet[20] & 0xFF;
        var pictureY = packet[21] & 0xFF;
        if (pictureWidth + pictureX > frameWidth || pictureHeight + pictureY > frameHeight) {
            throw new IllegalArgumentException("a Theora picture of " + pictureWidth + "×" + pictureHeight + " at "
                    + pictureX + "," + pictureY + " outside its " + frameWidth + "×" + frameHeight + " frame");
        }
        var bits = (int) bigEndian(packet, 40, 2);
        return new Identification(
                version,
                frameWidth,
                frameHeight,
                pictureWidth,
                pictureHeight,
                pictureX,
                pictureY,
                bigEndian(packet, 22, 4),
                bigEndian(packet, 26, 4),
                packet[36] & 0xFF,
                bits >>> 3 & 0x3,
                bits >>> 5 & 0x1F);
    }

    /// The identification header of a Xiph-laced record of the three headers,
    /// Matroska's `CodecPrivate`, or empty when the record is not that.
    public static Optional<Identification> identificationOfLaced(byte[] record) {
        try {
            List<byte[]> headers = XiphLacing.split(record);
            return Optional.of(identification(headers.getFirst()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static long bigEndian(byte[] bytes, int offset, int length) {
        var value = 0L;
        for (var i = 0; i < length; i++) {
            value = value << 8 | (bytes[offset + i] & 0xFF);
        }
        return value;
    }

    /// One frame of `rate` as a time base, reduced: `1/25` for 25 a second,
    /// `1001/30000` for NTSC's 29.97.
    private static Rational tick(FrameRate rate) {
        var a = rate.num();
        var b = rate.den();
        while (b != 0) {
            var t = a % b;
            a = b;
            b = t;
        }
        return new Rational(rate.den() / a, rate.num() / a);
    }
}
