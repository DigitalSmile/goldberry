package dev.goldberry.media.platform.vulkan.vp9;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.bitstream.vp9.ColorConfig;
import dev.goldberry.media.bitstream.vp9.StreamState;
import dev.goldberry.media.bitstream.vp9.Superframe;
import dev.goldberry.media.bitstream.vp9.UncompressedHeader;
import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.platform.vulkan.CodecDecoder;
import dev.goldberry.media.platform.vulkan.HostPictures;
import dev.goldberry.media.platform.vulkan.VideoCapabilities;
import dev.goldberry.media.platform.vulkan.VideoClaim;
import dev.goldberry.media.platform.vulkan.VideoDevice;
import dev.goldberry.media.platform.vulkan.VideoProfile;
import dev.goldberry.media.platform.vulkan.VideoSession;
import dev.goldberry.media.platform.vulkan.VulkanCodec;
import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoDecodeVP9PictureInfo;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoDecodeVP9PictureInfoFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoVP9ColorConfig;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoVP9ColorConfigFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoVP9LoopFilter;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoVP9LoopFilterFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoVP9Segmentation;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoVP9SegmentationFlags;

/// VP9 on Vulkan Video: each frame of a packet, superframes split, told to
/// the decode with its uncompressed header and copied back when it is shown,
/// as NV12, or P010 for profile 2.
///
/// The eight reference slots VP9 refreshes are mapped onto nine slots of the
/// session's picture buffer: a frame is decoded into the one no reference
/// holds. The session is made at the first keyframe, at its size and depth,
/// and made again at a keyframe that needs another; an inter frame of another
/// size than the session's is refused, which moves the Engine to the next
/// decoder.
public final class Vp9VulkanDecoder implements CodecDecoder {

    /// VP9's reference slots.
    private static final int REFERENCES = 8;

    /// The session's slots: the references and the frame being decoded.
    private static final int SLOTS = REFERENCES + 1;

    private final VideoDevice device;
    private final HostPictures pictures;
    private final int[] slotOf = new int[REFERENCES];
    private StreamState state = StreamState.initial();
    private @Nullable VideoSession session;
    private int sessionWidth;
    private int sessionHeight;
    private int sessionDepth;
    private boolean awaitingKeyframe = true;

    /// A decoder of a VP9 track on `device`, its pictures copied into
    /// `pictures`.
    public Vp9VulkanDecoder(VideoDevice device, HostPictures pictures) {
        this.device = device;
        this.pictures = pictures;
        Arrays.fill(slotOf, -1);
    }

    /// What `request`'s track needs of the device: profile 0 or 2 at its size,
    /// by the pixel format the container states, else by its profile; empty
    /// for a track of another chroma format or depth.
    public static Optional<VideoClaim> claim(DecoderRequest request) {
        if (!(request.params() instanceof TrackParams.Video video) || video.width() <= 0 || video.height() <= 0) {
            return Optional.empty();
        }
        int profile;
        if (video.pixelFormat().isPresent()) {
            profile = switch (video.pixelFormat().get()) {
                case "yuv420p" -> 0;
                case "yuv420p10le" -> 2;
                default -> -1;
            };
        } else {
            profile = video.profile().orElse(0);
        }
        if (profile != 0 && profile != 2) {
            return Optional.empty();
        }
        return Optional.of(new VideoClaim(
                new VideoProfile(VulkanCodec.VP9, profile, profile == 0 ? 8 : 10), video.width(), video.height()));
    }

    @Override
    public List<Output> decode(Packet packet) {
        var data = packet.data().toArray(JAVA_BYTE);
        var pts = packet.ptsNanos() == Long.MIN_VALUE ? Frame.NO_PTS : packet.ptsNanos();
        var due = new ArrayList<Output>();
        for (var range : Superframe.frames(data)) {
            var frame = Arrays.copyOfRange(data, range.offset(), range.offset() + range.size());
            if (awaitingKeyframe && !isKeyFrame(frame)) {
                // A seek lands on a keyframe; until one comes, nothing decodes.
                continue;
            }
            var header = UncompressedHeader.parse(frame, state);
            if (header.showExistingFrame()) {
                var slot = slotOf[header.frameToShowMapIdx()];
                var shown = state.slots().get(header.frameToShowMapIdx());
                var planes = session().readBack(slot, 0, 0, shown.width(), shown.height());
                due.add(output(planes, shown.width(), shown.height(), state.color(), pts));
            } else {
                decodeFrame(frame, header, pts).ifPresent(due::add);
            }
            state = state.after(header);
        }
        return due;
    }

