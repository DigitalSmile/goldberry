package dev.goldberry.gpu;

import java.util.Set;

import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.render.model.PhysicalRect;

/// A texture on a [GpuDevice], made to a [TextureSpec]: sampled by shaders,
/// rendered into, uploaded to, read back, or tested as depth, as its usages
/// allow. As a [RenderTarget] it is its level 0 of layer 0; [#view], [#level]
/// and [#layer] name the others, [#face] a cube's faces and [#slice] a
/// volume's slices.
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

    /// What shape it is.
    public TextureType type() {
        return spec.type();
    }

    /// How many slices deep level 0 is: one for every type but a volume.
    public int depth() {
        return spec.depth();
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

    /// Face `face` of mip level `level` of a cube: what a render pass draws a
    /// face into and a [CopyPass] uploads a face to.
    ///
    /// @throws IllegalArgumentException when the texture is not a cube, or
    ///                                  has no such level
    public TextureView face(CubeFace face, int level) {
        if (spec.type() != TextureType.CUBE) {
            throw new IllegalArgumentException(this + " is not a cube, and has no faces");
        }
        return new TextureView(this, level, face.layer());
    }

    /// Depth slice `z` of mip level `level` of a volume: what a [CopyPass]
    /// uploads a slice to.
    ///
    /// @throws IllegalArgumentException when the texture is not a volume, or
    ///                                  the level or slice does not exist
    public TextureView slice(int level, int z) {
        if (spec.type() != TextureType.THREE_D) {
            throw new IllegalArgumentException(this + " is not a volume, and has no slices");
        }
        return new TextureView(this, level, z);
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
                + switch (spec.type()) {
                    case TWO_D -> "";
                    case TWO_D_ARRAY -> " x" + spec.layers() + " layers";
                    case CUBE -> " cube";
                    case THREE_D -> "x" + spec.depth() + " volume";
                }
                + (spec.mipLevels() > 1 ? ", " + spec.mipLevels() + " levels" : "")
                + (spec.samples() > 1 ? ", " + spec.samples() + " samples" : "")
                + (isClosed() ? ", closed]" : "]");
    }
}
