package dev.goldberry.media.bitstream;

/// What an Opus packet says of itself in its first byte, the TOC (RFC 6716
/// §3.1): how many samples it decodes to, which a container that states no
/// packet durations needs for its timestamps and lengths.
public final class OpusPackets {

    /// Opus decodes at this rate whatever the input was: every count here is in
    /// samples of it.
    public static final int SAMPLE_RATE = 48_000;

    private OpusPackets() {}

    /// The samples per channel `packet` holds, at 48 kHz, or 0 for a packet too
    /// short to say.
    public static int samples(byte[] packet) {
        if (packet.length == 0) {
            return 0;
        }
        var toc = packet[0] & 0xFF;
        var frames = switch (toc & 0x03) {
            case 0 -> 1;
            case 1, 2 -> 2;
            default -> packet.length > 1 ? packet[1] & 0x3F : 0;
        };
        return frames * frameSamples(toc >>> 3);
    }

    /// The samples of one frame for a TOC configuration (RFC 6716 table 2):
    /// SILK and hybrid modes have 10, 20, 40 and 60 ms frames; CELT 2.5, 5, 10
    /// and 20 ms.
    static int frameSamples(int config) {
        if (config < 12) {
            return switch (config & 0x03) {
                case 0 -> 480;
                case 1 -> 960;
                case 2 -> 1920;
                default -> 2880;
            };
        }
        if (config < 16) {
            return (config & 0x01) == 0 ? 480 : 960;
        }
        return switch (config & 0x03) {
            case 0 -> 120;
            case 1 -> 240;
            case 2 -> 480;
            default -> 960;
        };
    }
}
