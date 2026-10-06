package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.SdlGpuShader;

/// A compiled shader on a [GpuDevice], from [ShaderCode] in the format the
/// device takes. A pipeline made from it keeps working after it is closed.
public final class Shader extends GpuResource {

    private final SdlGpuShader sdl;
    private final ShaderStage stage;
    private final ShaderFormat format;
    private final int samplers;
    private final int uniformBuffers;
    private final int storageTextures;
    private final int storageBuffers;

    Shader(GpuDevice device, SdlGpuShader sdl, ShaderCode code, ShaderFormat format) {
        super(device, sdl);
        this.sdl = sdl;
        this.stage = code.stage();
        this.format = format;
        this.samplers = code.samplers();
        this.uniformBuffers = code.uniformBuffers();
        this.storageTextures = code.storageTextures();
        this.storageBuffers = code.storageBuffers();
    }

    /// The stage it runs in.
    public ShaderStage stage() {
        return stage;
    }

    /// The format it was made from: the one of its code the device takes.
    public ShaderFormat format() {
        return format;
    }

    /// How many textures it samples.
    public int samplers() {
        return samplers;
    }

    /// How many uniform blocks it reads.
    public int uniformBuffers() {
        return uniformBuffers;
    }

    /// How many storage textures it reads.
    public int storageTextures() {
        return storageTextures;
    }

    /// How many storage buffers it reads.
    public int storageBuffers() {
        return storageBuffers;
    }

    /// The SDL shader, for `user`'s pipelines.
    SdlGpuShader sdl(GpuDevice user) {
        requireUsableBy(user);
        return sdl;
    }

    @Override
    public String toString() {
        return "Shader[" + stage + ", " + format + (isClosed() ? ", closed]" : "]");
    }
}
