package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;

/// A 2D texture on a [SdlGpuDevice]: one mip level, one layer, one sample.
public final class SdlGpuTexture extends SdlGpuResource implements SdlGpuTarget {

    private final SdlGpuTextureFormat format;
    private final int width;
    private final int height;
    private final Set<SdlGpuTextureUsage> usages;

    SdlGpuTexture(
            SdlGpuDevice device,
            MemorySegment handle,
            SdlGpuTextureFormat format,
            int width,
            int height,
            Set<SdlGpuTextureUsage> usages) {
        super(device, handle);
        this.format = format;
        this.width = width;
        this.height = height;
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
        return "SdlGpuTexture[" + format + " " + width + "x" + height + "]";
    }
}
