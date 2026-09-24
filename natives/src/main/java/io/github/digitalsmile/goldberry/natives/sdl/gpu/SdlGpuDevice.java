package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import static java.lang.foreign.ValueLayout.JAVA_INT;

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

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuResourceCalls;

/// An SDL GPU device: Metal on macOS, Direct3D 12 on Windows, Vulkan elsewhere,
/// or whichever the options name.
///
/// The toolkit makes one per process, the first time something needs it, and
/// never at start-up (`docs/gpu-plan.md`, D2). Everything made from a device is
/// released when the device closes, if it has not been already.
///
/// Needs SDL's video subsystem, under a video driver that can make a Metal view
/// or a Vulkan surface; [#create] says which is missing when it cannot.
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

        /// Every format the toolkit ships shaders in (`docs/gpu-plan.md`, D7),
        /// no validation, the low-power GPU, and SDL's choice of driver.
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
            io.github.digitalsmile.goldberry.natives.sdl.calls.SdlPropertiesCalls properties,
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
        return calls.device()
                .textureSupportsFormat()
                .call(handle(), format.value(), SdlGpuResourceCalls.TEXTURETYPE_2D, SdlGpuTextureUsage.mask(usages));
    }

    /// Creates a 2D texture with one mip level and one sample.
    ///
    /// @throws SdlException when SDL refuses: a format the device cannot make
    ///                      for these usages, or a size beyond its limit
    public SdlGpuTexture createTexture(
            SdlGpuTextureFormat format, int width, int height, Set<SdlGpuTextureUsage> usages) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture " + width + "x" + height);
        }
        if (usages.isEmpty()) {
            throw new IllegalArgumentException("a texture needs at least one usage");
        }
        var info = Layouts.SDL_GPU_TEXTURE_CREATE_INFO;
        try (var arena = Arena.ofConfined()) {
            var createInfo = arena.allocate(info.layout());
            createInfo.set(JAVA_INT, info.offsetOf("type"), SdlGpuResourceCalls.TEXTURETYPE_2D);
            createInfo.set(JAVA_INT, info.offsetOf("format"), format.value());
            createInfo.set(JAVA_INT, info.offsetOf("usage"), SdlGpuTextureUsage.mask(usages));
            createInfo.set(JAVA_INT, info.offsetOf("width"), width);
            createInfo.set(JAVA_INT, info.offsetOf("height"), height);
            createInfo.set(JAVA_INT, info.offsetOf("layer_count_or_depth"), 1);
            createInfo.set(JAVA_INT, info.offsetOf("num_levels"), 1);
            createInfo.set(JAVA_INT, info.offsetOf("sample_count"), SdlGpuResourceCalls.SAMPLECOUNT_1);
            var texture = calls.resources().createGPUTexture().call(handle(), createInfo);
            if (MemorySegment.NULL.equals(texture)) {
                throw new SdlException("SDL_CreateGPUTexture", Sdl.get().lastError());
            }
            return new SdlGpuTexture(this, texture, format, width, height, usages);
        }
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
