package io.github.digitalsmile.goldberry.gpu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.gpu.render.StagingBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuPipelineDescription;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShaderCode;

/// The GPU: Metal on macOS, Direct3D 12 on Windows, Vulkan elsewhere. What
/// textures, buffers, samplers, shaders and pipelines are made on, and what
/// frames of commands are recorded for.
///
/// **Not made by an application.** There is one per process, owned by the
/// backend and created the first time a GPU layer needs it, never at start-up
/// (`docs/gpu-plan.md`, D2); `canvas3d` hands its renderer the device, as a
/// composited window hands its layers. It is closed by its owner, and
/// everything made on it is released then, if it has not been already.
///
/// **One thread.** A device and everything made from it belong to the thread it
/// was handed out on, which in the toolkit is the UI thread. A call from any
/// other throws [WrongThreadException] before the driver is reached, as a
/// confined `Arena` does.
///
/// **Checked in Java.** A misuse the API can see -- a closed resource, another
/// device's, a draw with nothing bound, a region outside its texture -- throws
/// `IllegalArgumentException` or `IllegalStateException` at the call that made
/// it. What only the driver can refuse throws [GpuException].
public final class GpuDevice {

    private final SdlGpuDevice sdl;
    private final Thread owner;
    private final Set<ShaderFormat> shaderFormats;
    private @Nullable StagingBuffer upload;

    private GpuDevice(SdlGpuDevice sdl, Thread owner) {
        this.sdl = sdl;
        this.owner = owner;
        this.shaderFormats = Collections.unmodifiableSet(ShaderFormat.of(sdl.shaderFormats()));
    }

    /// The public face of `sdl`, confined to the calling thread. Its owner
    /// keeps closing `sdl`; this does not own it.
    static GpuDevice wrap(SdlGpuDevice sdl) {
        return new GpuDevice(Objects.requireNonNull(sdl, "sdl"), Thread.currentThread());
    }

    /// The driver it runs on: `metal`, `vulkan` or `direct3d12`.
    public String driver() {
        return sdl.driver();
    }

    /// The shader formats it takes. A [ShaderCode] needs one of them.
    public Set<ShaderFormat> shaderFormats() {
        return shaderFormats;
    }

    /// Whether its owner has closed it. Everything made on it is closed too.
    public boolean isClosed() {
        return sdl.isClosed();
    }

    /// Whether it can make a texture of `format` for `usages`. The formats in
    /// [TextureFormat] are supported everywhere for the usages [TextureSpec]
    /// allows; this is for the cases in between.
    public boolean supports(TextureFormat format, Set<TextureUsage> usages) {
        return sdl().supports(format.sdl(), TextureUsage.sdl(usages));
    }

    /// Makes a texture.
    ///
    /// @throws GpuException when the driver refuses: out of memory, or a size
    ///                      past its limit
    public GpuTexture createTexture(TextureSpec spec) {
        var device = sdl();
        var texture = call(() -> device.createTexture(
                spec.format().sdl(), spec.width(), spec.height(), TextureUsage.sdl(spec.usages())));
        return new GpuTexture(this, texture, spec);
    }

    /// Makes a buffer of `size` bytes for `usage`.
    ///
    /// @throws GpuException when the driver refuses
    public GpuBuffer createBuffer(BufferUsage usage, int size) {
        return createBuffer(EnumSet.of(usage), size);
    }

    /// Makes a buffer of `size` bytes for `usages`.
    ///
    /// @throws IllegalArgumentException when there is no usage, or the size is
    ///                                  not positive
    /// @throws GpuException             when the driver refuses
    public GpuBuffer createBuffer(Set<BufferUsage> usages, int size) {
        if (usages.isEmpty()) {
            throw new IllegalArgumentException("a buffer needs at least one usage");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("buffer of " + size + " bytes");
        }
        var device = sdl();
        var copy = Collections.unmodifiableSet(EnumSet.copyOf(usages));
        var buffer = call(() -> device.createBuffer(BufferUsage.sdl(copy), size));
        return new GpuBuffer(this, buffer, copy);
    }

    /// Makes a sampler.
    ///
    /// @throws GpuException when the driver refuses
    public GpuSampler createSampler(SamplerSpec spec) {
        var device = sdl();
        var sampler = call(() ->
                device.createSampler(spec.filter().sdl(), spec.addressMode().sdl()));
        return new GpuSampler(this, sampler, spec);
    }

