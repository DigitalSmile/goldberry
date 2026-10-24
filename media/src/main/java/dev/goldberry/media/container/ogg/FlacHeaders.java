package dev.goldberry.media.container.ogg;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/// What a container reads of a FLAC stream for itself: the `STREAMINFO`
/// block, for the rate, the channels and the bit depth, and the start of a
/// frame's header, for the frame's block size, which is how long a packet
/// is.
///
/// Both are the FLAC format (RFC 9639 §8.2 and §9.1). The frame header is
/// read only as far as the block size: the sync code and the two bytes after
/// it, the coded frame number that is skipped, and the block size that
/// follows them when the code says it does.
public final class FlacHeaders {

    /// The marker a FLAC stream begins with, which Ogg's mapping header carries
    /// too.
    static final byte[] MARKER = "fLaC".getBytes(StandardCharsets.US_ASCII);

    /// `STREAMINFO`'s own length.
    static final int STREAM_INFO_SIZE = 34;

    private FlacHeaders() {}

    /// What `STREAMINFO` states.
    ///
    /// @param rate         samples per second
    /// @param channels     the channel count
    /// @param bitsPerSample the bit depth
    /// @param totalSamples the sample count, or 0 when the encoder did not know
    public record StreamInfo(int rate, int channels, int bitsPerSample, long totalSamples) {}

    /// Reads a `STREAMINFO` block, `at` bytes into `bytes`.
    ///
    /// @throws IllegalArgumentException when the block is cut short, or states a
    ///                                  stream FLAC cannot
    public static StreamInfo streamInfo(byte[] bytes, int at) {
        if (bytes.length < at + STREAM_INFO_SIZE) {
            throw new IllegalArgumentException("a STREAMINFO block cut short");
        }
        // After the block sizes (2+2) and the frame sizes (3+3), 20 bits of rate,
        // 3 of channels - 1, 5 of bits per sample - 1 and 36 of total samples.
        var word = 0L;
        for (var i = 10; i < 18; i++) {
            word = (word << 8) | (bytes[at + i] & 0xFF);
        }
        var rate = (int) (word >>> 44);
        var channels = (int) ((word >>> 41) & 0x07) + 1;
        var bits = (int) ((word >>> 36) & 0x1F) + 1;
        var total = word & 0xF_FFFF_FFFFL;
        if (rate == 0) {
            throw new IllegalArgumentException("a FLAC stream at 0 Hz");
        }
        return new StreamInfo(rate, channels, bits, total);
    }

    /// The block size of the frame `packet` begins, in samples, or 0 when the
    /// packet does not begin a frame this reader understands.
    public static int blockSize(byte[] packet) {
        if (packet.length < 5 || (packet[0] & 0xFF) != 0xFF || (packet[1] & 0xFE) != 0xF8) {
            return 0;
        }
        var code = (packet[2] & 0xFF) >>> 4;
        var at = 4;
        // The frame or sample number is UTF-8 coded: its first byte says how many
        // continuation bytes follow.
        var lead = packet[at] & 0xFF;
        var extra = lead < 0x80 ? 0 : Integer.numberOfLeadingZeros(~(lead << 24)) - 1;
        if (extra > 6) {
            return 0;
        }
        at += 1 + extra;
        return switch (code) {
            case 0 -> 0;
            case 1 -> 192;
            case 2, 3, 4, 5 -> 576 << (code - 2);
            case 6 -> at < packet.length ? (packet[at] & 0xFF) + 1 : 0;
            case 7 -> at + 1 < packet.length ? ((packet[at] & 0xFF) << 8 | (packet[at + 1] & 0xFF)) + 1 : 0;
            default -> 256 << (code - 8);
        };
    }

    /// Whether `bytes` starts with `fLaC`.
    static boolean isMarker(byte[] bytes, int at) {
        return bytes.length >= at + MARKER.length
                && Arrays.equals(bytes, at, at + MARKER.length, MARKER, 0, MARKER.length);
    }
}
