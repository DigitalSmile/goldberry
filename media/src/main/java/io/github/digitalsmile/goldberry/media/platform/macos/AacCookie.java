package io.github.digitalsmile.goldberry.media.platform.macos;

import java.io.ByteArrayOutputStream;

/// The magic cookie AudioToolbox's AAC decoder is configured with: an MPEG-4
/// elementary stream descriptor (`ES_Descriptor`, ISO/IEC 14496-1 §7.2.6.5)
/// around the track's `AudioSpecificConfig`.
///
/// A container hands over the `AudioSpecificConfig` alone: FFmpeg's `mov`
/// demuxer takes it out of `esds`, and Matroska's `CodecPrivate` is only that.
/// AudioToolbox wants the whole descriptor, so it is built back here, as
/// FFmpeg's own `audiotoolboxdec.c` does.
final class AacCookie {

    private static final int ES_DESCRIPTOR = 0x03;
    private static final int DECODER_CONFIG_DESCRIPTOR = 0x04;
    private static final int DECODER_SPECIFIC_INFO = 0x05;
    /// `objectTypeIndication` for MPEG-4 Audio (ISO/IEC 14496-1 Table 5).
    private static final int OBJECT_TYPE_MPEG4_AUDIO = 0x40;
    /// `streamType` 5 (audio) shifted into place, with `upStream` 0 and the
    /// reserved bit 1.
    private static final int STREAM_TYPE_AUDIO = 0x15;

    private AacCookie() {}

    /// The descriptor around `audioSpecificConfig`.
    static byte[] of(byte[] audioSpecificConfig) {
        if (audioSpecificConfig.length < 2) {
            throw new IllegalArgumentException(
                    "an AudioSpecificConfig is at least two bytes, not " + audioSpecificConfig.length);
        }
        var config = audioSpecificConfig.length;
        var out = new ByteArrayOutputStream();

        descriptor(out, ES_DESCRIPTOR, 3 + 5 + 13 + 5 + config);
        out.write(0); // ES_ID
        out.write(0);
        out.write(0); // no dependency, URL or OCR stream

        descriptor(out, DECODER_CONFIG_DESCRIPTOR, 13 + 5 + config);
        out.write(OBJECT_TYPE_MPEG4_AUDIO);
        out.write(STREAM_TYPE_AUDIO);
        out.writeBytes(new byte[3 + 4 + 4]); // bufferSizeDB, maxBitrate, avgBitrate

        descriptor(out, DECODER_SPECIFIC_INFO, config);
        out.writeBytes(audioSpecificConfig);
        return out.toByteArray();
    }

    /// A descriptor's tag and its length, in the four-byte form with the
    /// continuation bit on the first three: what AudioToolbox and FFmpeg write.
    private static void descriptor(ByteArrayOutputStream out, int tag, int length) {
        out.write(tag);
        for (var shift = 21; shift > 0; shift -= 7) {
            out.write(((length >>> shift) & 0x7F) | 0x80);
        }
        out.write(length & 0x7F);
    }
}
