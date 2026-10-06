package dev.goldberry.gpu;

import java.util.Set;

import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.render.model.PhysicalRect;

/// A 2D texture on a [GpuDevice], made to a [TextureSpec]: sampled by shaders,
/// rendered into, uploaded to, read back, or tested as depth, as its usages
/// allow. As a [RenderTarget] it is its level 0 of layer 0; [#view], [#level]
/// and [#layer] name the others.
public final class GpuTexture extends GpuResource implements RenderTarget {

    private final SdlGpuTexture sdl;
    private final TextureSpec spec;

    GpuTexture(GpuDevice device, SdlGpuTexture sdl, TextureSpec spec) {
        super(device, sdl);
        this.sdl = sdl;
        this.spec = spec;
    }

    /// What it was made as.
    public TextureSpec spec() {
        return spec;
    }

    @Override
    public TextureFormat format() {
        return spec.format();
    }

    @Override
    public int width() {
        return spec.width();
    }

    @Override
    public int height() {
        return spec.height();
    }

    /// How many layers it has.
    public int layers() {
        return spec.layers();
    }

    /// How many mip levels it has.
    public int mipLevels() {
        return spec.mipLevels();
    }

    /// How many samples a texel has.
    public int samples() {
        return spec.samples();
    }

    /// What it may be used for.
    public Set<TextureUsage> usages() {
        return spec.usages();
    }

    /// Mip level `level` of layer `layer`.
    ///
    /// @throws IllegalArgumentException when either does not exist
    public TextureView view(int level, int layer) {
        return new TextureView(this, level, layer);
    }

    /// Mip level `level` of layer 0.
    ///
    /// @throws IllegalArgumentException when it does not exist
    public TextureView level(int level) {
        return new TextureView(this, level, 0);
    }

    /// Level 0 of layer `layer`.
    ///
    /// @throws IllegalArgumentException when it does not exist
    public TextureView layer(int layer) {
        return new TextureView(this, 0, layer);
    }

    /// Whether `region` lies inside level 0.
    public boolean contains(PhysicalRect region) {
        return region.x() >= 0
                && region.y() >= 0
                && (long) region.x() + region.width() <= spec.width()
                && (long) region.y() + region.height() <= spec.height();
    }

    /// The SDL texture, for `user`'s commands.
    SdlGpuTexture sdl(GpuDevice user) {
        requireUsableBy(user);
        return sdl;
    }

    @Override
    public String toString() {
        return "GpuTexture[" + spec.format() + " " + spec.width() + "x" + spec.height()
                + (spec.layers() > 1 ? " x" + spec.layers() + " layers" : "")
                + (spec.mipLevels() > 1 ? ", " + spec.mipLevels() + " levels" : "")
                + (spec.samples() > 1 ? ", " + spec.samples() + " samples" : "")
                + (isClosed() ? ", closed]" : "]");
    }
}
