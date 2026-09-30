package io.github.digitalsmile.goldberry.media.platform.windows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/// The audio format details the audio decoder hands Media Foundation: the AAC
/// user data, the rate and channels an `AudioSpecificConfig` gives, and the
/// channel mask of a float output type.
final class AudioFormats {

    /// The bytes of `HEAACWAVEINFO` after its `WAVEFORMATEX` (`mmreg.h`): five
    /// fields, 12 bytes.
    static final int HEAACWAVEINFO_TAIL = 12;

    /// `wAudioProfileLevelIndication` of 0xFE: no profile or level given, the
    /// value `MF_MT_AAC_AUDIO_PROFILE_LEVEL_INDICATION` takes for "unknown".
    static final int PROFILE_LEVEL_UNSPECIFIED = 0xFE;

    /// The sampling frequencies an `AudioSpecificConfig` indexes (ISO/IEC
    /// 14496-3, 1.6.3.4).
    private static final int[] FREQUENCIES = {
        96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350
    };

    private AudioFormats() {}

    /// What an `AudioSpecificConfig` says of the stream; 0 where it says
    /// nothing a decoder here could use.
    ///
    /// @param sampleRate the core's rate
    /// @param channels   from the channel configuration: 1–6, 8 for 7, 0 for a
    ///                   layout in the stream's program config element
    record AacConfig(int sampleRate, int channels) {}

    /// `MF_MT_USER_DATA` for raw AAC: the `HEAACWAVEINFO` fields that follow its
    /// `WAVEFORMATEX` (`wPayloadType` 0 for raw, `wAudioProfileLevelIndication`
    /// 0xFE, `wStructType` 0, `wReserved1` 0, `dwReserved2` 0), then the
    /// container's `AudioSpecificConfig`. Little-endian, as Windows is.
    ///
    /// @throws IllegalArgumentException for a configuration shorter than two bytes
    static byte[] aacUserData(byte[] audioSpecificConfig) {
        if (audioSpecificConfig.length < 2) {
            throw new IllegalArgumentException(
                    "an AudioSpecificConfig is at least two bytes, not " + audioSpecificConfig.length);
        }
        var out = ByteBuffer.allocate(HEAACWAVEINFO_TAIL + audioSpecificConfig.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0); // wPayloadType: raw
        out.putShort((short) PROFILE_LEVEL_UNSPECIFIED); // wAudioProfileLevelIndication
        out.putShort((short) 0); // wStructType
        out.putShort((short) 0); // wReserved1
        out.putInt(0); // dwReserved2
        out.put(audioSpecificConfig);
        return out.array();
    }

    /// The core rate and channel count `audioSpecificConfig` gives, for a track
    /// whose container gave neither.
    static AacConfig aacConfig(byte[] audioSpecificConfig) {
        if (audioSpecificConfig.length < 2) {
            return new AacConfig(0, 0);
        }
        var bits = new Bits(audioSpecificConfig);
        var objectType = bits.read(5);
        if (objectType == 31) {
            bits.read(6);
        }
        var index = bits.read(4);
        int rate;
        if (index == 0xF) {
            rate = bits.read(24);
        } else {
            rate = index < FREQUENCIES.length ? FREQUENCIES[index] : 0;
        }
        var configuration = bits.read(4);
        var channels =
                switch (configuration) {
                    case 1, 2, 3, 4, 5, 6 -> configuration;
                    case 7 -> 8;
                    default -> 0;
                };
        return new AacConfig(Math.max(rate, 0), channels);
    }

    /// The `SPEAKER_…` mask (`ksmedia.h`) of the usual layout of `channels`
    /// channels, for 1 to 8; 0, "no mask", otherwise. Used only for an output
    /// type the decoder does not offer itself.
    ///
    /// No remapping follows: Windows orders the channels of a buffer by their
    /// bit in the mask, low to high (FL, FR, FC, LFE, BL, BR, …, BC, SL, SR), and
    /// FFmpeg's `AV_CH_…` bits are the same bits in the same order, so a layout
    /// comes out of Media Foundation in the order FFmpeg's decoders give it.
    static int channelMask(int channels) {
        return switch (channels) {
            case 1 -> 0x4; // FC
            case 2 -> 0x3; // FL FR
            case 3 -> 0x7; // FL FR FC
            case 4 -> 0x33; // FL FR BL BR
            case 5 -> 0x37; // FL FR FC BL BR
            case 6 -> 0x3F; // FL FR FC LFE BL BR: KSAUDIO_SPEAKER_5POINT1
            case 7 -> 0x13F; // FL FR FC LFE BL BR BC
            case 8 -> 0x63F; // FL FR FC LFE BL BR SL SR: KSAUDIO_SPEAKER_7POINT1_SURROUND
            default -> 0;
        };
    }

    /// Big-endian bits, most significant first, reading zeros past the end.
    private static final class Bits {
        private final byte[] data;
        private int position;

        Bits(byte[] data) {
            this.data = data;
        }

        int read(int count) {
            var value = 0;
            for (var i = 0; i < count; i++) {
                var index = position >>> 3;
                var bit = index < data.length ? (data[index] >>> (7 - (position & 7))) & 1 : 0;
                value = value << 1 | bit;
                position++;
            }
            return value;
        }
    }
}
