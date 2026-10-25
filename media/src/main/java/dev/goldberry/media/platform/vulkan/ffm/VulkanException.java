package dev.goldberry.media.platform.vulkan.ffm;

/// A Vulkan call that failed, or a loader or driver without what the decoder
/// needs. The provider refuses on it, and the decoder's failure moves the
/// player to the next rung of the fallback ladder.
public final class VulkanException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int result;

    /// A failure with no `VkResult`.
    public VulkanException(String message) {
        this(message, 0);
    }

    /// A call that answered `result`, a negative `VkResult`.
    public VulkanException(String message, int result) {
        super(result == 0 ? message : message + " (" + VulkanResults.name(result) + ")");
        this.result = result;
    }

    /// A failure caused by `cause`.
    public VulkanException(String message, Throwable cause) {
        super(message, cause);
        this.result = 0;
    }

    /// The `VkResult`, or 0 when there was none.
    public int result() {
        return result;
    }

    /// Throws for a `VkResult` that is an error; returns it otherwise.
    public static int check(int result, String call) {
        if (result < 0) {
            throw new VulkanException(call, result);
        }
        return result;
    }
}
