package dev.goldberry.gpu;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.render.model.PhysicalSize;

/// What a [GpuTexture] is made as: a 2D texture of `layers` layers, `mipLevels`
/// mip levels and `samples` samples per texel, each one by default.
///
/// A texture with more than one layer is an array, which a shader samples as
/// one (`Texture2DArray` in HLSL) and whose layers are rendered into and
/// uploaded to one at a time through a [TextureView]. Mip level `n` is half the
/// size of level `n - 1`, rounded down and never below one texel; [#maxMipLevels]
/// is the full chain. A texture with more than one sample is a render target
/// alone, with one layer and one level.
///
/// @param format    the pixel format
/// @param width     in pixels, at least one
/// @param height    in pixels, at least one
/// @param layers    how many layers, at least one
/// @param mipLevels how many mip levels, at least one and at most
///                  [#maxMipLevels]
/// @param samples   how many samples a texel has: 1, 2, 4 or 8
/// @param usages    what it may be used for; at least one
public record TextureSpec(
        TextureFormat format, int width, int height, int layers, int mipLevels, int samples, Set<TextureUsage> usages) {

    /// Checks the size and counts, and that the usages suit the format and the
    /// sample count, and copies the usages.
    ///
    /// @throws IllegalArgumentException when the size is empty, a count is
    ///                                  below one or above what the size
    ///                                  allows, the sample count is not 1, 2,
    ///                                  4 or 8, there is no usage, a depth
    ///                                  format is asked to be anything but a
    ///                                  depth target (sampled or not), a colour
    ///                                  format to be a depth target, or a
    ///                                  multisampled texture to be sampled,
    ///                                  layered or mipmapped
    public TextureSpec {
        Objects.requireNonNull(format, "format");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture " + width + "x" + height);
        }
        if (layers <= 0) {
            throw new IllegalArgumentException("texture of " + layers + " layers");
        }
        if (mipLevels <= 0 || mipLevels > maxMipLevels(width, height)) {
            throw new IllegalArgumentException(mipLevels + " mip levels; a " + width + "x" + height
                    + " texture has at most " + maxMipLevels(width, height));
        }
        if (samples != 1 && samples != 2 && samples != 4 && samples != 8) {
            throw new IllegalArgumentException(samples + " samples; a texel has 1, 2, 4 or 8");
        }
        if (usages.isEmpty()) {
            throw new IllegalArgumentException("a texture needs at least one usage");
        }
        usages = Collections.unmodifiableSet(EnumSet.copyOf(usages));
        if (format.isDepth()) {
            if (!usages.contains(TextureUsage.DEPTH_TARGET)
                    || !EnumSet.of(TextureUsage.DEPTH_TARGET, TextureUsage.SAMPLER)
                            .containsAll(usages)) {
                throw new IllegalArgumentException(
                        format + " can only be a depth target, sampled or not, not " + usages);
            }
        } else if (usages.contains(TextureUsage.DEPTH_TARGET)) {
            throw new IllegalArgumentException(format + " cannot be a depth target");
        }
        if (samples > 1) {
            if (layers > 1 || mipLevels > 1) {
                throw new IllegalArgumentException("a multisampled texture has one layer and one mip level");
            }
            if (usages.contains(TextureUsage.SAMPLER)) {
                throw new IllegalArgumentException(
                        "a multisampled texture is a render target alone, and is resolved to be sampled");
            }
        }
    }

    /// A texture of one layer, one mip level and one sample.
    public TextureSpec(TextureFormat format, int width, int height, Set<TextureUsage> usages) {
        this(format, width, height, 1, 1, 1, usages);
    }

    /// A texture shaders sample: an image, a video plane, the UI.
    public static TextureSpec sampled(TextureFormat format, int width, int height) {
        return new TextureSpec(format, width, height, EnumSet.of(TextureUsage.SAMPLER));
    }

    /// A texture rendered into and then sampled or read back: a layer's
    /// offscreen result, an HDR scene before its tonemap.
    public static TextureSpec renderTarget(TextureFormat format, int width, int height) {
        return new TextureSpec(format, width, height, EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER));
    }

    /// A depth target of `format`, which must be a depth format.
    public static TextureSpec depth(TextureFormat format, int width, int height) {
        return new TextureSpec(format, width, height, EnumSet.of(TextureUsage.DEPTH_TARGET));
    }

    /// A depth target that a later pass samples: a shadow map.
    public static TextureSpec sampledDepth(TextureFormat format, int width, int height) {
        return new TextureSpec(format, width, height, EnumSet.of(TextureUsage.DEPTH_TARGET, TextureUsage.SAMPLER));
    }

    /// An array of `layers` textures of one size, sampled as one.
    public static TextureSpec array(TextureFormat format, int width, int height, int layers, Set<TextureUsage> usages) {
        return new TextureSpec(format, width, height, layers, 1, 1, usages);
    }

    /// How many mip levels a `width` by `height` texture can have: one per
    /// halving down to one texel, counting the full-size level.
    public static int maxMipLevels(int width, int height) {
        return SdlGpuDevice.maxMipLevels(width, height);
    }

    /// This spec with `count` mip levels.
    public TextureSpec withMipLevels(int count) {
        return new TextureSpec(format, width, height, layers, count, samples, usages);
    }

    /// This spec with the full mip chain, down to one texel.
    public TextureSpec withMipChain() {
        return withMipLevels(maxMipLevels(width, height));
    }

    /// This spec with `count` layers.
    public TextureSpec withLayers(int count) {
        return new TextureSpec(format, width, height, count, mipLevels, samples, usages);
    }

    /// This spec with `count` samples per texel.
    public TextureSpec withSamples(int count) {
        return new TextureSpec(format, width, height, layers, mipLevels, count, usages);
    }

    /// This spec with `usages`.
    public TextureSpec withUsages(Set<TextureUsage> usages) {
        return new TextureSpec(format, width, height, layers, mipLevels, samples, usages);
    }

    /// Whether it has more than one layer.
    public boolean isArray() {
        return layers > 1;
    }

    /// The size, in physical pixels, of level 0.
    public PhysicalSize size() {
        return new PhysicalSize(width, height);
    }

    /// The width of mip level `level`, in texels.
    ///
    /// @throws IllegalArgumentException when there is no such level
    public int levelWidth(int level) {
        return Math.max(1, width >> requireLevel(level));
    }

    /// The height of mip level `level`, in texels.
    ///
    /// @throws IllegalArgumentException when there is no such level
    public int levelHeight(int level) {
        return Math.max(1, height >> requireLevel(level));
    }

    private int requireLevel(int level) {
        if (level < 0 || level >= mipLevels) {
            throw new IllegalArgumentException(this + " has no mip level " + level);
        }
        return level;
    }
}
