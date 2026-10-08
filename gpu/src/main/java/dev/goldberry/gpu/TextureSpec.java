package dev.goldberry.gpu;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.render.model.PhysicalSize;

/// What a [GpuTexture] is made as: a texture of one [TextureType], `layers`
/// layers, `mipLevels` mip levels and `samples` samples per texel, each one by
/// default, and `depth` slices deep when it is a volume.
///
/// A [TextureType#TWO_D_ARRAY] texture is an array, which a shader samples as
/// one (`Texture2DArray` in HLSL) and whose layers are rendered into and
/// uploaded to one at a time through a [TextureView]. A [TextureType#CUBE] is
/// six square layers, its faces, sampled by direction ([#cube]). A
/// [TextureType#THREE_D] texture is a volume, sampled and uploaded to a slice
/// at a time ([#volume]). Mip level `n` is half the size of level `n - 1` on
/// every axis, rounded down and never below one texel; [#maxMipLevels] is the
/// full chain. A texture with more than one sample is a 2D render target alone,
/// with one layer and one level.
///
/// The factories and `with` methods choose the type: [#array] and
/// [#withLayers] of more than one make an array, [#cube] a cube and [#volume]
/// a volume, and every other spec is [TextureType#TWO_D].
///
/// @param type      what shape it is
/// @param format    the pixel format
/// @param width     in pixels, at least one
/// @param height    in pixels, at least one
/// @param depth     in slices, at least one, and more only for a volume
/// @param layers    how many layers, at least one: one for a 2D texture and a
///                  volume, and six for a cube
/// @param mipLevels how many mip levels, at least one and at most
///                  [#maxMipLevels]
/// @param samples   how many samples a texel has: 1, 2, 4 or 8
/// @param usages    what it may be used for; at least one
public record TextureSpec(
        TextureType type,
        TextureFormat format,
        int width,
        int height,
        int depth,
        int layers,
        int mipLevels,
        int samples,
        Set<TextureUsage> usages) {

    /// Checks the size and counts, and that the usages suit the type, the
    /// format and the sample count, and copies the usages.
    ///
    /// @throws IllegalArgumentException when the size is empty, a count is
    ///                                  below one or above what the size
    ///                                  allows, the sample count is not 1, 2,
    ///                                  4 or 8, there is no usage, a depth
    ///                                  format is asked to be anything but a
    ///                                  depth target (sampled or not), a colour
    ///                                  format to be a depth target, a
    ///                                  multisampled texture to be sampled,
    ///                                  layered, mipmapped or other than 2D, a
    ///                                  cube not to be six square layers or to
    ///                                  be other than sampled and rendered
    ///                                  into, a volume to be other than
    ///                                  sampled, or a block-compressed texture
    ///                                  to be other than sampled or other than
    ///                                  whole blocks
    public TextureSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(format, "format");
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("texture " + width + "x" + height + (depth != 1 ? "x" + depth : ""));
        }
        if (layers <= 0) {
            throw new IllegalArgumentException("texture of " + layers + " layers");
        }
        var maxLevels = maxMipLevels(width, height, depth);
        if (mipLevels <= 0 || mipLevels > maxLevels) {
            throw new IllegalArgumentException(mipLevels + " mip levels; a " + width + "x" + height
                    + (depth > 1 ? "x" + depth : "") + " texture has at most " + maxLevels);
        }
        if (samples != 1 && samples != 2 && samples != 4 && samples != 8) {
            throw new IllegalArgumentException(samples + " samples; a texel has 1, 2, 4 or 8");
        }
        if (usages.isEmpty()) {
            throw new IllegalArgumentException("a texture needs at least one usage");
        }
        usages = Collections.unmodifiableSet(EnumSet.copyOf(usages));
        if (depth > 1 && type != TextureType.THREE_D) {
            throw new IllegalArgumentException("only a volume has a depth, and a " + type + " texture has none");
        }
        switch (type) {
            case TWO_D -> {
                if (layers != 1) {
                    throw new IllegalArgumentException("a 2D texture has one layer; " + layers + " are an array");
                }
            }
            case TWO_D_ARRAY -> {}
            case CUBE -> {
                if (layers != 6 || width != height) {
                    throw new IllegalArgumentException(
                            "a cube is six square layers, not " + layers + " of " + width + "x" + height);
                }
                requireOnly(usages, EnumSet.of(TextureUsage.SAMPLER, TextureUsage.COLOR_TARGET), "a cube");
            }
            case THREE_D -> {
                if (layers != 1) {
                    throw new IllegalArgumentException("a volume has one layer and a depth, not " + layers + " layers");
                }
                requireOnly(usages, EnumSet.of(TextureUsage.SAMPLER), "a volume");
            }
        }
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
        if (format.isCompressed()) {
            requireOnly(usages, EnumSet.of(TextureUsage.SAMPLER), format + ", which is block-compressed,");
            var block = format.blockSize();
            if (width % block.width() != 0 || height % block.height() != 0) {
                throw new IllegalArgumentException(format + " is stored in " + block.width() + "x" + block.height()
                        + " blocks, and level 0 of " + width + "x" + height + " is not whole blocks");
            }
        }
        if (samples > 1) {
            if (type != TextureType.TWO_D || mipLevels > 1) {
                throw new IllegalArgumentException("a multisampled texture is 2D, with one layer and one mip level");
            }
            if (usages.contains(TextureUsage.SAMPLER)) {
                throw new IllegalArgumentException(
                        "a multisampled texture is a render target alone, and is resolved to be sampled");
            }
            if (format.isCompressed()) {
                throw new IllegalArgumentException(format + " is block-compressed, and never multisampled");
            }
        }
    }

    /// A 2D texture of `layers` layers, an array when there is more than one,
    /// of `mipLevels` mip levels and `samples` samples per texel.
    ///
    /// @throws IllegalArgumentException as the canonical constructor
    public TextureSpec(
            TextureFormat format,
            int width,
            int height,
            int layers,
            int mipLevels,
            int samples,
            Set<TextureUsage> usages) {
        this(
                layers > 1 ? TextureType.TWO_D_ARRAY : TextureType.TWO_D,
                format,
                width,
                height,
                1,
                layers,
                mipLevels,
                samples,
                usages);
    }

    /// A 2D texture of one layer, one mip level and one sample.
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

    /// An array of `layers` textures of one size, sampled as one: a
    /// [TextureType#TWO_D_ARRAY], even of one layer.
    public static TextureSpec array(TextureFormat format, int width, int height, int layers, Set<TextureUsage> usages) {
        return new TextureSpec(TextureType.TWO_D_ARRAY, format, width, height, 1, layers, 1, 1, usages);
    }

    /// A cube of six `size` by `size` faces, sampled by direction and rendered
    /// into a face at a time: a reflection probe. One mip level until
    /// [#withMipChain]; [GpuTexture#face] names a face of a level.
    ///
    /// @throws IllegalArgumentException when `format` is a depth or
    ///                                  block-compressed format, which is not
    ///                                  rendered into; [#cube(TextureFormat, int, Set)]
    ///                                  makes a compressed cube to be sampled
    public static TextureSpec cube(TextureFormat format, int size) {
        return cube(format, size, EnumSet.of(TextureUsage.SAMPLER, TextureUsage.COLOR_TARGET));
    }

    /// A cube of six `size` by `size` faces for `usages`: a sampler, and a
    /// colour target to be rendered into.
    ///
    /// @throws IllegalArgumentException when a usage is neither, or does not
    ///                                  suit `format`
    public static TextureSpec cube(TextureFormat format, int size, Set<TextureUsage> usages) {
        return new TextureSpec(TextureType.CUBE, format, size, size, 1, 6, 1, 1, usages);
    }

    /// A volume of `depth` slices of `width` by `height`, sampled with
    /// filtering across all three axes and uploaded a slice at a time through
    /// [GpuTexture#slice]: a colour-grading table. One mip level until
    /// [#withMipChain], which halves the depth too.
    ///
    /// @throws IllegalArgumentException when a size is not positive, or
    ///                                  `format` is a depth format
    public static TextureSpec volume(TextureFormat format, int width, int height, int depth) {
        return new TextureSpec(
                TextureType.THREE_D, format, width, height, depth, 1, 1, 1, EnumSet.of(TextureUsage.SAMPLER));
    }

    /// How many mip levels a `width` by `height` texture can have: one per
    /// halving down to one texel, counting the full-size level.
    public static int maxMipLevels(int width, int height) {
        return SdlGpuDevice.maxMipLevels(width, height);
    }

    /// How many mip levels a `width` by `height` by `depth` volume can have:
    /// one per halving of its longest axis down to one texel.
    public static int maxMipLevels(int width, int height, int depth) {
        return SdlGpuDevice.maxMipLevels(width, height, depth);
    }

    /// This spec with `count` mip levels.
    public TextureSpec withMipLevels(int count) {
        return new TextureSpec(type, format, width, height, depth, layers, count, samples, usages);
    }

    /// This spec with the full mip chain, down to one texel.
    public TextureSpec withMipChain() {
        return withMipLevels(maxMipLevels(width, height, depth));
    }

    /// This spec with `count` layers: a 2D texture of more than one becomes an
    /// array. A cube and a volume keep their type, and so refuse any count but
    /// their own.
    public TextureSpec withLayers(int count) {
        var layered = type == TextureType.TWO_D && count > 1 ? TextureType.TWO_D_ARRAY : type;
        return new TextureSpec(layered, format, width, height, depth, count, mipLevels, samples, usages);
    }

    /// This spec with `count` samples per texel.
    public TextureSpec withSamples(int count) {
        return new TextureSpec(type, format, width, height, depth, layers, mipLevels, count, usages);
    }

    /// This spec with `usages`.
    public TextureSpec withUsages(Set<TextureUsage> usages) {
        return new TextureSpec(type, format, width, height, depth, layers, mipLevels, samples, usages);
    }

    /// Whether it is an array: [TextureType#TWO_D_ARRAY].
    public boolean isArray() {
        return type == TextureType.TWO_D_ARRAY;
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

    /// How many slices deep mip level `level` is: one for every type but a
    /// volume.
    ///
    /// @throws IllegalArgumentException when there is no such level
    public int levelDepth(int level) {
        return Math.max(1, depth >> requireLevel(level));
    }

    private int requireLevel(int level) {
        if (level < 0 || level >= mipLevels) {
            throw new IllegalArgumentException(this + " has no mip level " + level);
        }
        return level;
    }

    private static void requireOnly(Set<TextureUsage> usages, Set<TextureUsage> allowed, String what) {
        if (!allowed.containsAll(usages)) {
            throw new IllegalArgumentException(what + " may be " + allowed + " alone, not " + usages);
        }
    }
}