    private static boolean isKeyFrame(byte[] frame) {
        try {
            return UncompressedHeader.parse(frame, StreamState.initial()).isKeyFrame();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Optional<Output> decodeFrame(byte[] frame, UncompressedHeader header, long pts) {
        var width = header.frameWidth();
        var height = header.frameHeight();
        var depth = header.color().bitDepth();
        if (header.isKeyFrame()) {
            ensureSession(header);
        } else if (session == null || width != sessionWidth || height != sessionHeight || depth != sessionDepth) {
            throw new IllegalArgumentException("a VP9 frame of " + width + "×" + height + " in a stream of "
                    + sessionWidth + "×" + sessionHeight + ": reference scaling is not decoded here");
        }
        var setup = freeSlot();
        var named = new int[3];
        var references = new ArrayList<Integer>();
        for (var i = 0; i < 3; i++) {
            named[i] = header.isIntra() ? -1 : slotOf[header.refFrameIdx().get(i)];
            if (named[i] >= 0 && !references.contains(named[i])) {
                references.add(named[i]);
            }
        }
        var planes =
                session().decode(arena -> picture(arena, frame, header, setup, named, references), 0, 0, width, height);
        for (var i = 0; i < REFERENCES; i++) {
            if ((header.refreshFrameFlags() >> i & 1) != 0) {
                slotOf[i] = setup;
            }
        }
        awaitingKeyframe = false;
        if (!header.showFrame()) {
            return Optional.empty();
        }
        return Optional.of(output(planes, width, height, header.color(), pts));
    }

    private Output output(VideoSession.Planes planes, int width, int height, ColorConfig color, long pts) {
        var format = color.bitDepth() == 8 ? PixelFormat.NV12 : PixelFormat.P010;
        var copied = pictures.copy(planes, format, width, height);
        return new Output(
                copied, pts, matrix(color.colorSpace(), height), color.fullRange(), ColorDescription.UNSPECIFIED);
    }

    /// The matrix of a VP9 `color_space`, as the built-in decoder reads it.
    static VideoFrame.ColorMatrix matrix(int colorSpace, int height) {
        return switch (colorSpace) {
            case ColorConfig.CS_BT_709 -> VideoFrame.ColorMatrix.BT709;
            case ColorConfig.CS_BT_2020 -> VideoFrame.ColorMatrix.BT2020;
            case ColorConfig.CS_BT_601, ColorConfig.CS_SMPTE_170, ColorConfig.CS_SMPTE_240 ->
                VideoFrame.ColorMatrix.BT601;
            default -> height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601;
        };
    }

    /// The slot no reference holds.
    private int freeSlot() {
        for (var slot = 0; slot < SLOTS; slot++) {
            var taken = false;
            for (var held : slotOf) {
                taken |= held == slot;
            }
            if (!taken) {
                return slot;
            }
        }
        throw new IllegalStateException("every slot holds a reference");
    }

    /// The decode of one frame: its bytes, its picture info, its setup slot
    /// and its references, all in `arena`.
    private static VideoSession.Picture picture(
            Arena arena, byte[] frame, UncompressedHeader header, int setup, int[] named, List<Integer> references) {
        var std = Structs.allocate(arena, StdVideoDecodeVP9PictureInfo.LAYOUT);
        var flags = StdVideoDecodeVP9PictureInfo.flags;
        Structs.flag(
                std, flags, StdVideoDecodeVP9PictureInfoFlags.MASK_error_resilient_mode, header.errorResilientMode());
        Structs.flag(std, flags, StdVideoDecodeVP9PictureInfoFlags.MASK_intra_only, header.intraOnly());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeVP9PictureInfoFlags.MASK_allow_high_precision_mv,
                header.allowHighPrecisionMv());
        Structs.flag(
                std, flags, StdVideoDecodeVP9PictureInfoFlags.MASK_refresh_frame_context, header.refreshFrameContext());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeVP9PictureInfoFlags.MASK_frame_parallel_decoding_mode,
                header.frameParallelDecodingMode());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeVP9PictureInfoFlags.MASK_segmentation_enabled,
                header.segmentation().enabled());
        Structs.flag(std, flags, StdVideoDecodeVP9PictureInfoFlags.MASK_show_frame, header.showFrame());
        Structs.flag(std, flags, StdVideoDecodeVP9PictureInfoFlags.MASK_UsePrevFrameMvs, header.usePrevFrameMvs());
        std.set(JAVA_INT, StdVideoDecodeVP9PictureInfo.profile, header.profile());
        std.set(JAVA_INT, StdVideoDecodeVP9PictureInfo.frame_type, header.frameType());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.frame_context_idx, (byte) header.frameContextIdxInForce());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.reset_frame_context, (byte) header.resetFrameContext());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.refresh_frame_flags, (byte) header.refreshFrameFlags());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.ref_frame_sign_bias_mask, (byte) header.refFrameSignBiasMask());
        std.set(JAVA_INT, StdVideoDecodeVP9PictureInfo.interpolation_filter, header.interpolationFilter());
        var q = header.quantization();
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.base_q_idx, (byte) q.baseQIdx());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.delta_q_y_dc, (byte) q.deltaQYDc());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.delta_q_uv_dc, (byte) q.deltaQUvDc());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.delta_q_uv_ac, (byte) q.deltaQUvAc());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.tile_cols_log2, (byte) header.tileColsLog2());
        std.set(JAVA_BYTE, StdVideoDecodeVP9PictureInfo.tile_rows_log2, (byte) header.tileRowsLog2());
        std.set(ADDRESS, StdVideoDecodeVP9PictureInfo.pColorConfig, colorConfig(arena, header.color()));
        std.set(ADDRESS, StdVideoDecodeVP9PictureInfo.pLoopFilter, loopFilter(arena, header));
        std.set(ADDRESS, StdVideoDecodeVP9PictureInfo.pSegmentation, segmentation(arena, header));
        var info = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeVP9PictureInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_VP9_PICTURE_INFO_KHR);
        info.set(ADDRESS, VkStructs.VkVideoDecodeVP9PictureInfoKHR.pStdPictureInfo, std);
        for (var i = 0; i < 3; i++) {
            info.set(JAVA_INT, VkStructs.VkVideoDecodeVP9PictureInfoKHR.referenceNameSlotIndices + 4L * i, named[i]);
        }
        info.set(JAVA_INT, VkStructs.VkVideoDecodeVP9PictureInfoKHR.uncompressedHeaderOffset, 0);
        info.set(
                JAVA_INT,
                VkStructs.VkVideoDecodeVP9PictureInfoKHR.compressedHeaderOffset,
                header.compressedHeaderOffset());
        info.set(JAVA_INT, VkStructs.VkVideoDecodeVP9PictureInfoKHR.tilesOffset, header.tilesOffset());
        var slots = new ArrayList<VideoSession.Slot>();
        for (var reference : references) {
            slots.add(new VideoSession.Slot(reference, MemorySegment.NULL));
        }
        return new VideoSession.Picture(frame, info, new VideoSession.Slot(setup, MemorySegment.NULL), slots, 0);
    }

    private static MemorySegment colorConfig(Arena arena, ColorConfig color) {
        var c = Structs.allocate(arena, StdVideoVP9ColorConfig.LAYOUT);
        Structs.flag(c, StdVideoVP9ColorConfig.flags, StdVideoVP9ColorConfigFlags.MASK_color_range, color.fullRange());
        c.set(JAVA_BYTE, StdVideoVP9ColorConfig.BitDepth, (byte) color.bitDepth());
        c.set(JAVA_BYTE, StdVideoVP9ColorConfig.subsampling_x, (byte) color.subsamplingX());
        c.set(JAVA_BYTE, StdVideoVP9ColorConfig.subsampling_y, (byte) color.subsamplingY());
        c.set(JAVA_INT, StdVideoVP9ColorConfig.color_space, color.colorSpace());
        return c;
    }

    private static MemorySegment loopFilter(Arena arena, UncompressedHeader header) {
        var lf = header.loopFilter();
        var l = Structs.allocate(arena, StdVideoVP9LoopFilter.LAYOUT);
        var flags = StdVideoVP9LoopFilter.flags;
        Structs.flag(l, flags, StdVideoVP9LoopFilterFlags.MASK_loop_filter_delta_enabled, lf.deltaEnabled());
        Structs.flag(l, flags, StdVideoVP9LoopFilterFlags.MASK_loop_filter_delta_update, lf.deltaUpdate());
        l.set(JAVA_BYTE, StdVideoVP9LoopFilter.loop_filter_level, (byte) lf.level());
        l.set(JAVA_BYTE, StdVideoVP9LoopFilter.loop_filter_sharpness, (byte) lf.sharpness());
        l.set(JAVA_BYTE, StdVideoVP9LoopFilter.update_ref_delta, (byte) lf.updateRefDelta());
        l.set(JAVA_BYTE, StdVideoVP9LoopFilter.update_mode_delta, (byte) lf.updateModeDelta());
        for (var i = 0; i < 4; i++) {
            l.set(JAVA_BYTE, StdVideoVP9LoopFilter.loop_filter_ref_deltas + i, (byte)
                    (int) lf.refDeltas().get(i));
        }
        for (var i = 0; i < 2; i++) {
            l.set(JAVA_BYTE, StdVideoVP9LoopFilter.loop_filter_mode_deltas + i, (byte)
                    (int) lf.modeDeltas().get(i));
        }
        return l;
    }

    private static MemorySegment segmentation(Arena arena, UncompressedHeader header) {
        var seg = header.segmentation();
        var s = Structs.allocate(arena, StdVideoVP9Segmentation.LAYOUT);
        var flags = StdVideoVP9Segmentation.flags;
        Structs.flag(s, flags, StdVideoVP9SegmentationFlags.MASK_segmentation_update_map, seg.updateMap());
        Structs.flag(s, flags, StdVideoVP9SegmentationFlags.MASK_segmentation_temporal_update, seg.temporalUpdate());
        Structs.flag(s, flags, StdVideoVP9SegmentationFlags.MASK_segmentation_update_data, seg.updateData());
        Structs.flag(
                s, flags, StdVideoVP9SegmentationFlags.MASK_segmentation_abs_or_delta_update, seg.absOrDeltaUpdate());
        for (var i = 0; i < 7; i++) {
            s.set(JAVA_BYTE, StdVideoVP9Segmentation.segmentation_tree_probs + i, (byte)
                    (int) seg.treeProbs().get(i));
        }
        for (var i = 0; i < 3; i++) {
            s.set(JAVA_BYTE, StdVideoVP9Segmentation.segmentation_pred_prob + i, (byte)
                    (int) seg.predProbs().get(i));
        }
        var features = seg.features();
        for (var segment = 0; segment < 8; segment++) {
            s.set(JAVA_BYTE, StdVideoVP9Segmentation.FeatureEnabled + segment, (byte)
                    (int) features.enabled().get(segment));
            for (var feature = 0; feature < 4; feature++) {
                s.set(JAVA_SHORT, StdVideoVP9Segmentation.FeatureData + 8L * segment + 2L * feature, (short)
                        (int) features.data().get(segment).get(feature));
            }
        }
        return s;
    }

    /// Makes the session for a keyframe whose size or depth the session does
    /// not decode, or the first.
    private void ensureSession(UncompressedHeader header) {
        var width = header.frameWidth();
        var height = header.frameHeight();
        var color = header.color();
        if (session != null && width == sessionWidth && height == sessionHeight && color.bitDepth() == sessionDepth) {
            return;
        }
        if (color.subsamplingX() != 1
                || color.subsamplingY() != 1
                || (color.bitDepth() != 8 && color.bitDepth() != 10)) {
            throw new IllegalArgumentException("only 4:2:0 VP9 of 8 or 10 bits is decoded here");
        }
        var profile = new VideoProfile(VulkanCodec.VP9, header.profile(), color.bitDepth());
        var capabilities = VideoCapabilities.query(device, profile)
                .filter(c -> c.fits(width, height))
                .orElseThrow(() -> new IllegalArgumentException(
                        "the device does not decode " + profile + " at " + width + "×" + height));
        close();
        session = new VideoSession(
                device, profile, capabilities, width, height, Math.min(SLOTS, capabilities.maxDpbSlots()));
        sessionWidth = width;
        sessionHeight = height;
        sessionDepth = color.bitDepth();
        Arrays.fill(slotOf, -1);
    }

    @Override
    public List<Output> drain() {
        return List.of();
    }

    @Override
    public void reset() {
        awaitingKeyframe = true;
    }

    @Override
    public void close() {
        var current = session;
        session = null;
        if (current != null) {
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
}
