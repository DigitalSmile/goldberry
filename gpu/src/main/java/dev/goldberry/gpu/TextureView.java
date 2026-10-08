package dev.goldberry.gpu;

import java.util.Objects;

import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

/// One mip level of one layer of a [GpuTexture]: what a render pass draws
/// into, a [CopyPass] uploads to, and a [GpuFrame#readback] reads, when the
/// texture has more than one of either. A texture itself stands for its level
/// 0 of layer 0.
///
/// A cube's layers are its faces ([GpuTexture#face]). A volume has one layer
/// and many depth slices, and its view names a slice of a level in `layer`
/// ([GpuTexture#slice]); each level of a volume is half as many slices deep as
/// the one above it.
///
/// @param texture the texture
/// @param level   the mip level, from 0
/// @param layer   the layer, from 0: a cube's face, a volume's depth slice
public record TextureView(GpuTexture texture, int level, int layer) implements RenderTarget {

    /// Checks the level and layer, or the level and slice, exist.
    ///
    /// @throws IllegalArgumentException when either is out of range
    public TextureView {
        Objects.requireNonNull(texture, "texture");
        var spec = texture.spec();
        if (level < 0 || level >= spec.mipLevels()) {
            throw new IllegalArgumentException(texture + " has no mip level " + level);
        }
        if (spec.type() == TextureType.THREE_D) {
            if (layer < 0 || layer >= spec.levelDepth(level)) {
                throw new IllegalArgumentException(texture + " has no slice " + layer + " at mip level " + level
                        + ", which is " + spec.levelDepth(level) + " deep");
            }
        } else if (layer < 0 || layer >= spec.layers()) {
            throw new IllegalArgumentException(texture + " has no layer " + layer);
        }
    }

    @Override
    public TextureFormat format() {
        return texture.format();
    }

    /// The width of the level, in texels.
    @Override
    public int width() {
        return texture.spec().levelWidth(level);
    }

    /// The height of the level, in texels.
    @Override
    public int height() {
        return texture.spec().levelHeight(level);
    }

    /// The size of the level, in texels.
    public PhysicalSize size() {
        return new PhysicalSize(width(), height());
    }

    /// Whether `region` lies inside the level.
    public boolean contains(PhysicalRect region) {
        return region.x() >= 0
                && region.y() >= 0
                && (long) region.x() + region.width() <= width()
                && (long) region.y() + region.height() <= height();
    }

    @Override
    public String toString() {
        var which =
                switch (texture.type()) {
                    case CUBE -> "face " + CubeFace.ofLayer(layer);
                    case THREE_D -> "slice " + layer;
                    case TWO_D, TWO_D_ARRAY -> "layer " + layer;
                };
        return texture + "[level " + level + ", " + which + "]";
    }
}
