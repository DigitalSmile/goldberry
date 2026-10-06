package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuSampleCount;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;

/// A 2D texture on a [SdlGpuDevice]: one or more layers, one or more mip
/// levels, and one or more samples per texel.
///
/// A texture with more than one layer is SDL's `2D_ARRAY` type. One with more
/// than one sample is a render target only. Level `n` is half the size of level
/// `n - 1`, rounded down and never below one texel.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuTexture extends SdlGpuResource implements SdlGpuTarget {

    private final SdlGpuTextureFormat format;
    private final int width;
    private final int height;
    private final int layers;
    private final int mipLevels;
    private final SdlGpuSampleCount sampleCount;
    private final Set<SdlGpuTextureUsage> usages;

    SdlGpuTexture(
            SdlGpuDevice device,
            MemorySegment handle,
            SdlGpuTextureFormat format,
            int width,
            int height,
            int layers,
            int mipLevels,
            SdlGpuSampleCount sampleCount,
            Set<SdlGpuTextureUsage> usages) {
        super(device, handle);
        this.format = format;
        this.width = width;
        this.height = height;
        this.layers = layers;
        this.mipLevels = mipLevels;
        this.sampleCount = sampleCount;
        this.usages = Collections.unmodifiableSet(EnumSet.copyOf(usages));
    }

    /// The pixel format.
    public SdlGpuTextureFormat format() {
        return format;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    /// How many layers it has: one, or more for an array.
    public int layers() {
        return layers;
    }

    /// How many mip levels it has.
    public int mipLevels() {
        return mipLevels;
    }

    /// How many samples a texel has.
    public SdlGpuSampleCount sampleCount() {
        return sampleCount;
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

    /// Checks `level` and `layer` name a sub-resource of this texture.
    ///
    /// @throws IllegalArgumentException when either is out of range
    public void requireSubresource(int level, int layer) {
        requireLevel(level);
        if (layer < 0 || layer >= layers) {
            throw new IllegalArgumentException(this + " has no layer " + layer);
        }
    }

    private int requireLevel(int level) {
        if (level < 0 || level >= mipLevels) {
            throw new IllegalArgumentException(this + " has no mip level " + level);
        }
        return level;
    }

    /// What the texture may be used for.
    public Set<SdlGpuTextureUsage> usages() {
        return usages;
    }

    /// How many bytes `region` of this texture takes, packed row after row.
    public long byteSize(SdlGpuRegion region) {
        return (long) region.width() * region.height() * format.bytesPerPixel();
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().resources().releaseGPUTexture().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuTexture[" + format + " " + width + "x" + height
                + (layers > 1 ? " x" + layers + " layers" : "")
                + (mipLevels > 1 ? ", " + mipLevels + " levels" : "")
                + (sampleCount != SdlGpuSampleCount.ONE ? ", " + sampleCount.samples() + " samples" : "")
                + "]";
    }
}