    /// Makes a shader from `code`, in the one of its formats this device takes.
    ///
    /// @throws IllegalArgumentException when it carries none of them; the
    ///                                  message names what is missing
    /// @throws GpuException             when the driver refuses the code
    public Shader createShader(ShaderCode code) {
        var device = sdl();
        var format = code.formatFor(shaderFormats)
                .orElseThrow(() -> new IllegalArgumentException(
                        this + " takes " + shaderFormats + ", and the shader has only " + code.formats()));
        var bytecode = Objects.requireNonNull(code.bytecode().get(format));
        var sdlCode = new SdlGpuShaderCode(
                code.stage().sdl(),
                format.sdl(),
                bytecode.codeUnsafe(),
                bytecode.entryPoint(),
                code.samplers(),
                code.uniformBuffers());
        var shader = call(() -> device.createShader(sdlCode));
        return new Shader(this, shader, code, format);
    }

    /// Makes a graphics pipeline.
    ///
    /// @throws IllegalArgumentException when its shaders are another device's
    /// @throws IllegalStateException    when one of them is closed
    /// @throws GpuException             when the driver refuses: shaders that do
    ///                                  not link, or vertex inputs the vertex
    ///                                  shader does not declare
    public GraphicsPipeline createPipeline(PipelineSpec spec) {
        var device = sdl();
        var buffers = new ArrayList<SdlGpuPipelineDescription.VertexBuffer>();
        var attributes = new ArrayList<SdlGpuPipelineDescription.VertexAttribute>();
        for (var layout : spec.vertexBuffers()) {
            buffers.add(new SdlGpuPipelineDescription.VertexBuffer(
                    layout.slot(), layout.stride(), layout.rate().sdl()));
            for (var attribute : layout.attributes()) {
                attributes.add(new SdlGpuPipelineDescription.VertexAttribute(
                        attribute.location(), layout.slot(), attribute.format().sdl(), attribute.offset()));
            }
        }
        var description = new SdlGpuPipelineDescription(
                spec.vertexShader().sdl(this),
                spec.fragmentShader().sdl(this),
                spec.targetFormat().sdl(),
                spec.blend().sdl(),
                spec.primitiveType().sdl(),
                spec.cullMode().sdl(),
                spec.frontFace().sdl(),
                buffers,
                attributes,
                spec.depthTest()
                        .map(test -> new SdlGpuPipelineDescription.DepthTest(
                                test.format().sdl(), test.compare().sdl(), test.write())));
        var pipeline = call(() -> device.createGraphicsPipeline(description));
        return new GraphicsPipeline(this, pipeline, spec);
    }

    /// Begins a frame: one command buffer, recorded into and then submitted
    /// with [GpuFrame#submit] or discarded with [GpuFrame#close].
    ///
    /// @throws GpuException when the driver has no command buffer to give
    public GpuFrame beginFrame() {
        var device = sdl();
        return new GpuFrame(this, call(device::acquireCommandBuffer));
    }

    @Override
    public String toString() {
        return "GpuDevice[" + sdl.driver() + (sdl.isClosed() ? ", closed]" : "]");
    }

    /// Checks the caller is on the device's thread.
    ///
    /// @throws WrongThreadException when it is not
    void requireThread() {
        if (Thread.currentThread() != owner) {
            throw new WrongThreadException(this + " belongs to thread " + owner.getName() + ", and was used from "
                    + Thread.currentThread().getName());
        }
    }

    /// The SDL device, checked open and on its thread.
    SdlGpuDevice sdl() {
        requireThread();
        if (sdl.isClosed()) {
            throw new IllegalStateException(this + " is closed");
        }
        return sdl;
    }

    /// The staging memory uploads pass through, made the first time one does.
    StagingBuffer upload() {
        var current = upload;
        if (current == null) {
            current = new StagingBuffer(sdl());
            upload = current;
        }
        return current;
    }

    /// Runs `call`, turning the driver's refusal into a [GpuException].
    static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (SdlException e) {
            throw GpuException.of(e);
        }
    }

    /// Runs `call`, turning the driver's refusal into a [GpuException].
    static void run(Runnable call) {
        try {
            call.run();
        } catch (SdlException e) {
            throw GpuException.of(e);
        }
    }
}
