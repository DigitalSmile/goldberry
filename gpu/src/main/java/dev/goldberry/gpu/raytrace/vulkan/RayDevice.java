package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkDeviceFunctions;
import dev.goldberry.natives.vulkan.VkRayTracingFunctions;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;
import dev.goldberry.natives.vulkan.VulkanInstance;
import dev.goldberry.natives.vulkan.VulkanLoader;

/// The ray tracer's Vulkan device: one a process, made at the first ask, and
/// kept for the process's life, as `:media`'s video device is.
///
/// It is not SDL's device. SDL_GPU has no ray tracing and hands out no device
/// of its own, so rays are traced here and what they found is copied through
/// the host into SDL's textures and buffers.
///
/// A device qualifies with `VK_KHR_acceleration_structure`,
/// `VK_KHR_ray_tracing_pipeline` and the features they need, and a queue that
/// computes. Of several, a discrete GPU is preferred, then an integrated one,
/// then anything else, the CPU (lavapipe) last; `goldberry.raytracing.device`
/// names one by part of its name instead.
public final class RayDevice {

    /// Picks the device by part of its name, ignoring case: `llvmpipe` for
    /// lavapipe, `nvidia` for the GPU.
    public static final String DEVICE_PROPERTY = "goldberry.raytracing.device";

    /// The device extensions ray tracing needs.
    static final List<String> EXTENSIONS = List.of(
            VkConstants.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
            VkConstants.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
            VkConstants.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME);

    private static final Logger LOG = Logs.of(RayDevice.class);

    private final MemorySegment handle;
    private final String name;
    private final int queueFamily;
    private final MemorySegment queue;
    private final VkDeviceFunctions functions;
    private final VkRayTracingFunctions rayTracing;
    private final List<VulkanInstance.MemoryType> memoryTypes;
    private final Limits limits;
    private final ReentrantLock submitLock = new ReentrantLock();

