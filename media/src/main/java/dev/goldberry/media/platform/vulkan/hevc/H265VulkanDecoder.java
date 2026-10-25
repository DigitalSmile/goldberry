package dev.goldberry.media.platform.vulkan.hevc;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.bitstream.BitReader;
import dev.goldberry.media.bitstream.ParameterSets;
import dev.goldberry.media.bitstream.hevc.NalHeader;
import dev.goldberry.media.bitstream.hevc.PictureParameterSet;
import dev.goldberry.media.bitstream.hevc.SequenceParameterSet;
import dev.goldberry.media.bitstream.hevc.SliceSegmentHeader;
import dev.goldberry.media.bitstream.hevc.VideoParameterSet;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.platform.vulkan.CodecDecoder;
import dev.goldberry.media.platform.vulkan.HostPictures;
import dev.goldberry.media.platform.vulkan.StreamSignal;
import dev.goldberry.media.platform.vulkan.VideoCapabilities;
import dev.goldberry.media.platform.vulkan.VideoClaim;
import dev.goldberry.media.platform.vulkan.VideoDevice;
import dev.goldberry.media.platform.vulkan.VideoProfile;
import dev.goldberry.media.platform.vulkan.VideoSession;
import dev.goldberry.media.platform.vulkan.VulkanCodec;
import dev.goldberry.media.platform.vulkan.ffm.Structs;
import dev.goldberry.media.platform.vulkan.ffm.VkConstants;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeH265PictureInfo;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeH265PictureInfoFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeH265ReferenceInfo;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeH265ReferenceInfoFlags;

/// HEVC on Vulkan Video: an `hvcC` stream's access units, one picture a
/// packet, each told to the decode with its order count and reference picture
/// set and copied back as NV12, or P010 for Main 10.
///
/// The session is made at the first picture, from its SPS: the profile, the
/// bit depth, the size and as many slots as the SPS buffers pictures, plus
/// one. A later SPS that needs another makes it again at the next IRAP that
/// starts afresh.
public final class H265VulkanDecoder implements CodecDecoder {

    private static final byte[] START_CODE = {0, 0, 1};

    private final VideoDevice device;
    private final HostPictures pictures;
    private final int lengthSize;
    private final Map<Integer, VideoParameterSet> vpss = new LinkedHashMap<>();
    private final Map<Integer, SequenceParameterSet> spss = new LinkedHashMap<>();
    private final Map<Integer, PictureParameterSet> ppss = new LinkedHashMap<>();
    private @Nullable VideoSession session;
    private @Nullable SequenceParameterSet sessionSps;
    private @Nullable H265Dpb<Output> dpb;
    private long parameters;
    private boolean parametersStale = true;

    /// A decoder of `request`'s track on `device`, its pictures copied into
    /// `pictures`.
    ///
    /// @throws IllegalArgumentException when the track's `hvcC` does not read
    public H265VulkanDecoder(VideoDevice device, DecoderRequest request, HostPictures pictures) {
        this.device = device;
        this.pictures = pictures;
        var configuration = ParameterSets.hevc(request.extradata().toArray(JAVA_BYTE));
        this.lengthSize = configuration.nalLengthSize();
        for (var set : configuration.parameterSets()) {
            parameterSet(set);
        }
    }

