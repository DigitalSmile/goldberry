package dev.goldberry.media.platform.vulkan.ffm;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/// A Vulkan instance of API version 1.3, made for video decoding, with the
/// instance-level functions resolved through it.
///
/// No layers and no instance extensions: the video extensions are a device's.
/// Destroyed only with the process, as the video device made from it is
/// shared by every decoder.
public final class VulkanInstance {

    /// Vulkan 1.3: synchronization2 and the video extensions' requirements are
    /// core there.
    public static final int API_VERSION = VkConstants.VK_API_VERSION_1_3;

    private final VulkanLoader loader;
    private final MemorySegment handle;
    private final VkCalls.EnumeratePhysicalDevices enumeratePhysicalDevices;
    private final VkCalls.GetPhysicalDeviceProperties2 physicalDeviceProperties;
    private final VkCalls.GetPhysicalDeviceQueueFamilyProperties2 queueFamilyProperties;
    private final VkCalls.GetPhysicalDeviceMemoryProperties memoryProperties;
    private final VkCalls.EnumerateDeviceExtensionProperties deviceExtensions;
    private final VkCalls.GetPhysicalDeviceVideoCapabilitiesKHR videoCapabilities;
    private final VkCalls.GetPhysicalDeviceVideoFormatPropertiesKHR videoFormats;
    private final VkCalls.CreateDevice createDevice;
    private final VkCalls.GetDeviceProcAddr deviceProcAddr;

    private VulkanInstance(VulkanLoader loader, MemorySegment handle) {
        this.loader = loader;
        this.handle = handle;
        this.enumeratePhysicalDevices =
                new VkCalls.EnumeratePhysicalDevices(proc(VkCalls.EnumeratePhysicalDevices.NAME));
        this.physicalDeviceProperties =
                new VkCalls.GetPhysicalDeviceProperties2(proc(VkCalls.GetPhysicalDeviceProperties2.NAME));
        this.queueFamilyProperties = new VkCalls.GetPhysicalDeviceQueueFamilyProperties2(
                proc(VkCalls.GetPhysicalDeviceQueueFamilyProperties2.NAME));
        this.memoryProperties =
                new VkCalls.GetPhysicalDeviceMemoryProperties(proc(VkCalls.GetPhysicalDeviceMemoryProperties.NAME));
        this.deviceExtensions =
                new VkCalls.EnumerateDeviceExtensionProperties(proc(VkCalls.EnumerateDeviceExtensionProperties.NAME));
        this.videoCapabilities = new VkCalls.GetPhysicalDeviceVideoCapabilitiesKHR(
                proc(VkCalls.GetPhysicalDeviceVideoCapabilitiesKHR.NAME));
        this.videoFormats = new VkCalls.GetPhysicalDeviceVideoFormatPropertiesKHR(
                proc(VkCalls.GetPhysicalDeviceVideoFormatPropertiesKHR.NAME));
        this.createDevice = new VkCalls.CreateDevice(proc(VkCalls.CreateDevice.NAME));
        this.deviceProcAddr = new VkCalls.GetDeviceProcAddr(proc(VkCalls.GetDeviceProcAddr.NAME));
    }

