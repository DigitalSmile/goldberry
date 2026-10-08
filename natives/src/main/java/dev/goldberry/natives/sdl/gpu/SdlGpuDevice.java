package dev.goldberry.natives.sdl.gpu;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dev.goldberry.natives.WindowPointers;
import dev.goldberry.natives.layout.Layouts;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.SdlWindowHandle;
import dev.goldberry.natives.sdl.calls.SdlGpuPipelineCalls;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuAddressMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBlend;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBufferUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuPresentMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuSampleCount;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureType;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;

/// An SDL GPU device: Metal on macOS, Direct3D 12 on Windows, Vulkan elsewhere,
/// or whichever the options name.
///
/// The toolkit makes one per process, the first time something needs it, and
/// never at start-up. Everything made from a device is
/// released when the device closes, if it has not been already.
///
/// Needs SDL's video subsystem, under a video driver that can make a Metal view
/// or a Vulkan surface; [#create] says which is missing when it cannot.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html#what-the-module-does-to-a-window) and
/// [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuDevice implements AutoCloseable {

    /// How a device is asked for.
    ///
    /// @param shaderFormats  the bytecode formats the caller can supply. SDL only
    ///                       considers the drivers that take one of them, so this
    ///                       is also how a driver is chosen
    /// @param debugMode      the driver's validation, where it has one: slower,
    ///                       and what the tests want
    /// @param preferLowPower an integrated GPU over a discrete one, where there
    ///                       are both
    /// @param driver         a driver by name (`metal`, `vulkan`, `direct3d12`),
    ///                       or empty for SDL's choice
    public record Options(
            Set<SdlGpuShaderFormat> shaderFormats, boolean debugMode, boolean preferLowPower, Optional<String> driver) {

        /// Checks there is a format to ask for, and copies the set.
        public Options {
            if (shaderFormats.isEmpty()) {
                throw new IllegalArgumentException("a device needs at least one shader format");
            }
            shaderFormats = Collections.unmodifiableSet(EnumSet.copyOf(shaderFormats));
            Objects.requireNonNull(driver, "driver");
        }

        /// Every format the toolkit ships shaders in — SPIR-V, DXIL and MSL, compiled
        /// offline and committed — no validation, the low-power GPU, and SDL's choice
        /// of driver.
        public static Options defaults() {
            return new Options(
                    EnumSet.of(SdlGpuShaderFormat.SPIRV, SdlGpuShaderFormat.DXIL, SdlGpuShaderFormat.MSL),
                    false,
                    true,
                    Optional.empty());
        }

        /// These options with the driver's validation on or off.
        public Options withDebugMode(boolean on) {
            return new Options(shaderFormats, on, preferLowPower, driver);
        }

        /// These options asking for `name`'s driver.
        public Options withDriver(String name) {
            return new Options(shaderFormats, debugMode, preferLowPower, Optional.of(name));
        }
    }

    private static final String DEBUG_MODE = "SDL.gpu.device.create.debugmode";
    private static final String PREFER_LOW_POWER = "SDL.gpu.device.create.preferlowpower";
    private static final String VERBOSE = "SDL.gpu.device.create.verbose";
    private static final String NAME = "SDL.gpu.device.create.name";

    /// Bound on strings read back from SDL: driver names are a dozen bytes.
    private static final long MAX_NAME_LENGTH = 256;

    private final GpuCalls calls;
    private final MemorySegment handle;
    private final String driver;
    private final Set<SdlGpuShaderFormat> shaderFormats;
    private final Set<SdlGpuResource> resources = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    private SdlGpuDevice(GpuCalls calls, MemorySegment handle) {
        this.calls = calls;
        this.handle = handle;
        this.driver = readName(calls.device().getGPUDeviceDriver().call(handle));
        this.shaderFormats = Collections.unmodifiableSet(
                SdlGpuShaderFormat.decode(calls.device().getGPUShaderFormats().call(handle)));
    }

    /// The GPU drivers this build of SDL has compiled in, in SDL's order of
    /// preference. Needs no initialisation.
    public static List<String> compiledDrivers() {
        var device = GpuCalls.get().device();
        var count = device.getNumGPUDrivers().call();
        var names = new ArrayList<String>(count);
        for (var i = 0; i < count; i++) {
            names.add(readName(device.getGPUDriver().call(i)));
        }
        return List.copyOf(names);
    }

    /// Creates a device.
    ///
    /// @throws IllegalStateException when SDL's video subsystem is not
    ///                               initialised, which SDL needs to make any
    /// @throws SdlException          when no driver could be created: none takes
    ///                               the formats asked for, the video driver can
    ///                               make neither a Metal view nor a Vulkan
    ///                               surface (SDL's `dummy`), or there is no GPU
    public static SdlGpuDevice create(Options options) {
        var sdl = Sdl.get();
        if (!sdl.wasInit().contains(SdlSubsystem.VIDEO)) {
            throw new IllegalStateException("SDL_GPU needs SDL's video subsystem initialised first");
        }
        var calls = GpuCalls.get();
        var properties = calls.properties();
        var props = properties.createProperties().call();
        if (props == 0) {
            throw new SdlException("SDL_CreateProperties", sdl.lastError());
        }
        try (var arena = Arena.ofConfined()) {
            for (var format : options.shaderFormats()) {
                setBoolean(properties, props, arena, format.createProperty(), true);
            }
            setBoolean(properties, props, arena, DEBUG_MODE, options.debugMode());
            setBoolean(properties, props, arena, PREFER_LOW_POWER, options.preferLowPower());
            setBoolean(properties, props, arena, VERBOSE, false);
            if (options.driver().isPresent()) {
                var name = options.driver().get();
                if (!properties.setStringProperty().call(props, arena.allocateFrom(NAME), arena.allocateFrom(name))) {
                    throw new SdlException("SDL_SetStringProperty", sdl.lastError());
                }
            }
            var device = calls.device().createGPUDeviceWithProperties().call(props);
            if (MemorySegment.NULL.equals(device)) {
                throw new SdlException(
                        "SDL_CreateGPUDeviceWithProperties",
                        sdl.lastError() + " (video driver " + sdl.videoDriver() + ")");
            }
            return new SdlGpuDevice(calls, device);
        } finally {
            properties.destroyProperties().call(props);
        }
    }

    private static void setBoolean(
            dev.goldberry.natives.sdl.calls.SdlPropertiesCalls properties,
            int props,
            Arena arena,
            String name,
            boolean value) {
        if (!properties.setBooleanProperty().call(props, arena.allocateFrom(name), value)) {
            throw new SdlException("SDL_SetBooleanProperty", Sdl.get().lastError());
        }
    }

    /// The driver this device runs on: `metal`, `vulkan` or `direct3d12`.
    public String driver() {
        return driver;
    }

    /// The shader formats this device accepts.
    public Set<SdlGpuShaderFormat> shaderFormats() {
        return shaderFormats;
    }

    /// Whether this device can make a 2D texture of `format` for `usages`.
    public boolean supports(SdlGpuTextureFormat format, Set<SdlGpuTextureUsage> usages) {
        return supports(SdlGpuTextureType.TWO_D, format, usages);
    }

    /// Whether this device can make a texture of `type` and `format` for
    /// `usages`.
    public boolean supports(SdlGpuTextureType type, SdlGpuTextureFormat format, Set<SdlGpuTextureUsage> usages) {
        return calls.device()
                .textureSupportsFormat()
                .call(handle(), format.value(), type.value(), SdlGpuTextureUsage.mask(usages));
    }

    /// Whether this device can make a texture of `format` multisampled at
    /// `samples`: always for [SdlGpuSampleCount#ONE], and as the driver says for
    /// the others.
    public boolean supportsSamples(SdlGpuTextureFormat format, SdlGpuSampleCount samples) {
        return samples == SdlGpuSampleCount.ONE
                || calls.device().textureSupportsSampleCount().call(handle(), format.value(), samples.value());
    }

    /// Creates a 2D texture with one layer, one mip level and one sample.
    ///
    /// @throws SdlException when SDL refuses: a format the device cannot make
    ///                      for these usages, or a size beyond its limit
    public SdlGpuTexture createTexture(
            SdlGpuTextureFormat format, int width, int height, Set<SdlGpuTextureUsage> usages) {
        return createTexture(format, width, height, 1, 1, SdlGpuSampleCount.ONE, usages);
    }

    /// Creates a 2D texture of `layers` layers (an array when more than one),
    /// `mipLevels` mip levels and `sampleCount` samples per texel.
    ///
    /// @throws IllegalArgumentException as the form with a type does
    /// @throws SdlException             as the form with a type does
    public SdlGpuTexture createTexture(
            SdlGpuTextureFormat format,
            int width,
            int height,
            int layers,
            int mipLevels,
            SdlGpuSampleCount sampleCount,
            Set<SdlGpuTextureUsage> usages) {
        return createTexture(
                layers > 1 ? SdlGpuTextureType.TWO_D_ARRAY : SdlGpuTextureType.TWO_D,
                format,
                width,
                height,
                layers,
                mipLevels,
                sampleCount,
                usages);
    }

    /// Creates a texture of `type`: `layersOrDepth` is the layers of a 2D
    /// texture or an array, six for a cube, and the depth of a 3D texture. It
    /// has `mipLevels` mip levels and `sampleCount` samples per texel.
    ///
    /// @throws IllegalArgumentException when the size, a count or the usages
    ///                                  make no texture: more levels than the
    ///                                  size halves into; a multisampled
    ///                                  texture that is not 2D, has more than
    ///                                  one level, or is sampled rather than a
    ///                                  render target; a 2D texture of more
    ///                                  than one layer; a cube that is not
    ///                                  square or not six layers; a 3D texture
    ///                                  that is a depth target; or a
    ///                                  block-compressed texture that is not a
    ///                                  whole number of blocks, or is anything
    ///                                  but sampled
    /// @throws SdlException             when SDL refuses: a format the device
    ///                                  cannot make for these usages, or a size
    ///                                  beyond its limit
    public SdlGpuTexture createTexture(
            SdlGpuTextureType type,
            SdlGpuTextureFormat format,
            int width,
            int height,
            int layersOrDepth,
            int mipLevels,
            SdlGpuSampleCount sampleCount,
            Set<SdlGpuTextureUsage> usages) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(sampleCount, "sampleCount");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture " + width + "x" + height);
        }
        if (usages.isEmpty()) {
            throw new IllegalArgumentException("a texture needs at least one usage");
        }
        if (layersOrDepth <= 0) {
            throw new IllegalArgumentException("texture of " + layersOrDepth + " layers or slices");
        }
        var depth = type == SdlGpuTextureType.THREE_D ? layersOrDepth : 1;
        var layers = type == SdlGpuTextureType.THREE_D ? 1 : layersOrDepth;
        var maxLevels = maxMipLevels(width, height, depth);
        if (mipLevels <= 0 || mipLevels > maxLevels) {
            throw new IllegalArgumentException(mipLevels + " mip levels; a " + width + "x" + height
                    + (depth > 1 ? "x" + depth : "") + " texture has at most " + maxLevels);
        }
        switch (type) {
            case TWO_D -> {
                if (layers != 1) {
                    throw new IllegalArgumentException("a 2D texture has one layer, not " + layers);
                }
            }
            case TWO_D_ARRAY -> {}
            case CUBE -> {
                if (width != height || layers != 6) {
                    throw new IllegalArgumentException(
                            "a cube is square with six layers, not " + width + "x" + height + " x" + layers);
                }
            }
            case THREE_D -> {
                if (usages.contains(SdlGpuTextureUsage.DEPTH_STENCIL_TARGET)) {
                    throw new IllegalArgumentException("a 3D texture is not a depth target");
                }
            }
        }
        if (sampleCount != SdlGpuSampleCount.ONE) {
            if (type != SdlGpuTextureType.TWO_D || mipLevels > 1) {
                throw new IllegalArgumentException("a multisampled texture is 2D, with one layer and one mip level");
            }
            var targetOnly = EnumSet.of(SdlGpuTextureUsage.COLOR_TARGET, SdlGpuTextureUsage.DEPTH_STENCIL_TARGET);
            if (!targetOnly.containsAll(usages)) {
                throw new IllegalArgumentException("a multisampled texture is a render target only, not " + usages);
            }
        }
        if (format.isCompressed()) {
            if (width % format.blockWidth() != 0 || height % format.blockHeight() != 0) {
                throw new IllegalArgumentException(format + " is stored in " + format.blockWidth() + "x"
                        + format.blockHeight() + " blocks, and " + width + "x" + height + " is not whole blocks");
            }
            if (!EnumSet.of(SdlGpuTextureUsage.SAMPLER).containsAll(usages)) {
                throw new IllegalArgumentException(format + " is block-compressed and only sampled, not " + usages);
            }
        }
        var info = Layouts.SDL_GPU_TEXTURE_CREATE_INFO;
        try (var arena = Arena.ofConfined()) {
            var createInfo = arena.allocate(info.layout());
            createInfo.set(JAVA_INT, info.offsetOf("type"), type.value());
            createInfo.set(JAVA_INT, info.offsetOf("format"), format.value());
            createInfo.set(JAVA_INT, info.offsetOf("usage"), SdlGpuTextureUsage.mask(usages));
            createInfo.set(JAVA_INT, info.offsetOf("width"), width);
            createInfo.set(JAVA_INT, info.offsetOf("height"), height);
            createInfo.set(JAVA_INT, info.offsetOf("layer_count_or_depth"), layersOrDepth);
            createInfo.set(JAVA_INT, info.offsetOf("num_levels"), mipLevels);
            createInfo.set(JAVA_INT, info.offsetOf("sample_count"), sampleCount.value());
            var texture = calls.resources().createGPUTexture().call(handle(), createInfo);
            if (MemorySegment.NULL.equals(texture)) {
                throw new SdlException("SDL_CreateGPUTexture", Sdl.get().lastError());
            }
            return new SdlGpuTexture(
                    this, texture, type, format, width, height, depth, layers, mipLevels, sampleCount, usages);
        }
    }

    /// How many mip levels a `width` by `height` texture can have: one per
    /// halving down to one texel, counting the full-size level.
    public static int maxMipLevels(int width, int height) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
    }

    /// How many mip levels a `width` by `height` by `depth` volume can have:
    /// one per halving of its longest axis down to one texel.
    public static int maxMipLevels(int width, int height, int depth) {
        return maxMipLevels(Math.max(width, depth), height);
    }

    /// Creates a transfer buffer of `size` bytes.
    ///
    /// @throws SdlException when SDL refuses
    public SdlGpuTransferBuffer createTransferBuffer(SdlGpuTransferUsage usage, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("transfer buffer of " + size + " bytes");
        }
        var info = Layouts.SDL_GPU_TRANSFER_BUFFER_CREATE_INFO;
        try (var arena = Arena.ofConfined()) {
            var createInfo = arena.allocate(info.layout());
            createInfo.set(JAVA_INT, info.offsetOf("usage"), usage.value());
            createInfo.set(JAVA_INT, info.offsetOf("size"), size);
            var buffer = calls.resources().createGPUTransferBuffer().call(handle(), createInfo);
            if (MemorySegment.NULL.equals(buffer)) {
                throw new SdlException("SDL_CreateGPUTransferBuffer", Sdl.get().lastError());
            }
            return new SdlGpuTransferBuffer(this, buffer, usage, size);
        }
    }

    /// Creates a buffer of `size` bytes for `usages`.
    ///
    /// @throws SdlException when SDL refuses
    public SdlGpuBuffer createBuffer(Set<SdlGpuBufferUsage> usages, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("buffer of " + size + " bytes");
        }
        if (usages.isEmpty()) {
            throw new IllegalArgumentException("a buffer needs at least one usage");
        }
        var info = Layouts.SDL_GPU_BUFFER_CREATE_INFO;
        try (var arena = Arena.ofConfined()) {
            var createInfo = arena.allocate(info.layout());
            createInfo.set(JAVA_INT, info.offsetOf("usage"), SdlGpuBufferUsage.mask(usages));
            createInfo.set(JAVA_INT, info.offsetOf("size"), size);
            var buffer = calls.buffers().createGPUBuffer().call(handle(), createInfo);
            if (MemorySegment.NULL.equals(buffer)) {
                throw new SdlException("SDL_CreateGPUBuffer", Sdl.get().lastError());
            }
            return new SdlGpuBuffer(this, buffer, usages, size);
        }
    }

    /// Acquires a command buffer to record into. It must be submitted or
    /// cancelled.
    ///
    /// @throws SdlException when SDL refuses
    public SdlGpuCommandBuffer acquireCommandBuffer() {
        var commandBuffer = calls.commands().acquireGPUCommandBuffer().call(handle());
        if (MemorySegment.NULL.equals(commandBuffer)) {
            throw new SdlException("SDL_AcquireGPUCommandBuffer", Sdl.get().lastError());
        }
        return new SdlGpuCommandBuffer(this, commandBuffer);
    }

    /// Creates a shader from `code`, which must be in one of this device's
    /// [#shaderFormats].
    ///
    /// @throws IllegalArgumentException when the device does not take the format
    /// @throws SdlException             when SDL refuses the bytecode
    public SdlGpuShader createShader(SdlGpuShaderCode code) {
        if (!shaderFormats.contains(code.format())) {
            throw new IllegalArgumentException(this + " takes " + shaderFormats + ", not " + code.format());
        }
        var info = Layouts.SDL_GPU_SHADER_CREATE_INFO;
        try (var arena = Arena.ofConfined()) {
            var bytes = arena.allocateFrom(JAVA_BYTE, code.code());
            var createInfo = arena.allocate(info.layout());
            createInfo.set(JAVA_LONG, info.offsetOf("code_size"), code.size());
            createInfo.set(ADDRESS, info.offsetOf("code"), bytes);
            createInfo.set(ADDRESS, info.offsetOf("entrypoint"), arena.allocateFrom(code.entryPoint()));
            createInfo.set(JAVA_INT, info.offsetOf("format"), code.format().bit());
            createInfo.set(JAVA_INT, info.offsetOf("stage"), code.stage().value());
            createInfo.set(JAVA_INT, info.offsetOf("num_samplers"), code.samplers());
            createInfo.set(JAVA_INT, info.offsetOf("num_storage_textures"), code.storageTextures());
            createInfo.set(JAVA_INT, info.offsetOf("num_storage_buffers"), code.storageBuffers());
            createInfo.set(JAVA_INT, info.offsetOf("num_uniform_buffers"), code.uniformBuffers());
            var shader = calls.pipelines().createGPUShader().call(handle(), createInfo);
            if (MemorySegment.NULL.equals(shader)) {
                throw new SdlException("SDL_CreateGPUShader", Sdl.get().lastError());
            }
            return new SdlGpuShader(this, shader, code);
        }
    }

    /// Creates a compute pipeline from `code`.
    ///
    /// @throws IllegalArgumentException when the code is in a format this
    ///                                  device does not take
    /// @throws SdlException             when SDL refuses the code
    public SdlGpuComputePipeline createComputePipeline(SdlGpuComputeCode code) {
        if (!shaderFormats.contains(code.format())) {
            throw new IllegalArgumentException(this + " takes " + shaderFormats + ", not " + code.format());
        }
        var info = Layouts.SDL_GPU_COMPUTE_PIPELINE_CREATE_INFO;
        try (var arena = Arena.ofConfined()) {
            var bytes = arena.allocateFrom(JAVA_BYTE, code.code());
            var createInfo = arena.allocate(info.layout());
            createInfo.set(JAVA_LONG, info.offsetOf("code_size"), code.size());
            createInfo.set(ADDRESS, info.offsetOf("code"), bytes);
            createInfo.set(ADDRESS, info.offsetOf("entrypoint"), arena.allocateFrom(code.entryPoint()));
            createInfo.set(JAVA_INT, info.offsetOf("format"), code.format().bit());
            createInfo.set(JAVA_INT, info.offsetOf("num_samplers"), code.samplers());
            createInfo.set(JAVA_INT, info.offsetOf("num_readonly_storage_textures"), code.readOnlyStorageTextures());
            createInfo.set(JAVA_INT, info.offsetOf("num_readonly_storage_buffers"), code.readOnlyStorageBuffers());
            createInfo.set(JAVA_INT, info.offsetOf("num_readwrite_storage_textures"), code.readWriteStorageTextures());
            createInfo.set(JAVA_INT, info.offsetOf("num_readwrite_storage_buffers"), code.readWriteStorageBuffers());
            createInfo.set(JAVA_INT, info.offsetOf("num_uniform_buffers"), code.uniformBuffers());
            createInfo.set(JAVA_INT, info.offsetOf("threadcount_x"), code.threadsX());
            createInfo.set(JAVA_INT, info.offsetOf("threadcount_y"), code.threadsY());
            createInfo.set(JAVA_INT, info.offsetOf("threadcount_z"), code.threadsZ());
            var pipeline = calls.pipelines().createGPUComputePipeline().call(handle(), createInfo);
            if (MemorySegment.NULL.equals(pipeline)) {
                throw new SdlException("SDL_CreateGPUComputePipeline", Sdl.get().lastError());
            }
            return new SdlGpuComputePipeline(this, pipeline, code);
        }
    }

    /// Creates a sampler that filters with `filter`, clamps to the edge, and
    /// reads the one mip level the toolkit's textures have.
    ///
    /// @throws SdlException when SDL refuses
    public SdlGpuSampler createSampler(SdlGpuFilter filter) {
        return createSampler(filter, SdlGpuAddressMode.CLAMP_TO_EDGE);
    }

    /// Creates a sampler that filters with `filter`, reads outside 0 to 1 as
    /// `addressMode` says on both axes, and reads the one mip level the
    /// toolkit's textures have.
    ///
    /// @throws SdlException when SDL refuses
    public SdlGpuSampler createSampler(SdlGpuFilter filter, SdlGpuAddressMode addressMode) {
        return createSampler(SdlGpuSamplerDescription.of(filter, addressMode));
    }

    /// Creates the sampler `description` describes. One with a mip filter reads
    /// every level the texture has; one without reads level 0 alone.
    ///
    /// @throws SdlException when SDL refuses
    public SdlGpuSampler createSampler(SdlGpuSamplerDescription description) {
        var info = Layouts.SDL_GPU_SAMPLER_CREATE_INFO;
        try (var arena = Arena.ofConfined()) {
            var createInfo = arena.allocate(info.layout());
            createInfo.set(
                    JAVA_INT, info.offsetOf("min_filter"), description.filter().value());
            createInfo.set(
                    JAVA_INT, info.offsetOf("mag_filter"), description.filter().value());
            var mip = description.mipFilter();
            createInfo.set(
                    JAVA_INT,
                    info.offsetOf("mipmap_mode"),
                    mip.isPresent() && mip.get() == SdlGpuFilter.LINEAR
                            ? SdlGpuPipelineCalls.SAMPLERMIPMAPMODE_LINEAR
                            : SdlGpuPipelineCalls.SAMPLERMIPMAPMODE_NEAREST);
            // The level-of-detail clamp: level 0 alone without a mip filter, as
            // the toolkit's own textures have one level, and every level with.
            createInfo.set(JAVA_FLOAT, info.offsetOf("min_lod"), 0f);
            createInfo.set(JAVA_FLOAT, info.offsetOf("max_lod"), mip.isPresent() ? MAX_LOD : 0f);
            for (var axis : new String[] {"address_mode_u", "address_mode_v", "address_mode_w"}) {
                createInfo.set(
                        JAVA_INT, info.offsetOf(axis), description.addressMode().value());
            }
            createInfo.set(JAVA_BOOLEAN, info.offsetOf("enable_anisotropy"), description.isAnisotropic());
            createInfo.set(JAVA_FLOAT, info.offsetOf("max_anisotropy"), description.maxAnisotropy());
            var compare = description.compare();
            createInfo.set(JAVA_BOOLEAN, info.offsetOf("enable_compare"), compare.isPresent());
            if (compare.isPresent()) {
                createInfo.set(
                        JAVA_INT, info.offsetOf("compare_op"), compare.get().value());
            }
            var sampler = calls.pipelines().createGPUSampler().call(handle(), createInfo);
            if (MemorySegment.NULL.equals(sampler)) {
                throw new SdlException("SDL_CreateGPUSampler", Sdl.get().lastError());
            }
            return new SdlGpuSampler(this, sampler, description);
        }
    }

    /// Vulkan's `VK_LOD_CLAMP_NONE`: a level-of-detail clamp past every level
    /// a texture can have, which the other drivers take the same way.
    private static final float MAX_LOD = 1000f;

    /// Creates a pipeline that draws triangles from the vertex id with `vertex`
    /// and `fragment`, into one colour target of `targetFormat`, blending with
    /// `blend`. No culling, no depth, one sample: [SdlGpuPipelineDescription#quads].
    /// A pipeline with no colour target is described and made through
    /// [#createGraphicsPipeline(SdlGpuPipelineDescription)].
    ///
    /// @throws IllegalArgumentException when a shader is for the wrong stage or
    ///                                  another device
    /// @throws SdlException             when SDL refuses: shaders that do not
    ///                                  link, or a format that cannot be a target
    public SdlGpuGraphicsPipeline createGraphicsPipeline(
            SdlGpuShader vertex, SdlGpuShader fragment, SdlGpuTextureFormat targetFormat, SdlGpuBlend blend) {
        return createGraphicsPipeline(SdlGpuPipelineDescription.quads(vertex, fragment, targetFormat, blend));
    }

    /// Creates the pipeline `description` describes.
    ///
    /// @throws IllegalArgumentException when its shaders are another device's
    /// @throws SdlException             when SDL refuses: shaders that do not
    ///                                  link, vertex inputs the vertex shader does
    ///                                  not declare, or a format that cannot be a
    ///                                  target
    public SdlGpuGraphicsPipeline createGraphicsPipeline(SdlGpuPipelineDescription description) {
        if (description.vertex().device() != this) {
            throw new IllegalArgumentException("a pipeline's shaders must be " + this + "'s");
        }
        var info = Layouts.SDL_GPU_GRAPHICS_PIPELINE_CREATE_INFO;
        var target = info.offsetOf("target_info");
        var targetLayout = Layouts.SDL_GPU_GRAPHICS_PIPELINE_TARGET_INFO;
        var rasterizer = info.offsetOf("rasterizer_state");
        var rasterizerLayout = Layouts.SDL_GPU_RASTERIZER_STATE;
        try (var arena = Arena.ofConfined()) {
            var createInfo = arena.allocate(info.layout());
            createInfo.set(
                    ADDRESS,
                    info.offsetOf("vertex_shader"),
                    description.vertex().handle());
            createInfo.set(
                    ADDRESS,
                    info.offsetOf("fragment_shader"),
                    description.fragment().handle());
            writeVertexInput(arena, createInfo, info.offsetOf("vertex_input_state"), description);
            createInfo.set(
                    JAVA_INT,
                    info.offsetOf("primitive_type"),
                    description.primitiveType().value());
            createInfo.set(
                    JAVA_INT, rasterizer + rasterizerLayout.offsetOf("fill_mode"), SdlGpuPipelineCalls.FILLMODE_FILL);
            createInfo.set(
                    JAVA_INT,
                    rasterizer + rasterizerLayout.offsetOf("cull_mode"),
                    description.cullMode().value());
            createInfo.set(
                    JAVA_INT,
                    rasterizer + rasterizerLayout.offsetOf("front_face"),
                    description.frontFace().value());
            var multisample = info.offsetOf("multisample_state");
            createInfo.set(
                    JAVA_INT,
                    multisample + Layouts.SDL_GPU_MULTISAMPLE_STATE.offsetOf("sample_count"),
                    description.sampleCount().value());
            if (description.depth().isPresent()) {
                var depth = description.depth().get();
                var state = info.offsetOf("depth_stencil_state");
                var stateLayout = Layouts.SDL_GPU_DEPTH_STENCIL_STATE;
                createInfo.set(
                        JAVA_INT,
                        state + stateLayout.offsetOf("compare_op"),
                        depth.compare().value());
                createInfo.set(JAVA_BOOLEAN, state + stateLayout.offsetOf("enable_depth_test"), true);
                createInfo.set(JAVA_BOOLEAN, state + stateLayout.offsetOf("enable_depth_write"), depth.write());
                createInfo.set(
                        JAVA_INT,
                        target + targetLayout.offsetOf("depth_stencil_format"),
                        depth.format().value());
                createInfo.set(JAVA_BOOLEAN, target + targetLayout.offsetOf("has_depth_stencil_target"), true);
            }
            if (description.targetFormat().isPresent()) {
                createInfo.set(
                        ADDRESS,
                        target + targetLayout.offsetOf("color_target_descriptions"),
                        colorTarget(arena, description.targetFormat().get(), description.blend()));
                createInfo.set(JAVA_INT, target + targetLayout.offsetOf("num_color_targets"), 1);
            }
            var pipeline = calls.pipelines().createGPUGraphicsPipeline().call(handle(), createInfo);
            if (MemorySegment.NULL.equals(pipeline)) {
                throw new SdlException(
                        "SDL_CreateGPUGraphicsPipeline", Sdl.get().lastError());
            }
            return new SdlGpuGraphicsPipeline(this, pipeline, description);
        }
    }

    /// The one `SDL_GPUColorTargetDescription` a pipeline has.
    private static MemorySegment colorTarget(Arena arena, SdlGpuTextureFormat format, SdlGpuBlend blend) {
        var description = Layouts.SDL_GPU_COLOR_TARGET_DESCRIPTION;
        var blendState = description.offsetOf("blend_state");
        var blendLayout = Layouts.SDL_GPU_COLOR_TARGET_BLEND_STATE;
        var colorTarget = arena.allocate(description.layout());
        colorTarget.set(JAVA_INT, description.offsetOf("format"), format.value());
        switch (blend) {
            case REPLACE -> {}
            case PREMULTIPLIED_OVER ->
                blendState(
                        colorTarget,
                        blendState,
                        SdlGpuPipelineCalls.BLENDFACTOR_ONE,
                        SdlGpuPipelineCalls.BLENDFACTOR_ONE_MINUS_SRC_ALPHA);
            case ADDITIVE ->
                blendState(
                        colorTarget,
                        blendState,
                        SdlGpuPipelineCalls.BLENDFACTOR_ONE,
                        SdlGpuPipelineCalls.BLENDFACTOR_ONE);
        }
        return colorTarget;
    }

    /// Enables blending with `source` and `destination` factors on colour and
    /// alpha alike, adding the two.
    private static void blendState(MemorySegment colorTarget, long blendState, int source, int destination) {
        var blendLayout = Layouts.SDL_GPU_COLOR_TARGET_BLEND_STATE;
        colorTarget.set(JAVA_BOOLEAN, blendState + blendLayout.offsetOf("enable_blend"), true);
        for (var factor : new String[] {"src_color_blendfactor", "src_alpha_blendfactor"}) {
            colorTarget.set(JAVA_INT, blendState + blendLayout.offsetOf(factor), source);
        }
        for (var factor : new String[] {"dst_color_blendfactor", "dst_alpha_blendfactor"}) {
            colorTarget.set(JAVA_INT, blendState + blendLayout.offsetOf(factor), destination);
        }
        for (var op : new String[] {"color_blend_op", "alpha_blend_op"}) {
            colorTarget.set(JAVA_INT, blendState + blendLayout.offsetOf(op), SdlGpuPipelineCalls.BLENDOP_ADD);
        }
    }

    /// Fills the `SDL_GPUVertexInputState` at `offset` of `createInfo`, with its
    /// two arrays in `arena`. Left zero, which SDL reads as no vertex input,
    /// when the description has no buffers.
    private static void writeVertexInput(
            Arena arena, MemorySegment createInfo, long offset, SdlGpuPipelineDescription description) {
        var buffers = description.vertexBuffers();
        var attributes = description.vertexAttributes();
        if (buffers.isEmpty()) {
            return;
        }
        var state = Layouts.SDL_GPU_VERTEX_INPUT_STATE;
        var bufferLayout = Layouts.SDL_GPU_VERTEX_BUFFER_DESCRIPTION;
        var bufferArray = arena.allocate(bufferLayout.layout(), buffers.size());
        for (var i = 0; i < buffers.size(); i++) {
            var buffer = buffers.get(i);
            var at = i * bufferLayout.byteSize();
            bufferArray.set(JAVA_INT, at + bufferLayout.offsetOf("slot"), buffer.slot());
            bufferArray.set(JAVA_INT, at + bufferLayout.offsetOf("pitch"), buffer.pitch());
            bufferArray.set(
                    JAVA_INT,
                    at + bufferLayout.offsetOf("input_rate"),
                    buffer.rate().value());
        }
        var attributeLayout = Layouts.SDL_GPU_VERTEX_ATTRIBUTE;
        var attributeArray =
                attributes.isEmpty() ? MemorySegment.NULL : arena.allocate(attributeLayout.layout(), attributes.size());
        for (var i = 0; i < attributes.size(); i++) {
            var attribute = attributes.get(i);
            var at = i * attributeLayout.byteSize();
            attributeArray.set(JAVA_INT, at + attributeLayout.offsetOf("location"), attribute.location());
            attributeArray.set(JAVA_INT, at + attributeLayout.offsetOf("buffer_slot"), attribute.bufferSlot());
            attributeArray.set(
                    JAVA_INT,
                    at + attributeLayout.offsetOf("format"),
                    attribute.format().value());
            attributeArray.set(JAVA_INT, at + attributeLayout.offsetOf("offset"), attribute.offset());
        }
        createInfo.set(ADDRESS, offset + state.offsetOf("vertex_buffer_descriptions"), bufferArray);
        createInfo.set(JAVA_INT, offset + state.offsetOf("num_vertex_buffers"), buffers.size());
        createInfo.set(ADDRESS, offset + state.offsetOf("vertex_attributes"), attributeArray);
        createInfo.set(JAVA_INT, offset + state.offsetOf("num_vertex_attributes"), attributes.size());
    }

    /// Claims `window` for this device: from now on it presents through a
    /// swapchain, in [SdlGpuPresentMode#VSYNC], and has no window surface.
    ///
    /// Call it on the window's thread. A window surface the caller holds must be
    /// given up first (`SdlVideo.invalidateSurface`), because the two cannot
    /// both present the window.
    ///
    /// SDL counts a second claim by the same device and gives the window back only
    /// when every claim is released, so two [SdlGpuWindow]s could hold one window
    /// and the first to close would release nothing. One claim per window is
    /// allowed here, and a second is refused.
    ///
    /// @throws IllegalStateException when this device already holds the window
    /// @throws SdlException          when SDL refuses: another device holds it, the
    ///                               window is transparent, or the video driver
    ///                               cannot make a Metal view or Vulkan surface
    public SdlGpuWindow claimWindow(SdlWindowHandle window) {
        for (var resource : resources) {
            if (resource instanceof SdlGpuWindow claimed && claimed.window() == window) {
                throw new IllegalStateException(window + " is already claimed by " + this);
            }
        }
        var pointer = WindowPointers.of(window);
        if (!calls.swapchain().claimWindowForGPUDevice().call(handle(), pointer)) {
            throw new SdlException("SDL_ClaimWindowForGPUDevice", Sdl.get().lastError());
        }
        return new SdlGpuWindow(this, pointer, window);
    }

    /// How many frames the CPU may record ahead of the GPU, from 1 to 3. SDL's
    /// default is 2, and the composited window keeps it.
    ///
    /// @throws SdlException when SDL refuses
    public void setAllowedFramesInFlight(int frames) {
        if (frames < 1 || frames > 3) {
            throw new IllegalArgumentException("frames in flight " + frames + ", not 1 to 3");
        }
        if (!calls.swapchain().setGPUAllowedFramesInFlight().call(handle(), frames)) {
            throw new SdlException("SDL_SetGPUAllowedFramesInFlight", Sdl.get().lastError());
        }
    }

    /// Whether [#close] has run.
    public boolean isClosed() {
        return closed;
    }

    /// How many resources made on this device are still open.
    public int openResources() {
        return resources.size();
    }

    /// Releases every resource still open, then destroys the device. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        for (var resource : List.copyOf(resources)) {
            resource.releaseOnce();
        }
        resources.clear();
        closed = true;
        calls.device().destroyGPUDevice().call(handle);
    }

    @Override
    public String toString() {
        return "SdlGpuDevice[" + driver + (closed ? ", closed]" : "]");
    }

    GpuCalls calls() {
        return calls;
    }

    /// The SDL handle.
    ///
    /// @throws IllegalStateException once closed
    MemorySegment handle() {
        if (closed) {
            throw new IllegalStateException(this + " is closed");
        }
        return handle;
    }

    void adopt(SdlGpuResource resource) {
        handle();
        resources.add(resource);
    }

    void disown(SdlGpuResource resource) {
        resources.remove(resource);
    }

    @SuppressWarnings("restricted")
    private static String readName(MemorySegment pointer) {
        if (MemorySegment.NULL.equals(pointer)) {
            return "";
        }
        return pointer.reinterpret(MAX_NAME_LENGTH).getString(0);
    }
}
