package io.github.digitalsmile.goldberry.gpu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuRegion;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;

/// A copy pass of a [GpuFrame]: CPU memory into textures and buffers, usable
/// only inside the body [GpuFrame#copyPass] runs it in.
///
/// Every upload goes through the device's staging memory ([Upload]): the bytes
/// are copied out of the caller's buffer before the method returns, so the
/// buffer can be reused at once, and nothing waits for the GPU.
///
/// **Partial uploads keep the rest.** An upload of regions writes those
/// regions and leaves every other pixel of the texture as it was, which is what
/// a damage-only upload of the UI needs. An upload of the whole of a texture or
/// buffer instead lets the driver give it fresh memory if the GPU is still
/// reading the old, so it never waits either.
public final class CopyPass {

    private final GpuDevice device;
    private final SdlGpuCommandBuffer.CopyPass sdl;
    private boolean ended;

    CopyPass(GpuDevice device, SdlGpuCommandBuffer.CopyPass sdl) {
        this.device = device;
        this.sdl = sdl;
    }

    /// Uploads the whole of `destination` from `source`, tightly packed rows
    /// from its position, which is not moved.
    ///
    /// @throws IllegalArgumentException when `source` holds too few bytes, or
    ///                                  `destination` is a depth texture,
    ///                                  another device's or closed
    public void upload(GpuTexture destination, ByteBuffer source) {
        upload(
                destination,
                source,
                destination.width() * destination.format().bytesPerPixel(),
                List.of(new PhysicalRect(0, 0, destination.width(), destination.height())));
    }

    /// Uploads the damaged `regions` of `frame`, a painted UI frame, into
    /// `destination`, a [TextureFormat#B8G8R8A8_UNORM] texture of the frame's
    /// size: the composited window's upload (`docs/gpu-plan.md`, phase 3).
    ///
    /// @throws IllegalArgumentException when the texture is not BGRA or not the
    ///                                  frame's size, or a region is outside it
    public void upload(GpuTexture destination, PixelBuffer frame, List<PhysicalRect> regions) {
        if (frame.format() != PixelFormat.BGRA32_PREMULTIPLIED) {
            throw new IllegalArgumentException(frame.format() + " has no texture format");
        }
        if (destination.format() != TextureFormat.B8G8R8A8_UNORM) {
            throw new IllegalArgumentException(destination + " is not B8G8R8A8_UNORM, which a frame is");
        }
        if (frame.size().width() != destination.width() || frame.size().height() != destination.height()) {
            throw new IllegalArgumentException(frame.size() + " is not the size of " + destination);
        }
        upload(destination, frame.pixels(), frame.stride(), regions);
    }

    /// Uploads `regions` of an image the size of `destination` into it. Row `y`
    /// of the image starts `y × rowBytes` bytes past `source`'s position, which
    /// is not moved; every region's rows are copied, packed, into staging
    /// memory, then recorded. Empty regions are skipped; overlapping ones are
    /// uploaded twice, which is correct and costs the overlap.
    ///
    /// @throws IllegalArgumentException when a region is outside the texture,
    ///                                  `rowBytes` is shorter than a row, the
    ///                                  source ends before a region's last
    ///                                  pixel, or `destination` is a depth
    ///                                  texture, another device's or closed
    /// @throws GpuException             when the driver refuses the staging memory
    public void upload(GpuTexture destination, ByteBuffer source, int rowBytes, List<PhysicalRect> regions) {
        Objects.requireNonNull(source, "source");
        requireOpen();
        var texture = destination.sdl(device);
        if (destination.format().isDepth()) {
            throw new IllegalArgumentException(destination + " is a depth texture, which is not uploaded to");
        }
        var pixel = destination.format().bytesPerPixel();
        if (rowBytes < destination.width() * pixel) {
            throw new IllegalArgumentException("rows of " + rowBytes + " bytes are shorter than " + destination + "'s");
        }
        var nonEmpty = new ArrayList<PhysicalRect>(regions.size());
        var total = 0L;
        for (var region : regions) {
            if (region.isEmpty()) {
                continue;
            }
            if (!destination.contains(region)) {
                throw new IllegalArgumentException(region + " is outside " + destination);
            }
            var end = (long) (region.bottom() - 1) * rowBytes + (long) region.right() * pixel;
            if (end > source.remaining()) {
                throw new IllegalArgumentException(
                        region + " ends at byte " + end + ", and the source holds " + source.remaining());
            }
            nonEmpty.add(region);
            total += (long) region.width() * region.height() * pixel;
        }
        if (nonEmpty.isEmpty()) {
            return;
        }
        var upload = device.upload();
        var bytes = total;
        var staging = GpuDevice.call(() -> upload.map(bytes));
        var base = source.position();
        var at = 0;
        var offsets = new int[nonEmpty.size()];
        try {
            for (var i = 0; i < nonEmpty.size(); i++) {
                var region = nonEmpty.get(i);
                offsets[i] = at;
                var row = region.width() * pixel;
                for (var y = region.y(); y < region.bottom(); y++) {
                    staging.put(at, source, base + y * rowBytes + region.x() * pixel, row);
                    at += row;
                }
            }
        } finally {
            // Unmapped whatever happened, so the next upload can map again.
            upload.unmap();
        }
        var transfer = upload.buffer();
        var whole = nonEmpty.size() == 1
                && nonEmpty.getFirst().width() == destination.width()
                && nonEmpty.getFirst().height() == destination.height();
        for (var i = 0; i < nonEmpty.size(); i++) {
            var region = nonEmpty.get(i);
            sdl.upload(
                    transfer,
                    offsets[i],
                    texture,
                    new SdlGpuRegion(region.x(), region.y(), region.width(), region.height()),
                    whole);
        }
    }

    /// Uploads the remaining bytes of `source`, which is not moved, into
    /// `destination` from byte `offset`.
    ///
    /// @throws IllegalArgumentException when they do not fit, `source` is empty,
    ///                                  or `destination` is another device's or
    ///                                  closed
    /// @throws GpuException             when the driver refuses the staging memory
    public void upload(GpuBuffer destination, int offset, ByteBuffer source) {
        Objects.requireNonNull(source, "source");
        requireOpen();
        var buffer = destination.sdl(device);
        var size = source.remaining();
        if (size == 0) {
            throw new IllegalArgumentException("an upload of no bytes");
        }
        if (offset < 0 || (long) offset + size > destination.size()) {
            throw new IllegalArgumentException(size + " bytes from offset " + offset + " do not fit " + destination);
        }
        var upload = device.upload();
        var staging = GpuDevice.call(() -> upload.map(size));
        try {
            staging.put(0, source, source.position(), size);
        } finally {
            upload.unmap();
        }
        var transfer = upload.buffer();
        sdl.uploadToBuffer(transfer, 0, buffer, offset, size, offset == 0 && size == destination.size());
    }

    /// The pass has ended with its body: every method throws from now on.
    void end() {
        ended = true;
    }

    private void requireOpen() {
        device.requireThread();
        if (ended) {
            throw new IllegalStateException("the copy pass has ended: a pass is used only inside its body");
        }
    }
}
