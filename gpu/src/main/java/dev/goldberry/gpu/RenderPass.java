package dev.goldberry.gpu;

import java.nio.ByteBuffer;
import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.render.model.PhysicalRect;

/// A render pass of a [GpuFrame]: draws into one [RenderTarget], usable only
/// inside the body [GpuFrame#renderPass] runs it in.
///
/// State is set, then drawn with: [#bindPipeline] first, then the pipeline's
/// samplers and vertex buffers (bound again after every pipeline change), the
/// index buffer for indexed draws, and uniforms, which last until pushed again.
/// A draw with anything missing throws before it reaches the GPU.
///
/// The viewport starts as the whole target, and the scissor as none.
/// Coordinates are pixels from the target's top left, as the toolkit measures
/// everything; clip space is SDL's, y up.
public final class RenderPass {

    private final GpuDevice device;
    private final SdlGpuCommandBuffer.RenderPass sdl;
    private final RenderTarget target;
    private boolean ended;

    RenderPass(GpuDevice device, SdlGpuCommandBuffer.RenderPass sdl, RenderTarget target) {
        this.device = device;
        this.sdl = sdl;
        this.target = target;
    }

    /// What it draws into.
    public RenderTarget target() {
        return target;
    }

    /// Binds the pipeline the next draws use.
    ///
    /// @throws IllegalArgumentException when it draws another format than the
    ///                                  target, tests depth this pass has no
    ///                                  target for (or the reverse), or is
    ///                                  another device's or closed
    public void bindPipeline(GraphicsPipeline pipeline) {
        requireOpen();
        sdl.bindPipeline(pipeline.sdl(device));
    }

    /// Maps clip space onto `x, y, width, height` of the target, in pixels.
    public void setViewport(float x, float y, float width, float height) {
        requireOpen();
        sdl.setViewport(x, y, width, height);
    }

    /// Draws nothing outside `region` of the target: a layer's clip.
    ///
    /// @throws IllegalArgumentException when it is empty or outside the target
    public void setScissor(PhysicalRect region) {
        requireOpen();
        if (region.isEmpty() || region.x() < 0 || region.y() < 0) {
            throw new IllegalArgumentException("scissor " + region);
        }
        sdl.setScissor(new SdlGpuRegion(region.x(), region.y(), region.width(), region.height()));
    }

    /// Binds `textures`, each read with `sampler`, to the bound pipeline's
    /// fragment sampler slots from 0: as many as its fragment shader samples.
    ///
    /// @throws IllegalStateException    when no pipeline is bound
    /// @throws IllegalArgumentException when the count is not the shader's, a
    ///                                  texture cannot be sampled, or anything
    ///                                  is another device's or closed
    public void bindFragmentSamplers(GpuSampler sampler, GpuTexture... textures) {
        requireOpen();
        var sdlSampler = sampler.sdl(device);
        var sdlTextures = new SdlGpuTexture[textures.length];
        for (var i = 0; i < textures.length; i++) {
            sdlTextures[i] = textures[i].sdl(device);
        }
        sdl.bindFragmentSamplers(sdlSampler, sdlTextures);
    }

    /// Binds `buffer` from its start to vertex slot `slot`.
    ///
    /// @throws IllegalStateException    when no pipeline is bound
    /// @throws IllegalArgumentException when the pipeline reads no such slot, or
    ///                                  the buffer is not a vertex buffer of this
    ///                                  device, or is closed
    public void bindVertexBuffer(int slot, GpuBuffer buffer) {
        bindVertexBuffer(slot, buffer, 0);
    }

    /// Binds `buffer` from byte `offset` to vertex slot `slot`.
    ///
    /// @throws IllegalStateException    when no pipeline is bound
    /// @throws IllegalArgumentException when the pipeline reads no such slot, the
    ///                                  offset is outside the buffer, or it is
    ///                                  not a vertex buffer of this device, or
    ///                                  is closed
    public void bindVertexBuffer(int slot, GpuBuffer buffer, int offset) {
        requireOpen();
        sdl.bindVertexBuffer(slot, buffer.sdl(device), offset);
    }

