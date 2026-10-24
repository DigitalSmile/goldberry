package dev.goldberry.media.decoder.opus;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/// The identification header of an Opus stream (RFC 7845 §5.1): what a
/// container's codec-private record holds for Opus, in Matroska as
/// `CodecPrivate` and in Ogg as the first packet.
///
/// @param channels      the output channel count
/// @param preSkip       samples at 48 kHz to drop from the start of the decoded
///                      stream: the encoder's look-ahead
/// @param inputRate     the rate the encoder was fed at; informational, since
///                      Opus decodes at 48 kHz
/// @param gain          the output gain in Q7.8 decibels, 0 for none
/// @param mappingFamily 0 for mono and stereo, 1 for Vorbis's surround order
/// @param streams       the Opus streams in each packet
/// @param coupled       how many of them are stereo pairs
/// @param mapping       which decoded channel each output channel is, or 255
///                      for silence
@SuppressWarnings("ArrayRecordComponent")
public record OpusHead(
        int channels,
        int preSkip,
        int inputRate,
        int gain,
        int mappingFamily,
        int streams,
        int coupled,
        byte[] mapping) {

    private static final byte[] MAGIC = "OpusHead".getBytes(StandardCharsets.US_ASCII);

    /// Reads a record.
    ///
    /// @throws IllegalArgumentException when `bytes` is not one
    public static OpusHead parse(byte[] bytes) {
        if (bytes.length < 19 || !Arrays.equals(bytes, 0, 8, MAGIC, 0, 8)) {
            throw new IllegalArgumentException("not an OpusHead record");
        }
        var channels = bytes[9] & 0xFF;
        var preSkip = u16(bytes, 10);
        var inputRate = (int) ((bytes[12] & 0xFFL)
                | (bytes[13] & 0xFFL) << 8
                | (bytes[14] & 0xFFL) << 16
                | (bytes[15] & 0xFFL) << 24);
        var gain = (short) u16(bytes, 16);
        var family = bytes[18] & 0xFF;
        if (channels == 0) {
            throw new IllegalArgumentException("an OpusHead with no channels");
        }
        if (family == 0) {
            if (channels > 2) {
                throw new IllegalArgumentException("mapping family 0 with " + channels + " channels");
            }
            var mapping = channels == 1 ? new byte[] {0} : new byte[] {0, 1};
            return new OpusHead(channels, preSkip, inputRate, gain, family, 1, channels - 1, mapping);
        }
        if (bytes.length < 21 + channels) {
            throw new IllegalArgumentException("an OpusHead whose channel mapping is cut short");
        }
        var streams = bytes[19] & 0xFF;
        var coupled = bytes[20] & 0xFF;
        var mapping = Arrays.copyOfRange(bytes, 21, 21 + channels);
        return new OpusHead(channels, preSkip, inputRate, gain, family, streams, coupled, mapping);
    }

    /// A record for a stream with no header: `channels` channels, nothing to
    /// skip, no gain.
    public static OpusHead plain(int channels) {
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("a plain Opus stream has one or two channels, not " + channels);
        }
        return new OpusHead(
                channels, 0, 48_000, 0, 0, 1, channels - 1, channels == 1 ? new byte[] {0} : new byte[] {0, 1});
    }

    /// Whether the plain decoder serves: one stream, as family 0 is.
    public boolean isSingleStream() {
        return mappingFamily == 0;
    }

    private static int u16(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) | (bytes[at + 1] & 0xFF) << 8;
    }
}