    /// What `request`'s track needs of the device: the profile, depth and
    /// coded size of its configuration's first SPS; empty for a track without
    /// an `hvcC`, or with one that does not read, or a stream that is not 4:2:0
    /// of 8 or 10 bits.
    public static Optional<VideoClaim> claim(DecoderRequest request) {
        if (request.extradata().byteSize() == 0) {
            return Optional.empty();
        }
        try {
            var configuration = ParameterSets.hevc(request.extradata().toArray(JAVA_BYTE));
            for (var set : configuration.parameterSets()) {
                if (NalHeader.of(set).type() == NalHeader.SPS) {
                    var sps = SequenceParameterSet.parse(rbsp(set));
                    return profile(sps).map(profile -> new VideoClaim(profile, sps.width(), sps.height()));
                }
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /// The profile `sps` decodes with, if it is one: 4:2:0, luma and chroma of
    /// one depth, 8 or 10.
    private static Optional<VideoProfile> profile(SequenceParameterSet sps) {
        var depth = sps.bitDepthLumaMinus8() + 8;
        if (sps.chromaFormatIdc() != 1
                || sps.bitDepthChromaMinus8() != sps.bitDepthLumaMinus8()
                || (depth != 8 && depth != 10)) {
            return Optional.empty();
        }
        return Optional.of(
                new VideoProfile(VulkanCodec.HEVC, sps.profileTierLevel().profileIdc(), depth));
    }

    @Override
    public List<Output> decode(Packet packet) {
        var data = packet.data().toArray(JAVA_BYTE);
        var segments = new ArrayList<byte[]>();
        var due = new ArrayList<Output>();
        for (var nal : units(data)) {
            var header = NalHeader.of(nal);
            if (header.layerId() != 0) {
                continue;
            }
            switch (header.type()) {
                case NalHeader.VPS, NalHeader.SPS, NalHeader.PPS -> parameterSet(nal);
                case NalHeader.EOS -> due.addAll(dpb().endOfSequence());
                default -> {
                    if (header.isSlice()) {
                        segments.add(nal);
                    }
                }
            }
        }
        if (segments.isEmpty()) {
            return due;
        }
        var first = NalHeader.of(segments.getFirst());
        var slice = SliceSegmentHeader.parse(first, rbsp(segments.getFirst()), spss, ppss);
        var pps = ppss.get(slice.ppsId());
        var sps = pps == null ? null : spss.get(pps.spsId());
        if (pps == null || sps == null) {
            throw new IllegalArgumentException("a slice names PPS " + slice.ppsId() + ", which was never sent");
        }
        if (dpb != null && dpb().skips(first)) {
            return due;
        }
        ensureSession(sps, first);
        var buffer = dpb();
        if (buffer.skips(first)) {
            return due;
        }
        var poc = buffer.poc(sps, slice);
        due.addAll(buffer.beforeDecoding(slice));
        var sets = buffer.referenceSets(sps, slice, poc);
        var references = buffer.references();
        var slot = buffer.freeSlot();
        var bitstream = new ByteArrayOutputStream();
        var offsets = new int[segments.size()];
        for (var i = 0; i < segments.size(); i++) {
            offsets[i] = bitstream.size();
            bitstream.writeBytes(START_CODE);
            bitstream.writeBytes(segments.get(i));
        }
        var bytes = bitstream.toByteArray();
        var planes = session()
                .decode(
                        arena -> picture(arena, bytes, offsets, sps, pps, slice, poc, slot, sets, references),
                        sps.confWinLeft() * subWidth(sps),
                        sps.confWinTop() * subHeight(sps),
                        sps.visibleWidth(),
                        sps.visibleHeight());
        var format = sps.bitDepthLumaMinus8() == 0 ? PixelFormat.NV12 : PixelFormat.P010;
        var copied = pictures.copy(planes, format, sps.visibleWidth(), sps.visibleHeight());
        var signal = colour(sps);
        var output = new Output(
                copied,
                packet.ptsNanos() == Long.MIN_VALUE ? Frame.NO_PTS : packet.ptsNanos(),
                signal.matrix(),
                signal.fullRange(),
                signal.colour());
        var outputs = slice.picOutput();
        due.addAll(buffer.decoded(sps, slice, poc, slot, output, outputs));
        if (!outputs) {
            pictures.recycle(copied);
        }
        return due;
    }

    /// The decode of one picture: its bitstream, its picture info, its setup
    /// slot and its references, all in `arena`.
    private VideoSession.Picture picture(
            Arena arena,
            byte[] bytes,
            int[] offsets,
            SequenceParameterSet sps,
            PictureParameterSet pps,
            SliceSegmentHeader slice,
            int poc,
            int slot,
            H265Dpb.Sets sets,
            List<H265Dpb.Reference> references) {
        var nal = slice.nal();
        var std = Structs.allocate(arena, StdVideoDecodeH265PictureInfo.LAYOUT);
        var flags = StdVideoDecodeH265PictureInfo.flags;
        Structs.flag(std, flags, StdVideoDecodeH265PictureInfoFlags.MASK_IrapPicFlag, nal.isIrap());
        Structs.flag(std, flags, StdVideoDecodeH265PictureInfoFlags.MASK_IdrPicFlag, nal.isIdr());
        Structs.flag(std, flags, StdVideoDecodeH265PictureInfoFlags.MASK_IsReference, !nal.isSubLayerNonReference());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeH265PictureInfoFlags.MASK_short_term_ref_pic_set_sps_flag,
                slice.shortTermRefPicSetSps());
        std.set(JAVA_BYTE, StdVideoDecodeH265PictureInfo.sps_video_parameter_set_id, (byte) sps.vpsId());
        std.set(JAVA_BYTE, StdVideoDecodeH265PictureInfo.pps_seq_parameter_set_id, (byte) sps.id());
        std.set(JAVA_BYTE, StdVideoDecodeH265PictureInfo.pps_pic_parameter_set_id, (byte) pps.id());
        std.set(JAVA_BYTE, StdVideoDecodeH265PictureInfo.NumDeltaPocsOfRefRpsIdx, (byte)
                numDeltaPocsOfRefRpsIdx(sps, slice));
        std.set(JAVA_INT, StdVideoDecodeH265PictureInfo.PicOrderCntVal, poc);
        std.set(JAVA_SHORT, StdVideoDecodeH265PictureInfo.NumBitsForSTRefPicSetInSlice, (short)
                (slice.shortTermRefPicSetSps() ? 0 : slice.numBitsForShortTermRefPicSet()));
        list(std, StdVideoDecodeH265PictureInfo.RefPicSetStCurrBefore, sets.stCurrBefore());
        list(std, StdVideoDecodeH265PictureInfo.RefPicSetStCurrAfter, sets.stCurrAfter());
        list(std, StdVideoDecodeH265PictureInfo.RefPicSetLtCurr, sets.ltCurr());
        var info = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeH265PictureInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_PICTURE_INFO_KHR);
        info.set(ADDRESS, VkStructs.VkVideoDecodeH265PictureInfoKHR.pStdPictureInfo, std);
        info.set(JAVA_INT, VkStructs.VkVideoDecodeH265PictureInfoKHR.sliceSegmentCount, offsets.length);
        info.set(
                ADDRESS,
                VkStructs.VkVideoDecodeH265PictureInfoKHR.pSliceSegmentOffsets,
                arena.allocateFrom(JAVA_INT, offsets));
        var setup = new VideoSession.Slot(slot, slotInfo(arena, poc, false));
        var named = new ArrayList<VideoSession.Slot>();
        for (var reference : references) {
            named.add(new VideoSession.Slot(reference.slot(), slotInfo(arena, reference.poc(), reference.longTerm())));
        }
        return new VideoSession.Picture(bytes, info, setup, named, parameters);
    }

    /// Eight slots from `offset`, those of `slots` first and the rest none.
    private static void list(MemorySegment std, long offset, List<Integer> slots) {
        for (var i = 0; i < 8; i++) {
            var slot = i < slots.size() ? slots.get(i) : H265Dpb.NO_SLOT;
            std.set(JAVA_BYTE, offset + i, (byte) slot);
        }
    }

    /// `NumDeltaPocs[RefRpsIdx]`: the deltas of the set a slice's own set was
    /// predicted from; zero otherwise.
    private static int numDeltaPocsOfRefRpsIdx(SequenceParameterSet sps, SliceSegmentHeader slice) {
        if (slice.shortTermRefPicSetSps()) {
            return 0;
        }
        return slice.shortTermRefPicSet()
                .filter(set -> set.interPrediction())
                .map(set -> {
                    var sets = sps.shortTermRefPicSets();
                    return sets.get(sets.size() - (set.deltaIdxMinus1() + 1)).deltaPocs();
                })
                .orElse(0);
    }

    private static MemorySegment slotInfo(Arena arena, int poc, boolean longTerm) {
        var reference = Structs.allocate(arena, StdVideoDecodeH265ReferenceInfo.LAYOUT);
        Structs.flag(
                reference,
                StdVideoDecodeH265ReferenceInfo.flags,
                StdVideoDecodeH265ReferenceInfoFlags.MASK_used_for_long_term_reference,
                longTerm);
        reference.set(JAVA_INT, StdVideoDecodeH265ReferenceInfo.PicOrderCntVal, poc);
        var slot = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeH265DpbSlotInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_DPB_SLOT_INFO_KHR);
        slot.set(ADDRESS, VkStructs.VkVideoDecodeH265DpbSlotInfoKHR.pStdReferenceInfo, reference);
        return slot;
    }