    /// Binds `buffer` from its start as the index buffer, of `format` indices.
    ///
    /// @throws IllegalArgumentException when it is not an index buffer of this
    ///                                  device, or is closed
    public void bindIndexBuffer(GpuBuffer buffer, IndexFormat format) {
        bindIndexBuffer(buffer, format, 0);
    }

    /// Binds `buffer` from byte `offset` as the index buffer, of `format`
    /// indices.
    ///
    /// @throws IllegalArgumentException when it is not an index buffer of this
    ///                                  device, is closed, or the offset is
    ///                                  outside it or not a whole index
    public void bindIndexBuffer(GpuBuffer buffer, IndexFormat format, int offset) {
        Objects.requireNonNull(format, "format");
        requireOpen();
        sdl.bindIndexBuffer(buffer.sdl(device), offset, format.sdl());
    }

    /// Sets the vertex shader's uniform block `slot` to `values`, for the draws
    /// that follow. Their layout is the shader's: HLSL packs a `cbuffer` in
    /// 16-byte rows, so a `float3` is followed by a float of padding.
    ///
    /// @throws IllegalArgumentException when the bound pipeline's vertex shader
    ///                                  reads no such block, or there are no
    ///                                  values
    public void pushVertexUniforms(int slot, float... values) {
        requireOpen();
        sdl.pushVertexUniforms(slot, values);
    }

    /// Sets the vertex shader's uniform block `slot` to the remaining bytes of
    /// `data`, which is not moved, for the draws that follow.
    ///
    /// @throws IllegalArgumentException as [#pushVertexUniforms(int, float...)]
    public void pushVertexUniforms(int slot, ByteBuffer data) {
        requireOpen();
        sdl.pushVertexUniforms(slot, data);
    }

    /// Sets the fragment shader's uniform block `slot` to `values`, for the
    /// draws that follow.
    ///
    /// @throws IllegalArgumentException when the bound pipeline's fragment
    ///                                  shader reads no such block, or there
    ///                                  are no values
    public void pushFragmentUniforms(int slot, float... values) {
        requireOpen();
        sdl.pushFragmentUniforms(slot, values);
    }

    /// Sets the fragment shader's uniform block `slot` to the remaining bytes
    /// of `data`, which is not moved, for the draws that follow.
    ///
    /// @throws IllegalArgumentException as [#pushFragmentUniforms(int, float...)]
    public void pushFragmentUniforms(int slot, ByteBuffer data) {
        requireOpen();
        sdl.pushFragmentUniforms(slot, data);
    }

    /// Draws `vertices` vertices, one instance.
    ///
    /// @throws IllegalStateException when no pipeline is bound, or its samplers
    ///                               or vertex buffers are not
    public void draw(int vertices) {
        draw(vertices, 1, 0, 0);
    }

    /// Draws `instances` instances of `vertices` vertices, from vertex
    /// `firstVertex` and instance `firstInstance`.
    ///
    /// @throws IllegalStateException when no pipeline is bound, or its samplers
    ///                               or vertex buffers are not
    public void draw(int vertices, int instances, int firstVertex, int firstInstance) {
        requireOpen();
        sdl.draw(vertices, instances, firstVertex, firstInstance);
    }

    /// Draws `indices` indices of the bound index buffer, one instance.
    ///
    /// @throws IllegalStateException when no pipeline or index buffer is bound,
    ///                               or the pipeline's samplers or vertex
    ///                               buffers are not
    public void drawIndexed(int indices) {
        drawIndexed(indices, 1, 0, 0, 0);
    }

    /// Draws `instances` instances of `indices` indices of the bound index
    /// buffer, from index `firstIndex`, `vertexOffset` added to each, from
    /// instance `firstInstance`.
    ///
    /// @throws IllegalStateException when no pipeline or index buffer is bound,
    ///                               or the pipeline's samplers or vertex
    ///                               buffers are not
    public void drawIndexed(int indices, int instances, int firstIndex, int vertexOffset, int firstInstance) {
        requireOpen();
        sdl.drawIndexed(indices, instances, firstIndex, vertexOffset, firstInstance);
    }

    /// The pass has ended with its body: every method throws from now on.
    void end() {
        ended = true;
    }

    private void requireOpen() {
        device.requireThread();
        if (ended) {
            throw new IllegalStateException("the render pass has ended: a pass is used only inside its body");
        }
    }
}