    private RayDevice(
            VulkanInstance instance, MemorySegment physical, MemorySegment handle, Candidate chosen, Limits limits) {
        this.handle = handle;
        this.name = chosen.name();
        this.queueFamily = chosen.computeFamily();
        this.limits = limits;
        Function<String, MemorySegment> procAddr = proc -> {
            try (var arena = Arena.ofConfined()) {
                return instance.deviceProc(handle, arena.allocateFrom(proc));
            }
        };
        this.functions = VkDeviceFunctions.resolve(procAddr);
        this.rayTracing = VkRayTracingFunctions.resolve(procAddr);
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            functions.getDeviceQueue().call(handle, queueFamily, 0, out);
            this.queue = out.get(ADDRESS, 0);
        }
        this.memoryTypes = instance.memoryTypes(physical);
    }

    /// What asking for the device found: the device, or why there is none.
    public sealed interface Probe {

        /// A device that traces rays.
        record Ready(RayDevice device) implements Probe {}

        /// No device, and why: no loader, no driver, or no device with the
        /// extensions, each named.
        record Refused(String reason) implements Probe {}
    }

    private static final class Holder {
        static final Probe PROBE = create();
    }

    /// The device, made at the first ask, or why there is none.
    public static Probe probe() {
        return Holder.PROBE;
    }

    /// The device's name, as its driver gives it.
    public String name() {
        return name;
    }

    /// What ray tracing on it is bounded by.
    public Limits limits() {
        return limits;
    }

    VkDeviceFunctions functions() {
        return functions;
    }

    VkRayTracingFunctions rayTracing() {
        return rayTracing;
    }

    MemorySegment handle() {
        return handle;
    }

    int queueFamily() {
        return queueFamily;
    }

    /// Submits `info`, a `VkSubmitInfo`, signalling `fence`. One submission at
    /// a time: two tracers on two threads share the one queue.
    void submit(MemorySegment info, long fence) {
        submitLock.lock();
        try {
            VulkanException.check(functions.queueSubmit().call(queue, 1, info, fence), "vkQueueSubmit");
        } finally {
            submitLock.unlock();
        }
    }

    /// The memory type for a resource that may live in `typeBits` and wants
    /// every flag of `wanted`: the first that has them, else the first it may
    /// live in when `wanted` is a preference (`required` false).
    ///
    /// @throws VulkanException when none has what is required
    int memoryType(int typeBits, int wanted, boolean required) {
        return chooseMemoryType(memoryTypes, typeBits, wanted, required);
    }

    /// The memory type for a buffer the host reads back: host-visible,
    /// coherent and cached where the device has it, and host-visible and
    /// coherent where it does not.
    ///
    /// @throws VulkanException when none is host-visible and coherent
    int readbackMemoryType(int typeBits) {
        return chooseReadbackMemoryType(memoryTypes, typeBits);
    }

    static int chooseReadbackMemoryType(List<VulkanInstance.MemoryType> types, int typeBits) {
        var cached = DeviceBuffer.HOST | VkConstants.VK_MEMORY_PROPERTY_HOST_CACHED_BIT;
        for (var type : types) {
            if ((typeBits & (1 << type.index())) != 0 && (type.flags() & cached) == cached) {
                return type.index();
            }
        }
        return chooseMemoryType(types, typeBits, DeviceBuffer.HOST, true);
    }

    static int chooseMemoryType(List<VulkanInstance.MemoryType> types, int typeBits, int wanted, boolean required) {
        for (var type : types) {
            if ((typeBits & (1 << type.index())) != 0 && (type.flags() & wanted) == wanted) {
                return type.index();
            }
        }
        if (!required) {
            for (var type : types) {
                if ((typeBits & (1 << type.index())) != 0) {
                    return type.index();
                }
            }
        }
        throw new VulkanException("no memory type of bits " + Integer.toBinaryString(typeBits) + " has flags 0x"
                + Integer.toHexString(wanted));
    }

    /// What one physical device offers, read before choosing.
    ///
    /// @param index         its place in the instance's list
    /// @param name          its name
    /// @param type          its `VkPhysicalDeviceType`
    /// @param extensions    whether it has every extension of [#EXTENSIONS]
    /// @param features      whether it has every feature ray tracing needs
    /// @param computeFamily a queue family that computes, or -1
    record Candidate(int index, String name, int type, boolean extensions, boolean features, int computeFamily) {

        /// Whether rays can be traced on it.
        boolean usable() {
            return extensions && features && computeFamily >= 0;
        }

        /// Why it cannot trace rays; empty when it can.
        String refusal() {
            if (!extensions) {
                return name + " has no " + String.join(" / ", EXTENSIONS);
            }
            if (!features) {
                return name + " has the extensions but not their features";
            }
            if (computeFamily < 0) {
                return name + " has no queue that computes";
            }
            return "";
        }

        /// Lower is preferred: a discrete GPU, an integrated one, a virtual one,
        /// anything else, the CPU.
        int rank() {
            return switch (type) {
                case VkConstants.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 0;
                case VkConstants.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 1;
                case VkConstants.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> 2;
                case VkConstants.VK_PHYSICAL_DEVICE_TYPE_CPU -> 4;
                default -> 3;
            };
        }
    }

    /// The device to trace on: the usable one whose name has `wanted` in it
    /// when that is given, else the best ranked usable one.
    static Optional<Candidate> choose(List<Candidate> candidates, @Nullable String wanted) {
        var usable = candidates.stream().filter(Candidate::usable);
        if (wanted != null && !wanted.isBlank()) {
            var needle = wanted.trim().toLowerCase(Locale.ROOT);
            usable = usable.filter(c -> c.name().toLowerCase(Locale.ROOT).contains(needle));
        }
        return usable.min(Comparator.comparingInt(Candidate::rank).thenComparingInt(Candidate::index));
    }

    /// Why none of `candidates` was chosen.
    static String refusal(List<Candidate> candidates, @Nullable String wanted) {
        if (candidates.isEmpty()) {
            return "no Vulkan device";
        }
        if (wanted != null && !wanted.isBlank()) {
            var named = candidates.stream()
                    .filter(c -> c.name()
                            .toLowerCase(Locale.ROOT)
                            .contains(wanted.trim().toLowerCase(Locale.ROOT)))
                    .toList();
            if (named.isEmpty()) {
                return "no Vulkan device is named like '" + wanted.trim() + "' ("
                        + candidates.stream().map(Candidate::name).collect(Collectors.joining(", ")) + ")";
            }
            return named.stream().map(Candidate::refusal).collect(Collectors.joining("; "));
        }
        return candidates.stream().map(Candidate::refusal).collect(Collectors.joining("; "));
    }

    /// What ray tracing on a device is bounded by.
    ///
    /// @param handleSize       the size of a shader group's handle
    /// @param handleAlignment  what a handle's place in a table aligns to
    /// @param baseAlignment    what a table's region starts on
    /// @param scratchAlignment what a build's scratch memory starts on
    /// @param maxRecursion     how deep rays may trace rays
    /// @param maxDispatch      how many rays one dispatch may start
    public record Limits(
            int handleSize,
            int handleAlignment,
            int baseAlignment,
            int scratchAlignment,
            int maxRecursion,
            long maxDispatch) {}

    private static Probe create() {
        var loader = VulkanLoader.get();
        if (loader.isEmpty()) {
            return new Probe.Refused("no Vulkan loader on this system");
        }
        try {
            var instance = VulkanInstance.create(loader.get(), "goldberry-gpu");
            var physicals = instance.physicalDevices();
            var candidates = new ArrayList<Candidate>();
            for (var i = 0; i < physicals.size(); i++) {
                candidates.add(inspect(instance, i, physicals.get(i)));
            }
            var wanted = System.getProperty(DEVICE_PROPERTY);
            var chosen = choose(candidates, wanted);
            if (chosen.isEmpty()) {
                var reason = refusal(candidates, wanted);
                LOG.debug("no ray tracing: {}", reason);
                return new Probe.Refused(reason);
            }
            var physical = physicals.get(chosen.get().index());
            var limits = limits(instance, physical);
            var handle = createDevice(instance, physical, chosen.get().computeFamily());
            LOG.debug("rays are traced on {} ({})", chosen.get().name(), limits);
            return new Probe.Ready(new RayDevice(instance, physical, handle, chosen.get(), limits));
        } catch (VulkanException e) {
            LOG.debug("no ray tracing: {}", e.getMessage());
            return new Probe.Refused(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static Candidate inspect(VulkanInstance instance, int index, MemorySegment physical) {
        int type;
        String name;
        try (var arena = Arena.ofConfined()) {
            var properties = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceProperties2.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2);
            instance.properties(physical, properties);
            var at = VkStructs.VkPhysicalDeviceProperties2.properties;
            type = properties.get(JAVA_INT, at + VkStructs.VkPhysicalDeviceProperties.deviceType);
            name = properties.getString(at + VkStructs.VkPhysicalDeviceProperties.deviceName);
        }
        var extensions = instance.deviceExtensions(physical).containsAll(EXTENSIONS);
        var features = extensions && features(instance, physical);
        var compute = instance.queueFamilies(physical, false).stream()
                .filter(VulkanInstance.QueueFamily::computes)
                .mapToInt(VulkanInstance.QueueFamily::index)
                .findFirst()
                .orElse(-1);
        return new Candidate(index, name, type, extensions, features, compute);
    }

    private static boolean features(VulkanInstance instance, MemorySegment physical) {
        try (var arena = Arena.ofConfined()) {
            var chain = FeatureChain.allocate(arena);
            instance.features(physical, chain.root());
            return chain.supported();
        }
    }

    private static Limits limits(VulkanInstance instance, MemorySegment physical) {
        try (var arena = Arena.ofConfined()) {
            var properties = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceProperties2.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2);
            var pipeline = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceRayTracingPipelinePropertiesKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR);
            var structures = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceAccelerationStructurePropertiesKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR);
            Structs.chain(properties, pipeline);
            Structs.chain(pipeline, structures);
            instance.properties(physical, properties);
            return new Limits(
                    pipeline.get(
                            JAVA_INT, VkStructs.VkPhysicalDeviceRayTracingPipelinePropertiesKHR.shaderGroupHandleSize),
                    pipeline.get(
                            JAVA_INT,
                            VkStructs.VkPhysicalDeviceRayTracingPipelinePropertiesKHR.shaderGroupHandleAlignment),
                    pipeline.get(
                            JAVA_INT,
                            VkStructs.VkPhysicalDeviceRayTracingPipelinePropertiesKHR.shaderGroupBaseAlignment),
                    structures.get(
                            JAVA_INT,
                            VkStructs.VkPhysicalDeviceAccelerationStructurePropertiesKHR
                                    .minAccelerationStructureScratchOffsetAlignment),
                    pipeline.get(
                            JAVA_INT, VkStructs.VkPhysicalDeviceRayTracingPipelinePropertiesKHR.maxRayRecursionDepth),
                    Integer.toUnsignedLong(pipeline.get(
                            JAVA_INT,
                            VkStructs.VkPhysicalDeviceRayTracingPipelinePropertiesKHR.maxRayDispatchInvocationCount)));
        }
    }

    private static MemorySegment createDevice(VulkanInstance instance, MemorySegment physical, int family) {
        try (var arena = Arena.ofConfined()) {
            var queue = Structs.allocate(
                    arena,
                    VkStructs.VkDeviceQueueCreateInfo.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            queue.set(JAVA_INT, VkStructs.VkDeviceQueueCreateInfo.queueFamilyIndex, family);
            queue.set(JAVA_INT, VkStructs.VkDeviceQueueCreateInfo.queueCount, 1);
            queue.set(ADDRESS, VkStructs.VkDeviceQueueCreateInfo.pQueuePriorities, arena.allocateFrom(JAVA_FLOAT, 1f));
            var names = arena.allocate(ADDRESS, EXTENSIONS.size());
            for (var i = 0; i < EXTENSIONS.size(); i++) {
                names.setAtIndex(ADDRESS, i, arena.allocateFrom(EXTENSIONS.get(i)));
            }
            var chain = FeatureChain.allocate(arena);
            chain.enable();
            var info = Structs.allocate(
                    arena, VkStructs.VkDeviceCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            Structs.chain(info, chain.root());
            info.set(JAVA_INT, VkStructs.VkDeviceCreateInfo.queueCreateInfoCount, 1);
            info.set(ADDRESS, VkStructs.VkDeviceCreateInfo.pQueueCreateInfos, queue);
            info.set(JAVA_INT, VkStructs.VkDeviceCreateInfo.enabledExtensionCount, EXTENSIONS.size());
            info.set(ADDRESS, VkStructs.VkDeviceCreateInfo.ppEnabledExtensionNames, names);
            var device = arena.allocate(ADDRESS);
            VulkanException.check(instance.createDevice(physical, info, device), "vkCreateDevice");
            return device.get(ADDRESS, 0);
        }
    }

    /// The features ray tracing needs, as one `VkPhysicalDeviceFeatures2`
    /// chain: buffer device addresses (1.2), synchronization2 (1.3),
    /// acceleration structures and ray-tracing pipelines.
    private record FeatureChain(
            MemorySegment root,
            MemorySegment vulkan12,
            MemorySegment vulkan13,
            MemorySegment structures,
            MemorySegment pipelines) {

        static FeatureChain allocate(Arena arena) {
            var root = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceFeatures2.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2);
            var vulkan12 = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceVulkan12Features.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES);
            var vulkan13 = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceVulkan13Features.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES);
            var structures = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceAccelerationStructureFeaturesKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR);
            var pipelines = Structs.allocate(
                    arena,
                    VkStructs.VkPhysicalDeviceRayTracingPipelineFeaturesKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR);
            Structs.chain(root, vulkan12);
            Structs.chain(vulkan12, vulkan13);
            Structs.chain(vulkan13, structures);
            Structs.chain(structures, pipelines);
            return new FeatureChain(root, vulkan12, vulkan13, structures, pipelines);
        }

        /// Whether a device that filled this has every feature.
        boolean supported() {
            return vulkan12.get(JAVA_INT, VkStructs.VkPhysicalDeviceVulkan12Features.bufferDeviceAddress) != 0
                    && vulkan13.get(JAVA_INT, VkStructs.VkPhysicalDeviceVulkan13Features.synchronization2) != 0
                    && structures.get(
                                    JAVA_INT,
                                    VkStructs.VkPhysicalDeviceAccelerationStructureFeaturesKHR.accelerationStructure)
                            != 0
                    && pipelines.get(
                                    JAVA_INT,
                                    VkStructs.VkPhysicalDeviceRayTracingPipelineFeaturesKHR.rayTracingPipeline)
                            != 0;
        }

        /// Asks for every feature, for a device being created.
        void enable() {
            vulkan12.set(JAVA_INT, VkStructs.VkPhysicalDeviceVulkan12Features.bufferDeviceAddress, 1);
            vulkan13.set(JAVA_INT, VkStructs.VkPhysicalDeviceVulkan13Features.synchronization2, 1);
            structures.set(
                    JAVA_INT, VkStructs.VkPhysicalDeviceAccelerationStructureFeaturesKHR.accelerationStructure, 1);
            pipelines.set(JAVA_INT, VkStructs.VkPhysicalDeviceRayTracingPipelineFeaturesKHR.rayTracingPipeline, 1);
        }
    }

    @Override
    public String toString() {
        return "RayDevice[" + name + "]";
    }
}
