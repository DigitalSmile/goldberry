package io.github.digitalsmile.goldberry.gpu;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// What a [GpuTexture] is made as: a 2D texture with one mip level, one layer
/// and one sample.
///
/// @param format the pixel format
/// @param width  in pixels, at least one
/// @param height in pixels, at least one
/// @param usages what it may be used for; at least one
public record TextureSpec(TextureFormat format, int width, int height, Set<TextureUsage> usages) {

    /// Checks the size and that the usages suit the format, and copies them.
    ///
    /// @throws IllegalArgumentException when the size is empty, there is no
    ///                                  usage, a depth format is asked to be
    ///                                  anything but a depth target, or a colour
    ///                                  format to be one
    public TextureSpec {
        Objects.requireNonNull(format, "format");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture " + width + "x" + height);
        }
        if (usages.isEmpty()) {
            throw new IllegalArgumentException("a texture needs at least one usage");
        }
        usages = Collections.unmodifiableSet(EnumSet.copyOf(usages));
        if (format.isDepth() && !usages.equals(EnumSet.of(TextureUsage.DEPTH_TARGET))) {
            throw new IllegalArgumentException(format + " can only be a depth target, not " + usages);
        }
        if (!format.isDepth() && usages.contains(TextureUsage.DEPTH_TARGET)) {
            throw new IllegalArgumentException(format + " cannot be a depth target");
        }
    }

    /// A texture shaders sample: an image, a video plane, the UI.
    public static TextureSpec sampled(TextureFormat format, int width, int height) {
        return new TextureSpec(format, width, height, EnumSet.of(TextureUsage.SAMPLER));
    }

    /// A texture rendered into and then sampled or read back: a layer's
    /// offscreen result.
    public static TextureSpec renderTarget(TextureFormat format, int width, int height) {
        return new TextureSpec(format, width, height, EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER));
    }

    /// A depth target of `format`, which must be a depth format.
    public static TextureSpec depth(TextureFormat format, int width, int height) {
        return new TextureSpec(format, width, height, EnumSet.of(TextureUsage.DEPTH_TARGET));
    }

    /// The size, in physical pixels.
    public PhysicalSize size() {
        return new PhysicalSize(width, height);
    }
}
