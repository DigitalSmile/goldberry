package dev.goldberry.media.container.mp3;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/// What the first frame of an MP3 says about the whole stream, when it is a
/// header frame rather than audio: a Xing or Info tag with the frame count,
/// the byte count and a hundred-point seek table, or a VBRI tag with the
/// counts alone, and after Xing's data the LAME tag with the encoder's delay
/// and padding.
///
/// `Xing` names a variable bit rate stream, `Info` a constant one; the frame
/// is silence either way and FFmpeg skips it, which a reader here does too.
/// The delay and padding are what gapless playback trims: FFmpeg drops
/// `delay + 529` decoded samples at the start, the encoder's delay plus the
/// decoder's own, and `padding − 529` at the end.
///
/// @param variable whether the tag was `Xing` (variable bit rate) or `VBRI`,
///                 rather than `Info`
/// @param frames   how many frames follow, or 0 when the tag did not say
/// @param bytes    how many bytes the stream is, this frame included, or 0
/// @param toc      the Xing seek table: for each of 100 time fractions, the
///                 byte position as a fraction of `bytes` in 256ths; empty
///                 when the tag carried none
/// @param delay    the encoder's delay in samples, from the LAME tag, or 0
/// @param padding  the samples the encoder appended, from the LAME tag, or 0
@SuppressWarnings("ArrayRecordComponent")
record VbrHeader(boolean variable, long frames, long bytes, int[] toc, int delay, int padding) {

    private static final int FLAG_FRAMES = 1;
    private static final int FLAG_BYTES = 2;
    private static final int FLAG_TOC = 4;
    private static final int FLAG_QUALITY = 8;
    private static final int TOC_SIZE = 100;
    /// Where the delay and padding sit in a LAME tag: after the nine bytes of
    /// encoder name, the revision and VBR method, the low-pass, the replay
    /// gains, the flags and the bit rate.
    private static final int LAME_DELAY_OFFSET = 21;
    private static final int VBRI_OFFSET = 36;

    /// The tag `frame` carries, when it is a Xing, Info or VBRI frame.
    static Optional<VbrHeader> parse(FrameHeader header, byte[] frame) {
        var xingAt = 4 + (header.crc() ? 2 : 0) + header.sideInfoBytes();
        if (header.layer() == 3 && hasTag(frame, xingAt, "Xing")) {
            return Optional.of(xing(frame, xingAt, true));
        }
        if (header.layer() == 3 && hasTag(frame, xingAt, "Info")) {
            return Optional.of(xing(frame, xingAt, false));
        }
        if (hasTag(frame, VBRI_OFFSET, "VBRI") && frame.length >= VBRI_OFFSET + 26) {
            var bytes = u32(frame, VBRI_OFFSET + 10);
            var frames = u32(frame, VBRI_OFFSET + 14);
            return Optional.of(new VbrHeader(true, frames, bytes, new int[0], 0, 0));
        }
        return Optional.empty();
    }

    /// A Xing or Info tag at `at`: the flags say which of the fields follow.
    private static VbrHeader xing(byte[] frame, int at, boolean variable) {
        var position = at + 4;
        if (position + 4 > frame.length) {
            return new VbrHeader(variable, 0, 0, new int[0], 0, 0);
        }
        var flags = (int) u32(frame, position);
        position += 4;
        var frames = 0L;
        var bytes = 0L;
        var toc = new int[0];
        if ((flags & FLAG_FRAMES) != 0 && position + 4 <= frame.length) {
            frames = u32(frame, position);
            position += 4;
        }
        if ((flags & FLAG_BYTES) != 0 && position + 4 <= frame.length) {
            bytes = u32(frame, position);
            position += 4;
        }
        if ((flags & FLAG_TOC) != 0 && position + TOC_SIZE <= frame.length) {
            toc = new int[TOC_SIZE];
            for (var i = 0; i < TOC_SIZE; i++) {
                toc[i] = frame[position + i] & 0xFF;
            }
            position += TOC_SIZE;
        }
        if ((flags & FLAG_QUALITY) != 0) {
            position += 4;
        }
        var delay = 0;
        var padding = 0;
        // The LAME tag, which Lavc writes too, under its own name.
        if (hasTag(frame, position, "LAME") || hasTag(frame, position, "Lavf") || hasTag(frame, position, "Lavc")) {
            var delayAt = position + LAME_DELAY_OFFSET;
            if (delayAt + 3 <= frame.length) {
                var packed =
                        (frame[delayAt] & 0xFF) << 16 | (frame[delayAt + 1] & 0xFF) << 8 | (frame[delayAt + 2] & 0xFF);
                delay = packed >> 12;
                padding = packed & 0xFFF;
            }
        }
        return new VbrHeader(variable, frames, bytes, toc, delay, padding);
    }

    /// Whether a seek can use the table.
    boolean hasToc() {
        return toc.length == TOC_SIZE && bytes > 0 && frames > 0;
    }

    /// The byte offset from the stream's first frame, in 256ths of [#bytes],
    /// where `fraction` of the stream's time has played, interpolated between
    /// the table's points.
    double tocPosition(double fraction) {
        var scaled = Math.clamp(fraction, 0d, 1d) * TOC_SIZE;
        var index = Math.min((int) scaled, TOC_SIZE - 1);
        var from = toc[index];
        var to = index + 1 < TOC_SIZE ? toc[index + 1] : 256;
        return from + (to - from) * (scaled - index);
    }

    private static boolean hasTag(byte[] frame, int at, String tag) {
        var ascii = tag.getBytes(StandardCharsets.US_ASCII);
        return at >= 0
                && at + ascii.length <= frame.length
                && Arrays.equals(frame, at, at + ascii.length, ascii, 0, ascii.length);
    }

    private static long u32(byte[] bytes, int at) {
        return (bytes[at] & 0xFFL) << 24
                | (bytes[at + 1] & 0xFFL) << 16
                | (bytes[at + 2] & 0xFFL) << 8
                | (bytes[at + 3] & 0xFFL);
    }
}
