package dev.goldberry.gpu.raster;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import dev.goldberry.gpu.CopyPass;
import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.composite.ApiAccess;
import dev.goldberry.gpu.render.mapped.InPlaceLayout;
import dev.goldberry.gpu.render.mapped.MappedRaster;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// A texture kept in step with a raster the application paints with the
/// toolkit's painter: a board's tile, a chart, a sheet of labels, drawn by a
/// `Canvas3dRenderer` like any other texture.
///
/// ```java
/// var tile = RasterTexture.create(device, new PhysicalSize(256, 256));    // in init
///
/// tile.paint(List.of(PhysicalRect.of(0, 0, 64, 64)), frame -> drawSquare(frame));
/// frame.copyPass(tile::upload);                                            // in render
/// frame.copyPass(copy -> copy.copy(tile.texture().view(0, 0), 0, 0, target.colour().view(0, 0), x, y, 256, 256));
/// ```
///
/// **What is painted is what goes up.** [#paint] records the regions it
/// painted, and [#upload] copies exactly those into the texture, then
/// forgets them. A new raster's damage is the whole of it, its pixels
/// transparent, so the first upload fills the texture.
///
/// **Painted where the GPU copies from, where that pays.** On a device whose
/// upload memory reads like ordinary memory (Vulkan's, as a rule), the raster
/// lives in that memory: the painter writes it, and [#upload] records a copy
/// out of it with nothing copied on the CPU. Elsewhere it lives in ordinary
/// memory and [#upload] stages its damage, as `CopyPass.upload` does.
/// [#inPlace] says which; the device's check at start-up decides, and
/// `goldberry.gpu.inPlace=on|off` overrides it. [#staged] always stages.
///
/// **A paint waits for the last upload's frame.** Painted in place, the
/// pixels the GPU is still copying must not change under it, so [#paint]
/// waits for the frame the last [#upload] was recorded in to have run. In
/// steady state it has long run by the next paint, and the wait costs nothing.
/// Before that frame is submitted the GPU has not begun, so a paint does not
/// wait, and the frame copies what was painted since. A frame discarded with
/// its upload in it loses that damage: paint it again.
///
/// **One thread**, the device's, like everything made on it. Close it once the
/// frame of its last upload is submitted or discarded.
///
/// Read more: [The renderer](https://goldberry.dev/docs/components/gpu.html#the-renderer).
public final class RasterTexture implements AutoCloseable {

    /// At more damaged regions than this, the damage becomes their bounds.
    private static final int MAX_REGIONS = 32;

    /// Where the pixels live.
    private sealed interface Memory {}

    /// In upload memory the GPU copies from.
    private record InPlace(MappedRaster raster) implements Memory {}

    /// In ordinary memory, staged by each upload.
    private record Staged(PixelBuffer pixels) implements Memory {}

    private final PhysicalSize size;
    private final GpuTexture texture;
    private final Memory memory;
    private final List<PhysicalRect> damage = new ArrayList<>();
    private boolean closed;

    private RasterTexture(PhysicalSize size, GpuTexture texture, Memory memory) {
        this.size = size;
        this.texture = texture;
        this.memory = memory;
        damage.add(PhysicalRect.of(size));
    }

    /// A raster and its texture of `size` on `device`, painted in place where
    /// the device's check said so, and staged otherwise, or where the driver
    /// refuses the memory.
    ///
    /// @throws IllegalArgumentException when `size` is empty
    /// @throws dev.goldberry.gpu.GpuException when the driver refuses the
    ///                                        texture
    public static RasterTexture create(GpuDevice device, PhysicalSize size) {
        Objects.requireNonNull(device, "device");
        requireSize(size);
        if (!ApiAccess.inPlace(device).on()) {
            return staged(device, size);
        }
        var texture = texture(device, size);
        try {
            var layout = InPlaceLayout.of(device.driver(), device.uploadsPaddedRows());
            var raster = MappedRaster.create(ApiAccess.sdl(device), size, layout);
            // Upload memory comes as the driver left it.
            MemorySegment.ofBuffer(raster.lend().pixels()).fill((byte) 0);
            return new RasterTexture(size, texture, new InPlace(raster));
        } catch (SdlException | IllegalArgumentException e) {
            return new RasterTexture(size, texture, new Staged(heap(size)));
        } catch (RuntimeException e) {
            texture.close();
            throw e;
        }
    }

    /// A raster and its texture of `size` on `device`, the raster in
    /// ordinary memory whatever the device: for pixels read back often, or to
    /// compare the two paths.
    ///
    /// @throws IllegalArgumentException when `size` is empty
    /// @throws dev.goldberry.gpu.GpuException when the driver refuses the
    ///                                        texture
    public static RasterTexture staged(GpuDevice device, PhysicalSize size) {
        Objects.requireNonNull(device, "device");
        requireSize(size);
        return new RasterTexture(size, texture(device, size), new Staged(heap(size)));
    }

    /// Its size in pixels: the raster's and the texture's.
    public PhysicalSize size() {
        return size;
    }

    /// The texture, [TextureFormat#B8G8R8A8_UNORM], premultiplied, sampled or
    /// copied from like any other. Closed with this.
    public GpuTexture texture() {
        return texture;
    }

    /// Whether the raster is painted where the GPU copies from.
    public boolean inPlace() {
        return memory instanceof InPlace;
    }

