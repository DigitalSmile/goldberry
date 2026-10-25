package dev.goldberry.media.platform.vulkan.h264;

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
import dev.goldberry.media.bitstream.h264.NalHeader;
import dev.goldberry.media.bitstream.h264.PictureParameterSet;
import dev.goldberry.media.bitstream.h264.SequenceParameterSet;
import dev.goldberry.media.bitstream.h264.SliceHeader;
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
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeH264PictureInfo;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeH264PictureInfoFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeH264ReferenceInfo;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeH264ReferenceInfoFlags;

/// H.264 on Vulkan Video: an `avcC` stream's access units, one picture a
/// packet, each told to the decode with its order counts and references and
/// copied back as NV12.
///
/// The session is made at the first picture, from its SPS: the profile, the
/// size and as many slots as the SPS keeps references, plus one. A later SPS
/// of another size or profile makes it again at the next IDR.
public final class H264VulkanDecoder implements CodecDecoder {

    private static final byte[] START_CODE = {0, 0, 1};

    private final VideoDevice device;
    private final HostPictures pictures;
    private final int lengthSize;
    private final Map<Integer, SequenceParameterSet> spss = new LinkedHashMap<>();
    private final Map<Integer, PictureParameterSet> ppss = new LinkedHashMap<>();
    private @Nullable VideoSession session;
    private @Nullable SequenceParameterSet sessionSps;
    private @Nullable H264Dpb<CodecDecoder.Output> dpb;
    private long parameters;
    private boolean parametersStale = true;
    private boolean awaitingKeyframe = true;

    /// A decoder of `request`'s track on `device`, its pictures copied into
    /// `pictures`.
    ///
    /// @throws IllegalArgumentException when the track's `avcC` does not read
    public H264VulkanDecoder(VideoDevice device, DecoderRequest request, HostPictures pictures) {
        this.device = device;
        this.pictures = pictures;
        var record = request.extradata().toArray(JAVA_BYTE);
        var configuration = ParameterSets.h264(record);
        this.lengthSize = configuration.nalLengthSize();
        for (var set : configuration.parameterSets()) {
            parameterSet(set);
        }
    }

