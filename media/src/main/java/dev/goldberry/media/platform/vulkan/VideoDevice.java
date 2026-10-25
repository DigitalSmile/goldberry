package dev.goldberry.media.platform.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkDeviceFunctions;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VkVideoFunctions;
import dev.goldberry.natives.vulkan.VulkanException;
import dev.goldberry.natives.vulkan.VulkanInstance;
import dev.goldberry.natives.vulkan.VulkanLoader;

/// The one Vulkan device a process decodes video with: a physical device with
/// a queue family that decodes and one that copies, made the first time a
/// decoder asks, and kept for the life of the process.
///
/// Every decode extension the device offers is enabled, H.264, HEVC, VP9 and
/// AV1, with synchronization2. One decode queue and one copy queue are shared
/// by every decoder; a queue is submitted to under its lock, as Vulkan asks of
/// a queue used from more than one thread.
///
/// Where there is none (no loader, no driver of 1.3, no device that decodes),
/// [#get()] is empty and the provider supports nothing.
public final class VideoDevice {

    private static final Logger LOG = Logs.of(VideoDevice.class);

    /// The decode extensions, each with the codec operation it adds.
    private static final List<String> CODEC_EXTENSIONS = List.of(
            VkConstants.VK_KHR_VIDEO_DECODE_H264_EXTENSION_NAME,
            VkConstants.VK_KHR_VIDEO_DECODE_H265_EXTENSION_NAME,
            VkConstants.VK_KHR_VIDEO_DECODE_VP9_EXTENSION_NAME,
            VkConstants.VK_KHR_VIDEO_DECODE_AV1_EXTENSION_NAME);

    private static final Optional<VideoDevice> DEVICE = create();

    private final VulkanInstance instance;
    private final MemorySegment physicalDevice;
    private final MemorySegment handle;
    private final VkDeviceFunctions functions;
    private final VkVideoFunctions video;
    private final String name;
    private final int decodeFamily;
    private final int transferFamily;
    private final int operations;
    private final MemorySegment decodeQueue;
    private final MemorySegment transferQueue;
    private final ReentrantLock decodeLock = new ReentrantLock();
    private final ReentrantLock transferLock = new ReentrantLock();
    private final List<VulkanInstance.MemoryType> memoryTypes;

    private VideoDevice(
            VulkanInstance instance,
            MemorySegment physicalDevice,
            MemorySegment handle,
            String name,
            int decodeFamily,
            int transferFamily,
            int operations) {
        this.instance = instance;
        this.physicalDevice = physicalDevice;
        this.handle = handle;
        this.name = name;
        this.decodeFamily = decodeFamily;
        this.transferFamily = transferFamily;
        this.operations = operations;
        Function<String, MemorySegment> procAddr = proc -> {
            try (var arena = Arena.ofConfined()) {
                return instance.deviceProc(handle, arena.allocateFrom(proc));
            }
        };
        this.functions = VkDeviceFunctions.resolve(procAddr);
        this.video = VkVideoFunctions.resolve(procAddr);
        try (var arena = Arena.ofConfined()) {
            var queue = arena.allocate(ADDRESS);
            functions.getDeviceQueue().call(handle, decodeFamily, 0, queue);
            this.decodeQueue = queue.get(ADDRESS, 0);
            functions.getDeviceQueue().call(handle, transferFamily, 0, queue);
            this.transferQueue = queue.get(ADDRESS, 0);
        }
        this.memoryTypes = instance.memoryTypes(physicalDevice);
    }

    /// The device, made at the first ask; empty where there is none.
    public static Optional<VideoDevice> get() {
        return DEVICE;
    }

