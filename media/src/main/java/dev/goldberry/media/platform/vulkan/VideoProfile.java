package dev.goldberry.media.platform.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

import dev.goldberry.media.platform.vulkan.ffm.Structs;
import dev.goldberry.media.platform.vulkan.ffm.VkConstants;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs;

/// A video profile Vulkan is asked about and decodes with: the codec, its
/// profile, 4:2:0, and the bit depth of luma and chroma alike.
///
/// @param codec      the codec
/// @param profileIdc the codec's own profile number: H.264's `profile_idc`,
///                   HEVC's `general_profile_idc`, VP9's and AV1's `profile`
/// @param bitDepth   8 or 10
public record VideoProfile(VulkanCodec codec, int profileIdc, int bitDepth) {

    public VideoProfile {
        Objects.requireNonNull(codec, "codec");
        if (bitDepth != 8 && bitDepth != 10) {
            throw new IllegalArgumentException(bitDepth + " bits a sample");
        }
    }

    /// `VkVideoProfileInfoKHR` with its codec's profile info chained to it.
    MemorySegment write(Arena arena) {
        var profile = Structs.allocate(
                arena, VkStructs.VkVideoProfileInfoKHR.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_VIDEO_PROFILE_INFO_KHR);
        profile.set(JAVA_INT, VkStructs.VkVideoProfileInfoKHR.videoCodecOperation, codec.operation());
        profile.set(
                JAVA_INT,
                VkStructs.VkVideoProfileInfoKHR.chromaSubsampling,
                VkConstants.VK_VIDEO_CHROMA_SUBSAMPLING_420_BIT_KHR);
        var depth = bitDepth == 8
                ? VkConstants.VK_VIDEO_COMPONENT_BIT_DEPTH_8_BIT_KHR
                : VkConstants.VK_VIDEO_COMPONENT_BIT_DEPTH_10_BIT_KHR;
        profile.set(JAVA_INT, VkStructs.VkVideoProfileInfoKHR.lumaBitDepth, depth);
        profile.set(JAVA_INT, VkStructs.VkVideoProfileInfoKHR.chromaBitDepth, depth);
        var codecProfile = switch (codec) {
            case H264 -> {
                var h264 = Structs.allocate(
                        arena,
                        VkStructs.VkVideoDecodeH264ProfileInfoKHR.LAYOUT,
                        VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_PROFILE_INFO_KHR);
                h264.set(JAVA_INT, VkStructs.VkVideoDecodeH264ProfileInfoKHR.stdProfileIdc, profileIdc);
                h264.set(
                        JAVA_INT,
                        VkStructs.VkVideoDecodeH264ProfileInfoKHR.pictureLayout,
                        VkConstants.VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_PROGRESSIVE_KHR);
                yield h264;
            }
            case HEVC -> {
                var hevc = Structs.allocate(
                        arena,
                        VkStructs.VkVideoDecodeH265ProfileInfoKHR.LAYOUT,
                        VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_PROFILE_INFO_KHR);
                hevc.set(JAVA_INT, VkStructs.VkVideoDecodeH265ProfileInfoKHR.stdProfileIdc, profileIdc);
                yield hevc;
            }
            case VP9 -> {
                var vp9 = Structs.allocate(
                        arena,
                        VkStructs.VkVideoDecodeVP9ProfileInfoKHR.LAYOUT,
                        VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_VP9_PROFILE_INFO_KHR);
                vp9.set(JAVA_INT, VkStructs.VkVideoDecodeVP9ProfileInfoKHR.stdProfile, profileIdc);
                yield vp9;
            }
            case AV1 -> {
                var av1 = Structs.allocate(
                        arena,
                        VkStructs.VkVideoDecodeAV1ProfileInfoKHR.LAYOUT,
                        VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_AV1_PROFILE_INFO_KHR);
                av1.set(JAVA_INT, VkStructs.VkVideoDecodeAV1ProfileInfoKHR.stdProfile, profileIdc);
                // Film grain wants an output picture apart from the reference;
                // the sessions here decode into the reference, so a stream
                // with grain goes to the built-in decoder.
                av1.set(JAVA_INT, VkStructs.VkVideoDecodeAV1ProfileInfoKHR.filmGrainSupport, 0);
                yield av1;
            }
        };
        Structs.chain(profile, codecProfile);
        return profile;
    }

    /// `VkVideoProfileListInfoKHR` of this profile alone, which images and
    /// buffers made for it chain.
    MemorySegment writeList(Arena arena) {
        var list = Structs.allocate(
                arena,
                VkStructs.VkVideoProfileListInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_PROFILE_LIST_INFO_KHR);
        list.set(JAVA_INT, VkStructs.VkVideoProfileListInfoKHR.profileCount, 1);
        list.set(ADDRESS, VkStructs.VkVideoProfileListInfoKHR.pProfiles, write(arena));
        return list;
    }
}
