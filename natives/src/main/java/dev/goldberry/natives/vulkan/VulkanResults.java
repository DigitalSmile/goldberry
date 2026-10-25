package dev.goldberry.natives.vulkan;

/// The names of the `VkResult`s the toolkit meets, for messages.
public final class VulkanResults {

    private VulkanResults() {}

    /// `result`'s name, or its number.
    public static String name(int result) {
        return switch (result) {
            case VkConstants.VK_SUCCESS -> "VK_SUCCESS";
            case VkConstants.VK_NOT_READY -> "VK_NOT_READY";
            case VkConstants.VK_TIMEOUT -> "VK_TIMEOUT";
            case VkConstants.VK_INCOMPLETE -> "VK_INCOMPLETE";
            case VkConstants.VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
            case VkConstants.VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
            case VkConstants.VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
            case VkConstants.VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
            case VkConstants.VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
            case VkConstants.VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
            case VkConstants.VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
            case VkConstants.VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
            case VkConstants.VK_ERROR_VIDEO_PROFILE_OPERATION_NOT_SUPPORTED_KHR ->
                "VK_ERROR_VIDEO_PROFILE_OPERATION_NOT_SUPPORTED_KHR";
            case VkConstants.VK_ERROR_VIDEO_PROFILE_FORMAT_NOT_SUPPORTED_KHR ->
                "VK_ERROR_VIDEO_PROFILE_FORMAT_NOT_SUPPORTED_KHR";
            case VkConstants.VK_ERROR_VIDEO_PROFILE_CODEC_NOT_SUPPORTED_KHR ->
                "VK_ERROR_VIDEO_PROFILE_CODEC_NOT_SUPPORTED_KHR";
            case VkConstants.VK_ERROR_VIDEO_STD_VERSION_NOT_SUPPORTED_KHR ->
                "VK_ERROR_VIDEO_STD_VERSION_NOT_SUPPORTED_KHR";
            case VkConstants.VK_ERROR_INVALID_VIDEO_STD_PARAMETERS_KHR -> "VK_ERROR_INVALID_VIDEO_STD_PARAMETERS_KHR";
            default -> "VkResult " + result;
        };
    }
}
