package dev.goldberry.natives.vulkan;

import java.io.Serial;

/// A Vulkan call that failed, or a loader or driver without what the caller
/// needs. A video decoder's failure moves the player to the next rung of its
/// fallback ladder; a ray tracer's leaves the device with no ray tracing.
public final class VulkanException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// A failure with no `VkResult`.
    public VulkanException(String message) {
        this(message, 0);
    }

    /// A call that answered `result`, a negative `VkResult`, which the message
    /// names.
    public VulkanException(String message, int result) {
        super(result == 0 ? message : message + " (" + VulkanResults.name(result) + ")");
    }

    /// A failure caused by `cause`.
    public VulkanException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Throws for a `VkResult` that is an error; returns it otherwise.
    public static int check(int result, String call) {
        if (result < 0) {
            throw new VulkanException(call, result);
        }
        return result;
    }
}