    /// What `request`'s track needs of the device: the profile and coded size
    /// of its configuration's first SPS; empty for a track without an `avcC`,
    /// or with one that does not read, or a stream that is not 8-bit 4:2:0
    /// frames.
    public static Optional<VideoClaim> claim(DecoderRequest request) {
        if (request.extradata().byteSize() == 0) {
            return Optional.empty();
        }
        try {
            var configuration = ParameterSets.h264(request.extradata().toArray(JAVA_BYTE));
            for (var set : configuration.parameterSets()) {
                if (NalHeader.of(set).type() != NalHeader.SPS) {
                    continue;
                }
                var sps = SequenceParameterSet.parse(rbsp(set));
                if (sps.bitDepthLumaMinus8() != 0 || sps.chromaFormatIdc() != 1 || !sps.frameMbsOnly()) {
                    return Optional.empty();
                }
                return Optional.of(new VideoClaim(
                        new VideoProfile(VulkanCodec.H264, sps.profileIdc(), 8), sps.codedWidth(), sps.codedHeight()));
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Output> decode(Packet packet) {
        var data = packet.data().toArray(JAVA_BYTE);
        var slices = new ArrayList<byte[]>();
        for (var nal : units(data)) {
            var header = NalHeader.of(nal);
            switch (header.type()) {
                case NalHeader.SPS, NalHeader.PPS -> parameterSet(nal);
                case NalHeader.SLICE, NalHeader.IDR -> slices.add(nal);
                case NalHeader.SLICE_EXTENSION, NalHeader.SUBSET_SPS ->
                    throw new IllegalArgumentException("SVC and MVC streams are not decoded here");
                default -> {}
            }
        }
        if (slices.isEmpty()) {
            return List.of();
        }
        var first = NalHeader.of(slices.getFirst());
        var slice = SliceHeader.parse(first, rbsp(slices.getFirst()), spss, ppss);
        if (awaitingKeyframe && !first.isIdr() && !slice.intra()) {
            // A seek lands on a keyframe; until one comes, nothing decodes.
            return List.of();
        }
        if (slice.fieldPic()) {
            throw new IllegalArgumentException("a field picture: Vulkan Video decodes frames here");
        }
        var pps = ppss.get(slice.ppsId());
        var sps = pps == null ? null : spss.get(pps.spsId());
        if (pps == null || sps == null) {
            throw new IllegalArgumentException("a slice names PPS " + slice.ppsId() + ", which was never sent");
        }
        ensureSession(sps, first.isIdr());
        var buffer = dpb();
        var order = buffer.order(sps, slice);
        var slot = buffer.freeSlot();
        var references = buffer.references();
        var bitstream = new ByteArrayOutputStream();
        var offsets = new int[slices.size()];
        for (var i = 0; i < slices.size(); i++) {
            offsets[i] = bitstream.size();
            bitstream.writeBytes(START_CODE);
            bitstream.writeBytes(slices.get(i));
        }
        var bytes = bitstream.toByteArray();
        var current = session();
        var planes = current.decode(
                arena -> picture(arena, bytes, offsets, sps, pps, slice, order, slot, references),
                sps.cropX(),
                sps.cropY(),
                sps.visibleWidth(),
                sps.visibleHeight());
        awaitingKeyframe = false;
        var copied = pictures.copy(planes, PixelFormat.NV12, sps.visibleWidth(), sps.visibleHeight());
        var signal = colour(sps);
        var output = new Output(
                copied,
                packet.ptsNanos() == Long.MIN_VALUE ? Frame.NO_PTS : packet.ptsNanos(),
                signal.matrix(),
                signal.fullRange(),
                signal.colour());
        return buffer.decoded(sps, slice, order, slot, output, reorderDepth(sps));
    }

    /// The decode of one picture: its bitstream, its picture info, its setup
    /// slot and its references, all in `arena`.
    private VideoSession.Picture picture(
            Arena arena,
            byte[] bytes,
            int[] offsets,
            SequenceParameterSet sps,
            PictureParameterSet pps,
            SliceHeader slice,
            H264Dpb.Order order,
            int slot,
            List<H264Dpb.Reference> references) {
        var std = Structs.allocate(arena, StdVideoDecodeH264PictureInfo.LAYOUT);
        var flags = StdVideoDecodeH264PictureInfo.flags;
        Structs.flag(
                std,
                flags,
                StdVideoDecodeH264PictureInfoFlags.MASK_IdrPicFlag,
                slice.nal().isIdr());
        Structs.flag(std, flags, StdVideoDecodeH264PictureInfoFlags.MASK_is_intra, slice.intra());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeH264PictureInfoFlags.MASK_is_reference,
                slice.nal().refIdc() != 0);
        std.set(JAVA_BYTE, StdVideoDecodeH264PictureInfo.seq_parameter_set_id, (byte) sps.id());
        std.set(JAVA_BYTE, StdVideoDecodeH264PictureInfo.pic_parameter_set_id, (byte) pps.id());
        std.set(JAVA_SHORT, StdVideoDecodeH264PictureInfo.frame_num, (short) slice.frameNum());
        std.set(JAVA_SHORT, StdVideoDecodeH264PictureInfo.idr_pic_id, (short) slice.idrPicId());
        std.set(JAVA_INT, StdVideoDecodeH264PictureInfo.PicOrderCnt, order.top());
        std.set(JAVA_INT, StdVideoDecodeH264PictureInfo.PicOrderCnt + 4, order.bottom());
        var info = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeH264PictureInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_PICTURE_INFO_KHR);
        info.set(ADDRESS, VkStructs.VkVideoDecodeH264PictureInfoKHR.pStdPictureInfo, std);
        info.set(JAVA_INT, VkStructs.VkVideoDecodeH264PictureInfoKHR.sliceCount, offsets.length);
        info.set(
                ADDRESS,
                VkStructs.VkVideoDecodeH264PictureInfoKHR.pSliceOffsets,
                arena.allocateFrom(JAVA_INT, offsets));
        var setup = new VideoSession.Slot(
                slot,
                slotInfo(
                        arena,
                        slice.frameNum(),
                        order.top(),
                        order.bottom(),
                        slice.nal().isIdr() && slice.longTermReference()));
        var named = new ArrayList<VideoSession.Slot>();
        for (var reference : references) {
            named.add(new VideoSession.Slot(
                    reference.slot(),
                    slotInfo(
                            arena,
                            reference.longTerm() ? reference.longTermFrameIdx() : reference.frameNum(),
                            reference.top(),
                            reference.bottom(),
                            reference.longTerm())));
        }
        return new VideoSession.Picture(bytes, info, setup, named, parameters);
    }

    private static MemorySegment slotInfo(Arena arena, int frameNum, int top, int bottom, boolean longTerm) {
        var reference = Structs.allocate(arena, StdVideoDecodeH264ReferenceInfo.LAYOUT);
        Structs.flag(
                reference,
                StdVideoDecodeH264ReferenceInfo.flags,
                StdVideoDecodeH264ReferenceInfoFlags.MASK_used_for_long_term_reference,
                longTerm);
        reference.set(JAVA_SHORT, StdVideoDecodeH264ReferenceInfo.FrameNum, (short) frameNum);
        reference.set(JAVA_INT, StdVideoDecodeH264ReferenceInfo.PicOrderCnt, top);
        reference.set(JAVA_INT, StdVideoDecodeH264ReferenceInfo.PicOrderCnt + 4, bottom);
        var slot = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeH264DpbSlotInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_DPB_SLOT_INFO_KHR);
        slot.set(ADDRESS, VkStructs.VkVideoDecodeH264DpbSlotInfoKHR.pStdReferenceInfo, reference);
        return slot;
    }