    /// Paints the whole raster with `painter`, at a scale of one.
    ///
    /// @throws IllegalStateException when it is closed
    public void paint(Consumer<Frame> painter) {
        paint(List.of(PhysicalRect.of(size)), new DisplayScale(1f), painter);
    }

    /// Paints `regions` of the raster with `painter`, at a scale of one: the
    /// painter runs once per region, clipped to it, and the regions are what
    /// the next [#upload] copies. Empty regions are skipped.
    ///
    /// @throws IllegalArgumentException when a region is outside the raster
    /// @throws IllegalStateException    when it is closed
    public void paint(List<PhysicalRect> regions, Consumer<Frame> painter) {
        paint(regions, new DisplayScale(1f), painter);
    }

    /// Paints `regions`, in pixels, with `painter`, drawing at `scale`: a
    /// logical unit is `scale` pixels, as in a window's frame.
    ///
    /// @throws IllegalArgumentException when a region is outside the raster
    /// @throws IllegalStateException    when it is closed
    public void paint(List<PhysicalRect> regions, DisplayScale scale, Consumer<Frame> painter) {
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(painter, "painter");
        requireOpen();
        var painted = new ArrayList<PhysicalRect>(regions.size());
        for (var region : regions) {
            if (!region.fitsWithin(size)) {
                throw new IllegalArgumentException(region + " is outside a " + size + " raster");
            }
            if (!region.isEmpty()) {
                painted.add(region);
            }
        }
        if (painted.isEmpty()) {
            return;
        }
        var pixels = switch (memory) {
            case InPlace(var raster) -> raster.lend();
            case Staged(var heap) -> heap;
        };
        // Before the painter runs: if it throws, the pixels may have changed.
        damaged(painted);
        var frame = Frame.over(pixels, scale);
        var factor = scale.factor();
        try {
            for (var region : painted) {
                frame.save();
                try {
                    frame.clipTo(
                            region.x() / factor,
                            region.y() / factor,
                            region.width() / factor,
                            region.height() / factor);
                    painter.accept(frame);
                } finally {
                    frame.restore();
                }
            }
        } finally {
            frame.end();
        }
    }

    /// What the next [#upload] copies: the regions painted since the last.
    public List<PhysicalRect> damage() {
        return List.copyOf(damage);
    }

    /// Copies the damage into the texture, recorded in `copy`, and forgets
    /// it. Nothing is recorded when nothing was painted.
    ///
    /// @throws IllegalArgumentException when `copy` is another device's
    /// @throws IllegalStateException    when it is closed, or the pass has
    ///                                  ended
    public void upload(CopyPass copy) {
        Objects.requireNonNull(copy, "copy");
        requireOpen();
        if (damage.isEmpty()) {
            return;
        }
        switch (memory) {
            case InPlace(var raster) -> {
                var into = ApiAccess.inPlaceCopy(copy, texture);
                raster.upload(into.pass(), into.destination(), regions(damage), into.finished());
            }
            case Staged(var pixels) -> copy.upload(texture, pixels, damage);
        }
        damage.clear();
    }

    /// Whether it is closed.
    public boolean isClosed() {
        return closed;
    }

    /// Waits for the last upload's frame where it is painted in place, then
    /// releases the raster and the texture. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (memory instanceof InPlace(var raster)) {
                raster.close();
            }
        } finally {
            texture.close();
        }
    }

    @Override
    public String toString() {
        return "RasterTexture[" + size + (inPlace() ? ", in place" : ", staged") + (closed ? ", closed]" : "]");
    }

    /// Adds `painted` to the damage: the whole when one covers it, and the
    /// bounds of all of it once there are too many to copy one by one.
    private void damaged(List<PhysicalRect> painted) {
        var whole = PhysicalRect.of(size);
        if (painted.contains(whole) || damage.contains(whole)) {
            damage.clear();
            damage.add(whole);
            return;
        }
        damage.addAll(painted);
        if (damage.size() > MAX_REGIONS) {
            var bounds = bounds(damage);
            damage.clear();
            damage.add(bounds);
        }
    }

    private static PhysicalRect bounds(List<PhysicalRect> rects) {
        var left = Integer.MAX_VALUE;
        var top = Integer.MAX_VALUE;
        var right = Integer.MIN_VALUE;
        var bottom = Integer.MIN_VALUE;
        for (var rect : rects) {
            left = Math.min(left, rect.x());
            top = Math.min(top, rect.y());
            right = Math.max(right, rect.right());
            bottom = Math.max(bottom, rect.bottom());
        }
        return PhysicalRect.of(left, top, right - left, bottom - top);
    }

    private static List<SdlGpuRegion> regions(List<PhysicalRect> rects) {
        var regions = new ArrayList<SdlGpuRegion>(rects.size());
        for (var rect : rects) {
            regions.add(new SdlGpuRegion(rect.x(), rect.y(), rect.width(), rect.height()));
        }
        return regions;
    }

    private static GpuTexture texture(GpuDevice device, PhysicalSize size) {
        return device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, size.width(), size.height()));
    }

    private static PixelBuffer heap(PhysicalSize size) {
        return PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
    }

    private static void requireSize(PhysicalSize size) {
        Objects.requireNonNull(size, "size");
        if (size.isEmpty()) {
            throw new IllegalArgumentException("a raster of " + size + " has no pixels");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(this + " is closed");
        }
    }
}
