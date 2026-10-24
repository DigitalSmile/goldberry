package dev.goldberry.media.container.mp4;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.FrameRate;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.matroska.MatroskaCodecs;

/// The `stsd` box's sample entries (ISO/IEC 14496-12 §8.5.2 and the codec
/// bindings that extend it), read into the Decoder SPI's vocabulary: a
/// [CodecId], the name a track goes by, the [TrackParams] a probe reports, and
/// the extradata the decoder is opened with.
///
/// The names are FFmpeg's, as [MatroskaCodecs]'s are and for the same reason.
/// A sample entry this list does not know keeps its four-character code as its
/// name and is [CodecId#UNKNOWN]. The extradata is each codec's configuration
/// box as the decoders already read it from Matroska: `av1C`, `avcC`, `hvcC`,
/// the AudioSpecificConfig out of `esds`, an `OpusHead` made from `dOps`, and
/// `fLaC` followed by `dfLa`'s metadata blocks.
final class SampleEntries {

    /// One sample entry, read.
    ///
    /// @param codec     the codec, or [CodecId#UNKNOWN]
    /// @param codecName FFmpeg's name, or the four-character code when there is
    ///                  none
    /// @param params    the track's parameters, a video track's without a frame
    ///                  rate, which the sample table says
    /// @param extradata what the decoder is opened with, empty when there is
    ///                  nothing
    @SuppressWarnings("ArrayRecordComponent")
    record SampleEntry(CodecId codec, String codecName, TrackParams params, byte[] extradata) {

        /// This entry with its video parameters' frame rate set.
        SampleEntry withFrameRate(Optional<FrameRate> frameRate) {
            if (!(params instanceof TrackParams.Video video)) {
                return this;
            }
            return new SampleEntry(
                    codec,
                    codecName,
                    new TrackParams.Video(
                            video.width(),
                            video.height(),
                            video.pixelFormat(),
                            video.profile(),
                            video.level(),
                            video.bitRate(),
                            frameRate,
                            video.alpha()),
                    extradata);
        }
    }

    private static final byte[] NO_BYTES = new byte[0];
    private static final byte[] FLAC_MARKER = "fLaC".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OPUS_MAGIC = "OpusHead".getBytes(StandardCharsets.US_ASCII);

    /// What is in an `esds` box's descriptors.
    @SuppressWarnings("ArrayRecordComponent")
    record Esds(int objectType, byte[] specificInfo, long averageBitRate) {}

    private SampleEntries() {}

    /// Reads the first sample entry of the `stsd` box the reader is in, whose
    /// payload runs to `end`, for a track of `type`. A second entry is passed
    /// over: a track that changes its coding mid-way is read as its first.
    static SampleEntry read(BoxReader boxes, long end, MediaType type) {
        var in = boxes.in();
        boxes.fullBox();
        var count = in.u32();
        if (count == 0 || !boxes.hasNext(end)) {
            return unknown("none", type);
        }
        var entry = boxes.next(end);
        var read = switch (type) {
            case VIDEO -> video(boxes, entry);
            case AUDIO -> audio(boxes, entry);
            default -> subtitle(entry);
        };
        boxes.skip(entry);
        if (end != Long.MAX_VALUE) {
            boxes.seekTo(end);
        }
        return read;
    }

