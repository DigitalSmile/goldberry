package dev.goldberry.media.platform.vulkan;

import java.util.Optional;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.platform.vulkan.ffm.VkConstants;

/// The codecs Vulkan Video decodes, each with its codec operation and the
/// version of its `StdVideo` headers the bindings were written against.
public enum VulkanCodec {
    H264(
            CodecId.H264,
            VkConstants.VK_VIDEO_CODEC_OPERATION_DECODE_H264_BIT_KHR,
            VkConstants.VK_STD_VULKAN_VIDEO_CODEC_H264_DECODE_EXTENSION_NAME,
            VkConstants.VK_STD_VULKAN_VIDEO_CODEC_H264_DECODE_SPEC_VERSION),
    HEVC(
            CodecId.HEVC,
            VkConstants.VK_VIDEO_CODEC_OPERATION_DECODE_H265_BIT_KHR,
            VkConstants.VK_STD_VULKAN_VIDEO_CODEC_H265_DECODE_EXTENSION_NAME,
            VkConstants.VK_STD_VULKAN_VIDEO_CODEC_H265_DECODE_SPEC_VERSION),
    VP9(
            CodecId.VP9,
            VkConstants.VK_VIDEO_CODEC_OPERATION_DECODE_VP9_BIT_KHR,
            VkConstants.VK_STD_VULKAN_VIDEO_CODEC_VP9_DECODE_EXTENSION_NAME,
            VkConstants.VK_STD_VULKAN_VIDEO_CODEC_VP9_DECODE_SPEC_VERSION),
    AV1(
            CodecId.AV1,
            VkConstants.VK_VIDEO_CODEC_OPERATION_DECODE_AV1_BIT_KHR,
            VkConstants.VK_STD_VULKAN_VIDEO_CODEC_AV1_DECODE_EXTENSION_NAME,
            VkConstants.VK_STD_VULKAN_VIDEO_CODEC_AV1_DECODE_SPEC_VERSION);

    private final CodecId codec;
    private final int operation;
    private final String stdName;
    private final int stdVersion;

    VulkanCodec(CodecId codec, int operation, String stdName, int stdVersion) {
        this.codec = codec;
        this.operation = operation;
        this.stdName = stdName;
        this.stdVersion = stdVersion;
    }

    /// The SPI's codec.
    public CodecId codec() {
        return codec;
    }

    /// `VkVideoCodecOperationFlagBitsKHR`.
    int operation() {
        return operation;
    }

    /// The `StdVideo` header's extension name, for `VkExtensionProperties`.
    String stdName() {
        return stdName;
    }

    /// The `StdVideo` header's version.
    int stdVersion() {
        return stdVersion;
    }

    /// The Vulkan codec for `codec`, if it is one.
    public static Optional<VulkanCodec> of(CodecId codec) {
        for (var candidate : values()) {
            if (candidate.codec == codec) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