    private static Optional<VideoDevice> create() {
        var loader = VulkanLoader.get();
        if (loader.isEmpty()) {
            return Optional.empty();
        }
        try {
            var instance = VulkanInstance.create(loader.get(), "goldberry-media");
            for (var physical : instance.physicalDevices()) {
                var extensions = instance.deviceExtensions(physical);
                if (!extensions.contains(VkConstants.VK_KHR_VIDEO_QUEUE_EXTENSION_NAME)
                        || !extensions.contains(VkConstants.VK_KHR_VIDEO_DECODE_QUEUE_EXTENSION_NAME)) {
                    continue;
                }
                var families = instance.queueFamilies(physical);
                var decode = families.stream()
                        .filter(VulkanInstance.QueueFamily::decodes)
                        .findFirst();
                var transfer = families.stream()
                        .filter(VulkanInstance.QueueFamily::transfers)
                        .findFirst();
                if (decode.isEmpty() || transfer.isEmpty()) {
                    continue;
                }
                var codecs = new ArrayList<String>();
                for (var extension : CODEC_EXTENSIONS) {
                    if (extensions.contains(extension)) {
                        codecs.add(extension);
                    }
                }
                if (codecs.isEmpty()) {
                    continue;
                }
                var name = instance.name(physical);
                var device = createDevice(
                        instance, physical, decode.get().index(), transfer.get().index(), codecs);
                LOG.debug("Vulkan Video decodes on {} ({})", name, codecs);
                return Optional.of(new VideoDevice(
                        instance,
                        physical,
                        device,
                        name,
                        decode.get().index(),
                        transfer.get().index(),
                        decode.get().operations()));
            }
            return Optional.empty();
        } catch (VulkanException e) {
            LOG.debug("no Vulkan Video: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static MemorySegment createDevice(
            VulkanInstance instance,
            MemorySegment physical,
            int decodeFamily,
            int transferFamily,
            List<String> codecs) {
        try (var arena = Arena.ofConfined()) {
            var familyCount = decodeFamily == transferFamily ? 1 : 2;
            var queueLayout = VkStructs.VkDeviceQueueCreateInfo.LAYOUT;
            var queues = Structs.array(
                    arena, queueLayout, familyCount, VkConstants.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            var priority = arena.allocateFrom(JAVA_FLOAT, 1f);
            var indices = familyCount == 1 ? new int[] {decodeFamily} : new int[] {decodeFamily, transferFamily};
            for (var i = 0; i < familyCount; i++) {
                var queue = Structs.at(queues, queueLayout, i);
                queue.set(JAVA_INT, VkStructs.VkDeviceQueueCreateInfo.queueFamilyIndex, indices[i]);
                queue.set(JAVA_INT, VkStructs.VkDeviceQueueCreateInfo.queueCount, 1);
                queue.set(ADDRESS, VkStructs.VkDeviceQueueCreateInfo.pQueuePriorities, priority);
            }
            var names = new ArrayList<String>();
            names.add(VkConstants.VK_KHR_VIDEO_QUEUE_EXTENSION_NAME);
            names.add(VkConstants.VK_KHR_VIDEO_DECODE_QUEUE_EXTENSION_NAME);
            names.addAll(codecs);
            var extensions = arena.allocate(ADDRESS, names.size());
            for (var i = 0; i < names.size(); i++) {
                extensions.setAtIndex(ADDRESS, i, arena.allocateFrom(names.get(i)));
            }
            var vulkan13 = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceVulkan13Features.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES);
            vulkan13.set(JAVA_INT, VkStructs.VkPhysicalDeviceVulkan13Features.synchronization2, 1);
            var info = Structs.allocate(
                    arena, VkStructs.VkDeviceCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            Structs.chain(info, vulkan13);
            info.set(JAVA_INT, VkStructs.VkDeviceCreateInfo.queueCreateInfoCount, familyCount);
            info.set(ADDRESS, VkStructs.VkDeviceCreateInfo.pQueueCreateInfos, queues);
            info.set(JAVA_INT, VkStructs.VkDeviceCreateInfo.enabledExtensionCount, names.size());
            info.set(ADDRESS, VkStructs.VkDeviceCreateInfo.ppEnabledExtensionNames, extensions);
            var device = arena.allocate(ADDRESS);
            VulkanException.check(instance.createDevice(physical, info, device), "vkCreateDevice");
            return device.get(ADDRESS, 0);
        }
    }

    VulkanInstance instance() {
        return instance;
    }

    MemorySegment physicalDevice() {
        return physicalDevice;
    }

    MemorySegment handle() {
        return handle;
    }

    VkDeviceFunctions functions() {
        return functions;
    }

    /// The video functions, which this device enabled.
    VkVideoFunctions video() {
        return video;
    }

    /// The device's name, for messages.
    public String name() {
        return name;
    }

    int decodeFamily() {
        return decodeFamily;
    }

    int transferFamily() {
        return transferFamily;
    }

    /// The video codec operations the decode queue performs.
    int operations() {
        return operations;
    }

    MemorySegment decodeQueue() {
        return decodeQueue;
    }

    MemorySegment transferQueue() {
        return transferQueue;
    }

    ReentrantLock decodeLock() {
        return decodeLock;
    }

    ReentrantLock transferLock() {
        return transferLock;
    }

    /// The first memory type `typeBits` allows with every flag of `required`.
    ///
    /// @throws VulkanException when there is none
    int memoryType(int typeBits, int required) {
        for (var type : memoryTypes) {
            if ((typeBits & (1 << type.index())) != 0 && (type.flags() & required) == required) {
                return type.index();
            }
        }
        throw new VulkanException(
                "no memory type of " + Integer.toBinaryString(typeBits) + " is " + Integer.toHexString(required));
    }

    @Override
    public String toString() {
        return "VideoDevice[" + name + ", decode family " + decodeFamily + ", copy family " + transferFamily + "]";
    }
}
