package dev.goldberry.media.container.mp3;

import org.jspecify.annotations.Nullable;

/// The four bytes every MPEG audio frame starts with: eleven set bits, then
/// the version, layer, bit rate, sample rate, padding and channel mode that
/// say how long the frame is and how many samples it decodes to.
///
/// A header is parsed from its 32-bit word; one whose fields are reserved
/// values, or whose bit rate is "free format", is not a header here, which is
/// what a demuxer resyncing through garbage needs. FFmpeg's reader refuses
/// the same words.
///
/// @param version    which MPEG the frame is
/// @param layer      1, 2 or 3
/// @param bitRate    bits per second
/// @param sampleRate samples per second per channel
/// @param channels   1 or 2
/// @param padding    whether the frame carries one extra slot
/// @param crc        whether a 16-bit CRC follows the header
record FrameHeader(
        Version version, int layer, int bitRate, int sampleRate, int channels, boolean padding, boolean crc) {

    /// MPEG-1, MPEG-2 and the unofficial MPEG-2.5, which halves MPEG-2's rates
    /// again.
    enum Version {
        MPEG_1(44_100),
        MPEG_2(22_050),
        MPEG_2_5(11_025);

        /// The version's lowest-numbered sample rate, 44.1 kHz or a half or a
        /// quarter of it; the other two are 48 and 32 kHz scaled the same.
        private final int baseRate;

        Version(int baseRate) {
            this.baseRate = baseRate;
        }

        /// The sample rate the header's two bits name.
        int sampleRate(int index) {
            var scale = 44_100 / baseRate;
            return switch (index) {
                case 0 -> baseRate;
                case 1 -> 48_000 / scale;
                default -> 32_000 / scale;
            };
        }
    }

    /// Bit rates in kbit/s by table: MPEG-1 layers I, II, III; MPEG-2 and 2.5
    /// layer I, and layers II and III, which share one.
    private static final int[][] BIT_RATES = {
        {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
    };

    /// The header `word` holds, big-endian, or null when the word is not one:
    /// no sync, a reserved version, layer, bit rate or sample rate, or a free
    /// format bit rate.
    static @Nullable FrameHeader parse(long word) {
        if ((word & 0xFFE0_0000L) != 0xFFE0_0000L) {
            return null;
        }
        var versionBits = (int) (word >> 19) & 3;
        var layerBits = (int) (word >> 17) & 3;
        var bitRateIndex = (int) (word >> 12) & 15;
        var sampleRateIndex = (int) (word >> 10) & 3;
        if (versionBits == 1 || layerBits == 0 || bitRateIndex == 0 || bitRateIndex == 15 || sampleRateIndex == 3) {
            return null;
        }
        var version = switch (versionBits) {
            case 3 -> Version.MPEG_1;
            case 2 -> Version.MPEG_2;
            default -> Version.MPEG_2_5;
        };
        var layer = 4 - layerBits;
        var table = version == Version.MPEG_1 ? layer - 1 : layer == 1 ? 3 : 4;
        var bitRate = BIT_RATES[table][bitRateIndex] * 1000;
        var sampleRate = version.sampleRate(sampleRateIndex);
        var mode = (int) (word >> 6) & 3;
        return new FrameHeader(
                version,
                layer,
                bitRate,
                sampleRate,
                mode == 3 ? 1 : 2,
                ((word >> 9) & 1) != 0,
                ((word >> 16) & 1) == 0);
    }

    /// How many samples per channel the frame decodes to: 384 for layer I,
    /// 1152 for layer II and for MPEG-1 layer III, 576 for MPEG-2 and 2.5
    /// layer III.
    int samples() {
        if (layer == 1) {
            return 384;
        }
        if (layer == 2 || version == Version.MPEG_1) {
            return 1152;
        }
        return 576;
    }

    /// The frame's length in bytes, header and padding included: the way
    /// FFmpeg's header parser counts it, so the two find the same next frame.
    int bytes() {
        if (layer == 1) {
            return (12 * bitRate / sampleRate + (padding ? 1 : 0)) * 4;
        }
        return samples() / 8 * bitRate / sampleRate + (padding ? 1 : 0);
    }

    /// How many bytes of side information follow the header (and the CRC) in a
    /// layer III frame, which is where a Xing or Info tag starts.
    int sideInfoBytes() {
        if (version == Version.MPEG_1) {
            return channels == 1 ? 17 : 32;
        }
        return channels == 1 ? 9 : 17;
    }

    /// Whether `other` belongs to the same stream as this header: the same
    /// version, layer and sample rate. The bit rate, padding and channel mode
    /// may change from frame to frame.
    boolean sameStream(FrameHeader other) {
        return version == other.version && layer == other.layer && sampleRate == other.sampleRate;
    }

    /// FFmpeg's name for the codec: `mp1`, `mp2` or `mp3`.
    String codecName() {
        return "mp" + layer;
    }
}
