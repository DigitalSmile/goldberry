package dev.goldberry.natives.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.Locale;
import java.util.Optional;

import dev.goldberry.natives.vulkan.calls.VkCalls;

/// The Vulkan loader of the system: `libvulkan.so.1` on Linux, `vulkan-1.dll`
/// on Windows. None on macOS, where Vulkan is MoltenVK over Metal, which has
/// neither a video queue nor the ray-tracing extensions the toolkit asks for.
///
/// Opened once a process, for its life, and only asked for
/// `vkGetInstanceProcAddr`: every other function's address comes from it, as
/// the loader's documentation asks, so the calls reach the driver without the
/// loader's trampolines where a device has one of its own.
public final class VulkanLoader {

    private static final Optional<VulkanLoader> LOADED = open();

    private final VkCalls.GetInstanceProcAddr getInstanceProcAddr;

    private VulkanLoader(MemorySegment getInstanceProcAddr) {
        this.getInstanceProcAddr = new VkCalls.GetInstanceProcAddr(getInstanceProcAddr);
    }

    /// The loader, or empty when this system has none: not installed, or a
    /// system the toolkit's Vulkan does not run on.
    public static Optional<VulkanLoader> get() {
        return LOADED;
    }

    @SuppressWarnings("restricted")
    private static Optional<VulkanLoader> open() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name;
        if (os.contains("win")) {
            name = "vulkan-1.dll";
        } else if (os.contains("linux")) {
            name = "libvulkan.so.1";
        } else {
            return Optional.empty();
        }
        try {
            var lookup = SymbolLookup.libraryLookup(name, Arena.global());
            return lookup.find(VkCalls.GetInstanceProcAddr.NAME).map(VulkanLoader::new);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /// The address of `name` for `instance`, or `NULL` with no instance for the
    /// functions that need none.
    public MemorySegment instanceProc(MemorySegment instance, String name) {
        try (var arena = Arena.ofConfined()) {
            return getInstanceProcAddr.call(instance, arena.allocateFrom(name));
        }
    }
}