    private static SampleEntry unknown(String fourcc, MediaType type) {
        var params = switch (type) {
            case VIDEO ->
                new TrackParams.Video(
                        0, 0, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty());
            case AUDIO -> new TrackParams.Audio(0, 0, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
            default -> new TrackParams.Subtitle();
        };
        return new SampleEntry(CodecId.UNKNOWN, fourcc, params, NO_BYTES);
    }

    // ---- video --------------------------------------------------------------

    private static SampleEntry video(BoxReader boxes, BoxReader.Box entry) {
        var in = boxes.in();
        in.skip(8 + 16);
        var width = in.u16();
        var height = in.u16();
        in.skip(4 + 4 + 4 + 2 + 32 + 2 + 2);
        var children = children(boxes, entry.end());
        var type = entry.type();
        var codec = CodecId.UNKNOWN;
        var record = NO_BYTES;
        if (type == BoxTypes.AV01) {
            codec = CodecId.AV1;
            record = children.getOrDefault(BoxTypes.AV1C, NO_BYTES);
        } else if (type == BoxTypes.VP09) {
            codec = CodecId.VP9;
            record = children.getOrDefault(BoxTypes.VPCC, NO_BYTES);
        } else if (type == BoxTypes.VP08) {
            codec = CodecId.VP8;
            record = children.getOrDefault(BoxTypes.VPCC, NO_BYTES);
        } else if (type == BoxTypes.AVC1 || type == BoxTypes.AVC3) {
            codec = CodecId.H264;
            record = children.getOrDefault(BoxTypes.AVCC, NO_BYTES);
        } else if (type == BoxTypes.HVC1 || type == BoxTypes.HEV1) {
            codec = CodecId.HEVC;
            record = children.getOrDefault(BoxTypes.HVCC, NO_BYTES);
        }
        var bitRate = averageBitRate(children);
        TrackParams.Video params;
        if (codec == CodecId.VP9 || codec == CodecId.VP8) {
            params = new TrackParams.Video(
                    width,
                    height,
                    codec == CodecId.VP8 ? Optional.of("yuv420p") : vpPixelFormat(record),
                    vpField(record, 4),
                    vpField(record, 5),
                    bitRate);
        } else {
            var described = MatroskaCodecs.video(codec, width, height, record, 0, false);
            params = new TrackParams.Video(
                    width, height, described.pixelFormat(), described.profile(), described.level(), bitRate);
        }
        return new SampleEntry(codec, codec == CodecId.UNKNOWN ? entry.name() : codec.ffmpegName(), params, record);
    }

    /// FFmpeg's name for the pixel format a `vpcC` box states (VP Codec ISO
    /// Media File Format Binding §2.2): the bit depth and chroma subsampling
    /// after the box's version and flags, its profile and its level.
    static Optional<String> vpPixelFormat(byte[] vpcC) {
        if (vpcC.length < 7 || vpcC[0] != 1) {
            return Optional.empty();
        }
        var depth = (vpcC[6] >>> 4) & 0x0F;
        var subsampling = (vpcC[6] >>> 1) & 0x07;
        var base = switch (subsampling) {
            case 0, 1 -> "yuv420p";
            case 2 -> "yuv422p";
            case 3 -> "yuv444p";
            default -> null;
        };
        if (base == null) {
            return Optional.empty();
        }
        return switch (depth) {
            case 8 -> Optional.of(base);
            case 10, 12 -> Optional.of(base + depth + "le");
            default -> Optional.empty();
        };
    }

    private static OptionalInt vpField(byte[] vpcC, int at) {
        return vpcC.length > at && vpcC[0] == 1 ? OptionalInt.of(vpcC[at] & 0xFF) : OptionalInt.empty();
    }

    // ---- audio --------------------------------------------------------------

    private static SampleEntry audio(BoxReader boxes, BoxReader.Box entry) {
        var in = boxes.in();
        in.skip(8);
        var version = in.u16();
        in.skip(2 + 4);
        var channels = in.u16();
        var bitDepth = in.u16();
        in.skip(2 + 2);
        var rate = (int) (in.u32() >>> 16);
        var lpcmFlags = 0L;
        if (version == 1) {
            in.skip(16);
        } else if (version == 2) {
            // QuickTime's version 2 sound description: the rate as a double and
            // the channel count as a whole integer, for what the 16.16 and the
            // 16-bit fields cannot hold.
            in.skip(4);
            var sampleRate = Double.longBitsToDouble(in.unsignedBigEndian(8));
            channels = (int) in.u32();
            in.skip(4);
            bitDepth = (int) in.u32();
            lpcmFlags = in.u32();
            in.skip(4 + 4);
            rate = (int) Math.round(sampleRate);
        }
        var children = children(boxes, entry.end());
        var type = entry.type();
        var codec = CodecId.UNKNOWN;
        var extradata = NO_BYTES;
        var profile = OptionalInt.empty();
        var bitRate = averageBitRate(children);
        if (type == BoxTypes.MP4A) {
            var esds = children.get(BoxTypes.ESDS);
            if (esds == null) {
                codec = CodecId.AAC;
            } else {
                var descriptors = esds(esds);
                codec = switch (descriptors.objectType()) {
                    case 0x40, 0x66, 0x67, 0x68 -> CodecId.AAC;
                    case 0x69, 0x6B -> CodecId.MP3;
                    default -> CodecId.UNKNOWN;
                };
                if (codec == CodecId.AAC) {
                    extradata = descriptors.specificInfo();
                    profile = aacProfile(extradata);
                }
                if (bitRate.isEmpty() && descriptors.averageBitRate() > 0) {
                    bitRate = OptionalLong.of(descriptors.averageBitRate());
                }
            }
        } else if (type == BoxTypes.OPUS) {
            codec = CodecId.OPUS;
            extradata = opusHead(children.getOrDefault(BoxTypes.DOPS, NO_BYTES), channels);
        } else if (type == BoxTypes.FLAC) {
            codec = CodecId.FLAC;
            var dfLa = children.getOrDefault(BoxTypes.DFLA, NO_BYTES);
            extradata = flacExtradata(dfLa);
        } else if (type == BoxTypes.MP3) {
            codec = CodecId.MP3;
        } else if (type == BoxTypes.AC3) {
            codec = CodecId.AC3;
        } else if (type == BoxTypes.EC3) {
            codec = CodecId.EAC3;
        } else if (type == BoxTypes.SOWT) {
            codec = pcm(bitDepth, true, false);
        } else if (type == BoxTypes.LPCM && version == 2) {
            var littleEndian = (lpcmFlags & 0x2) == 0;
            var isFloat = (lpcmFlags & 0x1) != 0;
            var signedInt = (lpcmFlags & 0x4) != 0;
            codec = littleEndian && (isFloat || signedInt) ? pcm(bitDepth, !isFloat, isFloat) : CodecId.UNKNOWN;
        } else if (type == BoxTypes.IPCM) {
            var pcmC = children.get(BoxTypes.PCMC);
            if (pcmC != null && pcmC.length >= 6 && (pcmC[4] & 0x01) != 0) {
                codec = pcm(pcmC[5] & 0xFF, true, false);
            }
        }
        var described = MatroskaCodecs.audio(codec, rate, channels, bitDepth);
        var params = new TrackParams.Audio(
                described.sampleRate(), described.channels(), described.sampleFormat(), profile, bitRate);
        return new SampleEntry(codec, codec == CodecId.UNKNOWN ? entry.name() : codec.ffmpegName(), params, extradata);
    }

    /// The little-endian PCM codec at `bitDepth`, or [CodecId#UNKNOWN] for a
    /// depth there is no codec for.
    private static CodecId pcm(int bitDepth, boolean integer, boolean floating) {
        if (floating) {
            return bitDepth == 32 ? CodecId.PCM_F32LE : CodecId.UNKNOWN;
        }
        if (!integer) {
            return CodecId.UNKNOWN;
        }
        return switch (bitDepth) {
            case 16 -> CodecId.PCM_S16LE;
            case 24 -> CodecId.PCM_S24LE;
            default -> CodecId.UNKNOWN;
        };
    }

    /// FFmpeg's profile number for the audio object type an AudioSpecificConfig
    /// (ISO/IEC 14496-3 §1.6.2.1) opens with: one less than the type, as its
    /// `FF_PROFILE_AAC_*` constants are numbered.
    static OptionalInt aacProfile(byte[] specificInfo) {
        if (specificInfo.length < 1) {
            return OptionalInt.empty();
        }
        var objectType = (specificInfo[0] & 0xFF) >>> 3;
        if (objectType == 31) {
            if (specificInfo.length < 2) {
                return OptionalInt.empty();
            }
            objectType = 32 + (((specificInfo[0] & 0x07) << 3) | ((specificInfo[1] & 0xFF) >>> 5));
        }
        return objectType >= 1 ? OptionalInt.of(objectType - 1) : OptionalInt.empty();
    }

    /// The `esds` box's descriptors (ISO/IEC 14496-1 §7.2.6): the ES_Descriptor
    /// holding a DecoderConfigDescriptor holding the DecoderSpecificInfo, each
    /// a tag, a length in 7-bit groups, and a body.
    static Esds esds(byte[] esds) {
        var objectType = 0;
        var info = NO_BYTES;
        var averageBitRate = 0L;
        var at = 4; // the full box's version and flags
        var end = esds.length;
        while (at < end) {
            var tag = esds[at++] & 0xFF;
            var length = 0;
            var more = true;
            for (var i = 0; i < 4 && more && at < end; i++) {
                var b = esds[at++] & 0xFF;
                length = (length << 7) | (b & 0x7F);
                more = (b & 0x80) != 0;
            }
            var bodyEnd = Math.min(end, at + length);
            if (tag == 0x03) {
                // ES_Descriptor: ES_ID, then flags that may be followed by a
                // stream dependence, a URL and an OCR stream; the decoder
                // config is a child after those.
                if (at + 3 > bodyEnd) {
                    break;
                }
                var flags = esds[at + 2] & 0xFF;
                at += 3;
                if ((flags & 0x80) != 0) {
                    at += 2;
                }
                if ((flags & 0x40) != 0 && at < bodyEnd) {
                    at += 1 + (esds[at] & 0xFF);
                }
                if ((flags & 0x20) != 0) {
                    at += 2;
                }
                // Carry on inside: the children are what we are after.
                end = bodyEnd;
            } else if (tag == 0x04) {
                if (at + 13 > bodyEnd) {
                    break;
                }
                objectType = esds[at] & 0xFF;
                averageBitRate = (esds[at + 9] & 0xFFL) << 24
                        | (esds[at + 10] & 0xFFL) << 16
                        | (esds[at + 11] & 0xFFL) << 8
                        | (esds[at + 12] & 0xFFL);
                at += 13;
                end = bodyEnd;
            } else if (tag == 0x05) {
                info = Arrays.copyOfRange(esds, at, bodyEnd);
                break;
            } else {
                at = bodyEnd;
            }
        }
        return new Esds(objectType, info, averageBitRate);
    }

    /// An `OpusHead` record (RFC 7845 §5.1), little-endian, made from the
    /// `dOps` box (Opus in ISO BMFF §4.3.2), which holds the same fields
    /// big-endian and without the magic or the version: what the Opus decoder
    /// already reads out of Matroska and Ogg.
    static byte[] opusHead(byte[] dOps, int channelsFromEntry) {
        if (dOps.length < 11) {
            // No usable box: a plain stereo or mono header, nothing to skip.
            var channels = channelsFromEntry == 1 ? 1 : 2;
            var head = new byte[19];
            System.arraycopy(OPUS_MAGIC, 0, head, 0, 8);
            head[8] = 1;
            head[9] = (byte) channels;
            putLittleEndian(head, 12, 48_000, 4);
            return head;
        }
        var channels = dOps[1] & 0xFF;
        var preSkip = ((dOps[2] & 0xFF) << 8) | (dOps[3] & 0xFF);
        var inputRate = (dOps[4] & 0xFFL) << 24 | (dOps[5] & 0xFFL) << 16 | (dOps[6] & 0xFFL) << 8 | (dOps[7] & 0xFFL);
        var gain = ((dOps[8] & 0xFF) << 8) | (dOps[9] & 0xFF);
        var family = dOps[10] & 0xFF;
        var mappingLength = family == 0 ? 0 : 2 + channels;
        var head = new byte[19 + mappingLength];
        System.arraycopy(OPUS_MAGIC, 0, head, 0, 8);
        head[8] = 1;
        head[9] = (byte) channels;
        putLittleEndian(head, 10, preSkip, 2);
        putLittleEndian(head, 12, inputRate, 4);
        putLittleEndian(head, 16, gain, 2);
        head[18] = (byte) family;
        if (mappingLength > 0) {
            if (dOps.length < 11 + mappingLength) {
                throw new MediaException(new MediaError.InvalidData(
                        "a dOps box of " + dOps.length + " bytes whose channel mapping is cut short"));
            }
            System.arraycopy(dOps, 11, head, 19, mappingLength);
        }
        return head;
    }

    /// `fLaC` followed by the metadata blocks a `dfLa` box holds after its
    /// version and flags (FLAC in ISO BMFF §3.3.2): the stream's header as the
    /// FLAC decoder reads it from a file or Matroska's `CodecPrivate`.
    static byte[] flacExtradata(byte[] dfLa) {
        var blocks = dfLa.length > 4 ? dfLa.length - 4 : 0;
        var out = new byte[4 + blocks];
        System.arraycopy(FLAC_MARKER, 0, out, 0, 4);
        if (blocks > 0) {
            System.arraycopy(dfLa, 4, out, 4, blocks);
        }
        return out;
    }

    private static void putLittleEndian(byte[] into, int at, long value, int count) {
        for (var i = 0; i < count; i++) {
            into[at + i] = (byte) (value >>> (8 * i));
        }
    }

    // ---- subtitles ----------------------------------------------------------

    private static SampleEntry subtitle(BoxReader.Box entry) {
        var type = entry.type();
        CodecId codec;
        if (type == BoxTypes.TX3G) {
            codec = CodecId.MOV_TEXT;
        } else if (type == BoxTypes.WVTT) {
            codec = CodecId.WEBVTT;
        } else {
            codec = CodecId.UNKNOWN;
        }
        return new SampleEntry(
                codec,
                codec == CodecId.UNKNOWN ? entry.name() : codec.ffmpegName(),
                new TrackParams.Subtitle(),
                NO_BYTES);
    }

    // ---- helpers ------------------------------------------------------------

    /// The boxes inside a sample entry, by type, on the heap: the codec's
    /// configuration record among them.
    private static Map<Integer, byte[]> children(BoxReader boxes, long end) {
        var children = new HashMap<Integer, byte[]>();
        while (boxes.hasNext(end)) {
            var child = boxes.next(end);
            if (child.size() > 1 << 20) {
                // No configuration record is a megabyte; a size that says so
                // is a sample entry of a shape this reader does not know.
                boxes.skip(child);
                continue;
            }
            children.putIfAbsent(child.type(), boxes.payload(child));
        }
        return children;
    }

    /// The average bit rate a `btrt` box states, when one does and it is not
    /// zero.
    private static OptionalLong averageBitRate(Map<Integer, byte[]> children) {
        var btrt = children.get(BoxTypes.BTRT);
        if (btrt == null || btrt.length < 12) {
            return OptionalLong.empty();
        }
        var average = (btrt[8] & 0xFFL) << 24 | (btrt[9] & 0xFFL) << 16 | (btrt[10] & 0xFFL) << 8 | (btrt[11] & 0xFFL);
        return average > 0 ? OptionalLong.of(average) : OptionalLong.empty();
    }
}
