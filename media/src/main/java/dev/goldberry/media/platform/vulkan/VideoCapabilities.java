package dev.goldberry.media.platform.vulkan;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.platform.vulkan.ffm.Structs;
import dev.goldberry.media.platform.vulkan.ffm.VkConstants;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs;
import dev.goldberry.media.platform.vulkan.ffm.VulkanException;

/// What the device decodes of one [VideoProfile]: the sizes, the decoded
/// picture buffer, the bitstream buffer's alignments, the highest level, and
/// the format pictures are decoded into.
///
/// @param minBitstreamOffsetAlignment where a picture's bitstream may start in
///                                    the buffer, a multiple of this
/// @param minBitstreamSizeAlignment   how much of the buffer a picture's
///                                    bitstream is read as, a multiple of this
/// @param granularityWidth            the width a picture's region is rounded to
/// @param granularityHeight           the height likewise
/// @param minWidth                    the smallest coded width
/// @param minHeight                   the smallest coded height
/// @param maxWidth                    the largest coded width
/// @param maxHeight                   the largest coded height
/// @param maxDpbSlots                 how many pictures the decoded picture
///                                    buffer holds
/// @param maxActiveReferences         how many a picture refers to at most
/// @param stdVersion                  the `StdVideo` header version supported
/// @param decodeFlags                 `VkVideoDecodeCapabilityFlagsKHR`: whether
///                                    the picture buffer and the output may be
///                                    the same images
/// @param maxLevel                    the codec's highest level, in its `StdVideo`
///                                    enumeration
/// @param format                      the `VkFormat` pictures are decoded into
/// @param usage                       the image usages the format allows
public record VideoCapabilities(
        long minBitstreamOffsetAlignment,
        long minBitstreamSizeAlignment,
        int granularityWidth,
        int granularityHeight,
        int minWidth,
        int minHeight,
        int maxWidth,
        int maxHeight,
        int maxDpbSlots,
        int maxActiveReferences,
        int stdVersion,
        int decodeFlags,
        int maxLevel,
        int format,
        int usage) {

    /// Whether the decoded picture buffer's images can be the output too.
    public boolean dpbIsOutput() {
        return (decodeFlags & VkConstants.VK_VIDEO_DECODE_CAPABILITY_DPB_AND_OUTPUT_COINCIDE_BIT_KHR) != 0;
    }

    /// Whether a `width` by `height` picture is within the sizes.
    public boolean fits(int width, int height) {
        return width >= minWidth && height >= minHeight && width <= maxWidth && height <= maxHeight;
    }

    /// `profile` on `device`, or empty when the device does not decode it, or
    /// cannot hand its pictures back to be copied.
    public static Optional<VideoCapabilities> query(VideoDevice device, VideoProfile profile) {
        if ((device.operations() & profile.codec().operation()) == 0) {
            return Optional.empty();
        }
        try (var arena = Arena.ofConfined()) {
            var capabilities = Structs.allocate(
                    arena,
                    VkStructs.VkVideoCapabilitiesKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_VIDEO_CAPABILITIES_KHR);
            var decode = Structs.allocate(
                    arena,
                    VkStructs.VkVideoDecodeCapabilitiesKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_CAPABILITIES_KHR);
            Structs.chain(capabilities, decode);
            var codecCapabilities = switch (profile.codec()) {
                case H264 ->
                    Structs.allocate(
                            arena,
                            VkStructs.VkVideoDecodeH264CapabilitiesKHR.LAYOUT,
                            VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_CAPABILITIES_KHR);
                case HEVC ->
                    Structs.allocate(
                            arena,
                            VkStructs.VkVideoDecodeH265CapabilitiesKHR.LAYOUT,
                            VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_CAPABILITIES_KHR);
                case VP9 ->
                    Structs.allocate(
                            arena,
                            VkStructs.VkVideoDecodeVP9CapabilitiesKHR.LAYOUT,
                            VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_VP9_CAPABILITIES_KHR);
                case AV1 ->
                    Structs.allocate(
                            arena,
                            VkStructs.VkVideoDecodeAV1CapabilitiesKHR.LAYOUT,
                            VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_AV1_CAPABILITIES_KHR);
            };
            Structs.chain(decode, codecCapabilities);
            var profileInfo = profile.write(arena);
            var result = device.instance().videoCapabilities(device.physicalDevice(), profileInfo, capabilities);
            if (result < 0) {
                return Optional.empty();
            }
            var maxLevel = switch (profile.codec()) {
                case H264 -> codecCapabilities.get(JAVA_INT, VkStructs.VkVideoDecodeH264CapabilitiesKHR.maxLevelIdc);
                case HEVC -> codecCapabilities.get(JAVA_INT, VkStructs.VkVideoDecodeH265CapabilitiesKHR.maxLevelIdc);
                case VP9 -> codecCapabilities.get(JAVA_INT, VkStructs.VkVideoDecodeVP9CapabilitiesKHR.maxLevel);
                case AV1 -> codecCapabilities.get(JAVA_INT, VkStructs.VkVideoDecodeAV1CapabilitiesKHR.maxLevel);
            };
            var decodeFlags = decode.get(JAVA_INT, VkStructs.VkVideoDecodeCapabilitiesKHR.flags);
            var usage = VkConstants.VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR
                    | ((decodeFlags & VkConstants.VK_VIDEO_DECODE_CAPABILITY_DPB_AND_OUTPUT_COINCIDE_BIT_KHR) != 0
                            ? VkConstants.VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR
                            : 0);
            var formats = formats(device, profile, usage, arena);
            if (formats.isEmpty()) {
                return Optional.empty();
            }
            var chosen = formats.getFirst();
            if ((chosen[1] & VkConstants.VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0
                    || (decodeFlags & VkConstants.VK_VIDEO_DECODE_CAPABILITY_DPB_AND_OUTPUT_COINCIDE_BIT_KHR) == 0) {
                // The pictures are copied back from the picture buffer itself.
                return Optional.empty();
            }
            var granularity = VkStructs.VkVideoCapabilitiesKHR.pictureAccessGranularity;
            var min = VkStructs.VkVideoCapabilitiesKHR.minCodedExtent;
            var max = VkStructs.VkVideoCapabilitiesKHR.maxCodedExtent;
            return Optional.of(new VideoCapabilities(
                    capabilities.get(JAVA_LONG, VkStructs.VkVideoCapabilitiesKHR.minBitstreamBufferOffsetAlignment),
                    capabilities.get(JAVA_LONG, VkStructs.VkVideoCapabilitiesKHR.minBitstreamBufferSizeAlignment),
                    capabilities.get(JAVA_INT, granularity + VkStructs.VkExtent2D.width),
                    capabilities.get(JAVA_INT, granularity + VkStructs.VkExtent2D.height),
                    capabilities.get(JAVA_INT, min + VkStructs.VkExtent2D.width),
                    capabilities.get(JAVA_INT, min + VkStructs.VkExtent2D.height),
                    capabilities.get(JAVA_INT, max + VkStructs.VkExtent2D.width),
                    capabilities.get(JAVA_INT, max + VkStructs.VkExtent2D.height),
                    capabilities.get(JAVA_INT, VkStructs.VkVideoCapabilitiesKHR.maxDpbSlots),
                    capabilities.get(JAVA_INT, VkStructs.VkVideoCapabilitiesKHR.maxActiveReferencePictures),
                    capabilities.get(
                            JAVA_INT,
                            VkStructs.VkVideoCapabilitiesKHR.stdHeaderVersion
                                    + VkStructs.VkExtensionProperties.specVersion),
                    decodeFlags,
                    maxLevel,
                    chosen[0],
                    chosen[1]));
        } catch (VulkanException e) {
            return Optional.empty();
        }
    }

    /// The formats `profile` decodes into for `usage`, each its `VkFormat` and
    /// the usages it allows, the driver's preferred first.
    private static List<int[]> formats(VideoDevice device, VideoProfile profile, int usage, Arena arena) {
        var info = Structs.allocate(
                arena,
                VkStructs.VkPhysicalDeviceVideoFormatInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VIDEO_FORMAT_INFO_KHR);
        info.set(JAVA_INT, VkStructs.VkPhysicalDeviceVideoFormatInfoKHR.imageUsage, usage);
        Structs.chain(info, profile.writeList(arena));
        var count = arena.allocate(JAVA_INT);
        if (device.instance().videoFormats(device.physicalDevice(), info, count, MemorySegment.NULL) < 0) {
            return List.of();
        }
        var n = count.get(JAVA_INT, 0);
        var layout = VkStructs.VkVideoFormatPropertiesKHR.LAYOUT;
        var out = Structs.array(arena, layout, n, VkConstants.VK_STRUCTURE_TYPE_VIDEO_FORMAT_PROPERTIES_KHR);
        if (device.instance().videoFormats(device.physicalDevice(), info, count, out) < 0) {
            return List.of();
        }
        var all = new ArrayList<int[]>();
        for (var i = 0; i < count.get(JAVA_INT, 0); i++) {
            var properties = Structs.at(out, layout, i);
            all.add(new int[] {
                properties.get(JAVA_INT, VkStructs.VkVideoFormatPropertiesKHR.format),
                properties.get(JAVA_INT, VkStructs.VkVideoFormatPropertiesKHR.imageUsageFlags)
            });
        }
        return all;
    }
}