    /// An instance through `loader`.
    ///
    /// @throws VulkanException when the loader or driver refuses: no driver, or
    ///                         one older than 1.3
    public static VulkanInstance create(VulkanLoader loader) {
        var version = new VkCalls.EnumerateInstanceVersion(
                loader.instanceProc(MemorySegment.NULL, VkCalls.EnumerateInstanceVersion.NAME));
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_INT);
            VulkanException.check(version.call(out), "vkEnumerateInstanceVersion");
            if (Integer.compareUnsigned(out.get(JAVA_INT, 0), API_VERSION) < 0) {
                throw new VulkanException("the Vulkan loader is older than 1.3");
            }
            var app = Structs.allocate(
                    arena, VkStructs.VkApplicationInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_APPLICATION_INFO);
            app.set(ADDRESS, VkStructs.VkApplicationInfo.pApplicationName, arena.allocateFrom("goldberry-media"));
            app.set(ADDRESS, VkStructs.VkApplicationInfo.pEngineName, arena.allocateFrom("goldberry"));
            app.set(JAVA_INT, VkStructs.VkApplicationInfo.apiVersion, API_VERSION);
            var info = Structs.allocate(
                    arena, VkStructs.VkInstanceCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            info.set(ADDRESS, VkStructs.VkInstanceCreateInfo.pApplicationInfo, app);
            var create =
                    new VkCalls.CreateInstance(loader.instanceProc(MemorySegment.NULL, VkCalls.CreateInstance.NAME));
            var handle = arena.allocate(ADDRESS);
            VulkanException.check(create.call(info, MemorySegment.NULL, handle), "vkCreateInstance");
            return new VulkanInstance(loader, handle.get(ADDRESS, 0));
        }
    }

    private MemorySegment proc(String name) {
        return loader.instanceProc(handle, name);
    }

    /// Creates a logical device on `physicalDevice` from `info`, the handle
    /// written to `device`.
    public int createDevice(MemorySegment physicalDevice, MemorySegment info, MemorySegment device) {
        return createDevice.call(physicalDevice, info, MemorySegment.NULL, device);
    }

    /// The address of the device function `name` for `device`.
    public MemorySegment deviceProc(MemorySegment device, MemorySegment name) {
        return deviceProcAddr.call(device, name);
    }

    /// The memory types of `physicalDevice`, each with its property flags.
    public List<MemoryType> memoryTypes(MemorySegment physicalDevice) {
        try (var arena = Arena.ofConfined()) {
            var properties = Structs.allocate(arena, VkStructs.VkPhysicalDeviceMemoryProperties.LAYOUT);
            memoryProperties.call(physicalDevice, properties);
            var count = properties.get(JAVA_INT, VkStructs.VkPhysicalDeviceMemoryProperties.memoryTypeCount);
            var types = new ArrayList<MemoryType>(count);
            for (var i = 0; i < count; i++) {
                var at = VkStructs.VkPhysicalDeviceMemoryProperties.memoryTypes
                        + i * VkStructs.VkMemoryType.LAYOUT.byteSize();
                types.add(new MemoryType(i, properties.get(JAVA_INT, at + VkStructs.VkMemoryType.propertyFlags)));
            }
            return types;
        }
    }

    /// One memory type of a physical device: its index, and its property flags.
    public record MemoryType(int index, int flags) {}

    /// Asks `physicalDevice` for the capabilities of `profile` into
    /// `capabilities`, a chain the caller made.
    public int videoCapabilities(MemorySegment physicalDevice, MemorySegment profile, MemorySegment capabilities) {
        return videoCapabilities.call(physicalDevice, profile, capabilities);
    }

    /// Asks `physicalDevice` for the formats of `info`.
    public int videoFormats(MemorySegment physicalDevice, MemorySegment info, MemorySegment count, MemorySegment out) {
        return videoFormats.call(physicalDevice, info, count, out);
    }

    /// Every physical device.
    public List<MemorySegment> physicalDevices() {
        try (var arena = Arena.ofConfined()) {
            var count = arena.allocate(JAVA_INT);
            VulkanException.check(
                    enumeratePhysicalDevices.call(handle, count, MemorySegment.NULL), "vkEnumeratePhysicalDevices");
            var n = count.get(JAVA_INT, 0);
            var devices = arena.allocate(ADDRESS, Math.max(n, 1));
            VulkanException.check(enumeratePhysicalDevices.call(handle, count, devices), "vkEnumeratePhysicalDevices");
            var all = new ArrayList<MemorySegment>(n);
            for (var i = 0; i < count.get(JAVA_INT, 0); i++) {
                all.add(devices.getAtIndex(ADDRESS, i));
            }
            return all;
        }
    }

    /// The names of the device extensions `physicalDevice` offers.
    public List<String> deviceExtensions(MemorySegment physicalDevice) {
        try (var arena = Arena.ofConfined()) {
            var count = arena.allocate(JAVA_INT);
            VulkanException.check(
                    deviceExtensions.call(physicalDevice, MemorySegment.NULL, count, MemorySegment.NULL),
                    "vkEnumerateDeviceExtensionProperties");
            var layout = VkStructs.VkExtensionProperties.LAYOUT;
            var properties = arena.allocate(layout, Math.max(count.get(JAVA_INT, 0), 1));
            VulkanException.check(
                    deviceExtensions.call(physicalDevice, MemorySegment.NULL, count, properties),
                    "vkEnumerateDeviceExtensionProperties");
            var names = new ArrayList<String>();
            for (var i = 0; i < count.get(JAVA_INT, 0); i++) {
                names.add(Structs.at(properties, layout, i).getString(VkStructs.VkExtensionProperties.extensionName));
            }
            return names;
        }
    }

    /// `physicalDevice`'s name, from its properties.
    public String name(MemorySegment physicalDevice) {
        try (var arena = Arena.ofConfined()) {
            var properties = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceProperties2.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2);
            physicalDeviceProperties.call(physicalDevice, properties);
            return properties.getString(
                    VkStructs.VkPhysicalDeviceProperties2.properties + VkStructs.VkPhysicalDeviceProperties.deviceName);
        }
    }

    /// The queue families of `physicalDevice`: their flags, and for each the
    /// video codec operations it performs.
    public List<QueueFamily> queueFamilies(MemorySegment physicalDevice) {
        try (var arena = Arena.ofConfined()) {
            var count = arena.allocate(JAVA_INT);
            queueFamilyProperties.call(physicalDevice, count, MemorySegment.NULL);
            var n = count.get(JAVA_INT, 0);
            var layout = VkStructs.VkQueueFamilyProperties2.LAYOUT;
            var videoLayout = VkStructs.VkQueueFamilyVideoPropertiesKHR.LAYOUT;
            var families = Structs.array(arena, layout, n, VkConstants.VK_STRUCTURE_TYPE_QUEUE_FAMILY_PROPERTIES_2);
            var video = Structs.array(
                    arena, videoLayout, n, VkConstants.VK_STRUCTURE_TYPE_QUEUE_FAMILY_VIDEO_PROPERTIES_KHR);
            for (var i = 0; i < n; i++) {
                Structs.chain(Structs.at(families, layout, i), Structs.at(video, videoLayout, i));
            }
            queueFamilyProperties.call(physicalDevice, count, families);
            var all = new ArrayList<QueueFamily>(n);
            for (var i = 0; i < n; i++) {
                var family = Structs.at(families, layout, i);
                var flags = family.get(
                        JAVA_INT,
                        VkStructs.VkQueueFamilyProperties2.queueFamilyProperties
                                + VkStructs.VkQueueFamilyProperties.queueFlags);
                var operations = Structs.at(video, videoLayout, i)
                        .get(JAVA_INT, VkStructs.VkQueueFamilyVideoPropertiesKHR.videoCodecOperations);
                all.add(new QueueFamily(i, flags, operations));
            }
            return all;
        }
    }

    /// One queue family of a physical device.
    ///
    /// @param index      its index
    /// @param flags      `VkQueueFlags`
    /// @param operations the video codec operations its queues perform
    public record QueueFamily(int index, int flags, int operations) {

        /// Whether its queues decode video.
        public boolean decodes() {
            return (flags & VkConstants.VK_QUEUE_VIDEO_DECODE_BIT_KHR) != 0 && operations != 0;
        }

        /// Whether its queues copy.
        public boolean transfers() {
            return (flags
                            & (VkConstants.VK_QUEUE_TRANSFER_BIT
                                    | VkConstants.VK_QUEUE_GRAPHICS_BIT
                                    | VkConstants.VK_QUEUE_COMPUTE_BIT))
                    != 0;
        }
    }
}