    /// Makes the session for `sps`, or makes it again at an IRAP that starts
    /// afresh whose SPS needs another; and the session parameters, when a
    /// parameter set changed.
    private void ensureSession(SequenceParameterSet sps, NalHeader nal) {
        var current = session;
        var made = sessionSps;
        var afresh = dpb == null || dpb().startsAfresh(nal);
        if (current == null
                || made == null
                || (afresh
                        && (sps.width() != made.width()
                                || sps.height() != made.height()
                                || sps.bitDepthLumaMinus8() != made.bitDepthLumaMinus8()
                                || sps.profileTierLevel().profileIdc()
                                        != made.profileTierLevel().profileIdc()
                                || sps.highest().maxDecPicBufferingMinus1()
                                        > made.highest().maxDecPicBufferingMinus1()))) {
            var profile = profile(sps)
                    .orElseThrow(() -> new IllegalArgumentException("only 4:2:0 HEVC of 8 or 10 bits is decoded here"));
            var capabilities = VideoCapabilities.query(device, profile)
                    .filter(c -> c.fits(sps.width(), sps.height()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "the device does not decode " + profile + " at " + sps.width() + "×" + sps.height()));
            closeSession();
            var slots = Math.min(sps.highest().maxDecPicBufferingMinus1() + 2, capabilities.maxDpbSlots());
            session = new VideoSession(device, profile, capabilities, sps.width(), sps.height(), slots);
            sessionSps = sps;
            var previous = dpb;
            dpb = new H265Dpb<>(slots);
            if (previous != null) {
                previous.drainWaiting().forEach(output -> pictures.recycle(output.picture()));
            }
            parametersStale = true;
        }
        if (parametersStale) {
            var live = session();
            try (var arena = Arena.ofConfined()) {
                var next =
                        live.parameters(H265Parameters.createInfo(arena, vpss.values(), spss.values(), ppss.values()));
                live.destroyParameters(parameters);
                parameters = next;
            }
            parametersStale = false;
        }
    }

