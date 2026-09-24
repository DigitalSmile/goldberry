package io.github.digitalsmile.goldberry.gpu;

import java.util.Set;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTexture;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;

/// A 2D texture on a [GpuDevice], made to a [TextureSpec]: sampled by shaders,
/// rendered into, uploaded to, read back, or tested as depth, as its usages
/// allow.
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

    /// What it may be used for.
    public Set<TextureUsage> usages() {
        return spec.usages();
    }

    /// Whether `region` lies inside it.
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
                + (isClosed() ? ", closed]" : "]");
    }
}
