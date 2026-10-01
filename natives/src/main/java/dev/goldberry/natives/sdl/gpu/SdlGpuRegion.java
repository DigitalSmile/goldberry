package dev.goldberry.natives.sdl.gpu;

/// A rectangle of a texture's first mip level and layer, in pixels.
///
/// @param x      the left edge
/// @param y      the top edge
/// @param width  in pixels, at least one
/// @param height in pixels, at least one
public record SdlGpuRegion(int x, int y, int width, int height) {

    /// Checks the rectangle is not empty and does not start before the origin.
    public SdlGpuRegion {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("region " + x + "," + y + " " + width + "x" + height);
        }
    }

    /// The whole of a texture.
    public static SdlGpuRegion of(SdlGpuTexture texture) {
        return new SdlGpuRegion(0, 0, texture.width(), texture.height());
    }

    /// Whether the region lies inside a `width` by `height` texture.
    public boolean fitsIn(int textureWidth, int textureHeight) {
        return x + (long) width <= textureWidth && y + (long) height <= textureHeight;
    }
}