    @Override
    public List<Output> drain() {
        var buffer = dpb;
        return buffer == null ? List.of() : buffer.drainWaiting();
    }

    @Override
    public void reset() {
        var buffer = dpb;
        if (buffer != null) {
            buffer.drainWaiting().forEach(output -> pictures.recycle(output.picture()));
            buffer.reset();
        }
    }

    @Override
    public void close() {
        closeSession();
    }

    private void closeSession() {
        var current = session;
        session = null;
        if (current != null) {
            current.destroyParameters(parameters);
            parameters = 0;
            current.close();
        }
    }

    private VideoSession session() {
        var current = session;
        if (current == null) {
            throw new IllegalStateException("no session");
        }
        return current;
    }

    private H265Dpb<Output> dpb() {
        var current = dpb;
        if (current == null) {
            throw new IllegalStateException("no picture buffer");
        }
        return current;
    }

    /// Reads a VPS, an SPS or a PPS, and marks the session parameters stale
    /// when it is new or changed.
    private void parameterSet(byte[] nal) {
        var changed = switch (NalHeader.of(nal).type()) {
            case NalHeader.VPS -> {
                var vps = VideoParameterSet.parse(rbsp(nal));
                yield !vps.equals(vpss.put(vps.id(), vps));
            }
            case NalHeader.SPS -> {
                var sps = SequenceParameterSet.parse(rbsp(nal));
                yield !sps.equals(spss.put(sps.id(), sps));
            }
            case NalHeader.PPS -> {
                var pps = PictureParameterSet.parse(rbsp(nal));
                yield !pps.equals(ppss.put(pps.id(), pps));
            }
            default -> false;
        };
        parametersStale |= changed;
    }

    /// The NAL units of a packet whose units each follow their length.
    private List<byte[]> units(byte[] data) {
        var units = new ArrayList<byte[]>();
        var at = 0;
        while (at + lengthSize <= data.length) {
            var length = 0;
            for (var i = 0; i < lengthSize; i++) {
                length = (length << 8) | (data[at + i] & 0xFF);
            }
            at += lengthSize;
            if (length < 2 || at + length > data.length) {
                throw new IllegalArgumentException("a NAL unit of " + length + " bytes at byte " + at);
            }
            var unit = new byte[length];
            System.arraycopy(data, at, unit, 0, length);
            units.add(unit);
            at += length;
        }
        return units;
    }

    private static byte[] rbsp(byte[] nal) {
        return BitReader.unescape(nal, 2, nal.length - 2);
    }

    private static int subWidth(SequenceParameterSet sps) {
        return sps.chromaFormatIdc() == 1 || sps.chromaFormatIdc() == 2 ? 2 : 1;
    }

    private static int subHeight(SequenceParameterSet sps) {
        return sps.chromaFormatIdc() == 1 ? 2 : 1;
    }

    /// What the VUI says of the colour, with the defaults for what it does not.
    private static StreamSignal colour(SequenceParameterSet sps) {
        return sps.vui()
                .map(v -> StreamSignal.of(
                        v.colourDescriptionPresent(),
                        v.matrixCoefficients(),
                        v.videoSignalTypePresent(),
                        v.fullRange(),
                        v.colour(),
                        sps.visibleHeight()))
                .orElseGet(() -> StreamSignal.unsignalled(sps.visibleHeight()));
    }
}