    /// Makes the session for `sps`, or makes it again at an IDR whose SPS
    /// needs another; and the session parameters, when a parameter set changed.
    private void ensureSession(SequenceParameterSet sps, boolean idr) {
        var current = session;
        var made = sessionSps;
        if (current == null
                || made == null
                || (idr
                        && (sps.codedWidth() != made.codedWidth()
                                || sps.codedHeight() != made.codedHeight()
                                || sps.profileIdc() != made.profileIdc()
                                || sps.maxNumRefFrames() > made.maxNumRefFrames()))) {
            if (sps.bitDepthLumaMinus8() != 0 || sps.chromaFormatIdc() != 1) {
                throw new IllegalArgumentException("only 8-bit 4:2:0 H.264 is decoded here");
            }
            var profile = new VideoProfile(VulkanCodec.H264, sps.profileIdc(), 8);
            var capabilities = VideoCapabilities.query(device, profile)
                    .filter(c -> c.fits(sps.codedWidth(), sps.codedHeight()))
                    .orElseThrow(() -> new IllegalArgumentException("the device does not decode H.264 profile "
                            + sps.profileIdc() + " at " + sps.codedWidth() + "×" + sps.codedHeight()));
            closeSession();
            var slots = Math.min(sps.maxNumRefFrames() + 1, capabilities.maxDpbSlots());
            session = new VideoSession(device, profile, capabilities, sps.codedWidth(), sps.codedHeight(), slots);
            sessionSps = sps;
            dpb = new H264Dpb<>(slots);
            parametersStale = true;
        }
        if (parametersStale) {
            var live = session();
            try (var arena = Arena.ofConfined()) {
                var next = live.parameters(H264Parameters.createInfo(arena, spss.values(), ppss.values()));
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
        awaitingKeyframe = true;
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

    private H264Dpb<Output> dpb() {
        var current = dpb;
        if (current == null) {
            throw new IllegalStateException("no picture buffer");
        }
        return current;
    }

    /// Reads an SPS or a PPS, and marks the session parameters stale when it
    /// is new or changed.
    private void parameterSet(byte[] nal) {
        var header = NalHeader.of(nal);
        if (header.type() == NalHeader.SPS) {
            var sps = SequenceParameterSet.parse(rbsp(nal));
            if (!sps.equals(spss.put(sps.id(), sps))) {
                parametersStale = true;
            }
        } else if (header.type() == NalHeader.PPS) {
            var pps = PictureParameterSet.parse(rbsp(nal), spss);
            if (!pps.equals(ppss.put(pps.id(), pps))) {
                parametersStale = true;
            }
        }
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
            if (length <= 0 || at + length > data.length) {
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
        return BitReader.unescape(nal, 1, nal.length - 1);
    }

    /// How many pictures may wait ahead of one for output: the VUI's
    /// `max_num_reorder_frames`, else none for order type 2, else what the
    /// level's picture buffer holds.
    private static int reorderDepth(SequenceParameterSet sps) {
        var vui = sps.vui();
        if (vui.isPresent() && vui.get().maxNumReorderFrames() >= 0) {
            return vui.get().maxNumReorderFrames();
        }
        if (sps.picOrderCntType() == 2) {
            return 0;
        }
        var macroblocks = (long) (sps.picWidthInMbsMinus1() + 1) * (sps.codedHeight() / 16);
        return ParameterSets.maxDpbFrames(sps.levelIdc(), (sps.constraints() & 0x10) != 0, macroblocks);
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
