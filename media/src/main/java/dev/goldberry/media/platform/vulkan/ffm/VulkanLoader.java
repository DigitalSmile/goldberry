package dev.goldberry.media.platform.vulkan.ffm;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.Optional;

/// The Vulkan loader of the system: `libvulkan.so.1` on Linux, `vulkan-1.dll`
/// on Windows. None on macOS, whose Vulkan (MoltenVK) has no video queue.
///
/// Opened once a process, for its life, and only asked for
/// `vkGetInstanceProcAddr`: every other function's address comes from it, as
/// the loader's documentation asks, so the calls reach the driver without the
/// loader's trampolines where a device has one of its own.
public final class VulkanLoader {

    private static final FunctionDescriptor PROC_ADDR = FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS);
    private static final MethodHandle GET_PROC_ADDR = VulkanLinker.link(PROC_ADDR);

    private static final Optional<VulkanLoader> LOADED = open();

    private final MemorySegment getInstanceProcAddr;

    private VulkanLoader(MemorySegment getInstanceProcAddr) {
        this.getInstanceProcAddr = getInstanceProcAddr;
    }

    /// The loader, or empty when this system has none: not installed, or a
    /// system Vulkan Video does not run on.
    public static Optional<VulkanLoader> get() {
        return LOADED;
    }

    /// The descriptor `vkGetInstanceProcAddr` is called with, for the native
    /// image's foreign metadata.
    public static FunctionDescriptor procAddrDescriptor() {
        return PROC_ADDR;
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
            return lookup.find("vkGetInstanceProcAddr").map(VulkanLoader::new);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /// The address of `name` for `instance`, or `NULL` with no instance for the
    /// functions that need none.
    public MemorySegment instanceProc(MemorySegment instance, String name) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) GET_PROC_ADDR.invokeExact(getInstanceProcAddr, instance, arena.allocateFrom(name));
        } catch (Throwable t) {
            throw VulkanLinker.failure("vkGetInstanceProcAddr", t);
        }
    }
}
