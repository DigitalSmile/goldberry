package dev.goldberry.gpu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// A copy pass of a [GpuFrame]: CPU memory into textures and buffers, usable
/// only inside the body [GpuFrame#copyPass] runs it in.
///
/// Every upload goes through the device's staging memory: the bytes
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
    /// For a block-compressed format the rows are rows of blocks, each
    /// [TextureFormat#bytesPerRow] long: the level's blocks as an encoder
    /// writes them.
    ///
    /// @throws IllegalArgumentException when `source` holds too few bytes, or
    ///                                  `destination` is a depth texture,
    ///                                  another device's or closed
    public void upload(GpuTexture destination, ByteBuffer source) {
        upload(
                destination,
                source,
                packedRow(destination.format(), destination.width()),
                List.of(new PhysicalRect(0, 0, destination.width(), destination.height())));
    }

    /// Uploads the whole of `destination`, one level of one layer, from
    /// `source`, tightly packed rows of the level's width from its position,
    /// which is not moved: a mip level, a cube's face, a volume's slice.
    ///
    /// @throws IllegalArgumentException as [#upload(GpuTexture, ByteBuffer)]
    public void upload(TextureView destination, ByteBuffer source) {
        Objects.requireNonNull(destination, "destination");
        upload(
                destination,
                source,
                packedRow(destination.format(), destination.width()),
                List.of(new PhysicalRect(0, 0, destination.width(), destination.height())));
    }

    /// Uploads `regions` of an image the size of `destination`'s level into it,
    /// as [#upload(GpuTexture, ByteBuffer, int, List)] does for level 0.
    ///
    /// @throws IllegalArgumentException as that method
    /// @throws GpuException             as that method
    public void upload(TextureView destination, ByteBuffer source, int rowBytes, List<PhysicalRect> regions) {
        Objects.requireNonNull(destination, "destination");
        upload(destination.texture(), destination.level(), destination.layer(), source, rowBytes, regions);
    }

    /// Uploads the damaged `regions` of `frame`, a painted UI frame, into
    /// `destination`, a [TextureFormat#B8G8R8A8_UNORM] texture of the frame's
    /// size: the composited window's upload.
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
    /// For a block-compressed format, regions are still in texels, but each
    /// starts on a block and ends on one or at the level's edge, and the image
    /// is rows of blocks: row `y` of blocks, texels `4y` to `4y + 3`, starts
    /// `y × rowBytes` bytes in.
    ///
    /// @throws IllegalArgumentException when a region is outside the texture
    ///                                  or not on its blocks, `rowBytes` is
    ///                                  shorter than a row, the source ends
    ///                                  before a region's last pixel, or
    ///                                  `destination` is a depth texture,
    ///                                  another device's or closed
    /// @throws GpuException             when the driver refuses the staging memory
    public void upload(GpuTexture destination, ByteBuffer source, int rowBytes, List<PhysicalRect> regions) {
        upload(destination, 0, 0, source, rowBytes, regions);
    }

    private void upload(
            GpuTexture texture, int level, int layer, ByteBuffer source, int rowBytes, List<PhysicalRect> regions) {
        Objects.requireNonNull(source, "source");
        requireOpen();
        var sdlTexture = texture.sdl(device);
        if (texture.format().isDepth()) {
            throw new IllegalArgumentException(texture + " is a depth texture, which is not uploaded to");
        }
        var destination = texture.view(level, layer);
        var format = destination.format();
        if (rowBytes < format.bytesPerRow(destination.width())) {
            throw new IllegalArgumentException("rows of " + rowBytes + " bytes are shorter than " + destination + "'s");
        }
        var block = format.bytesPerBlock();
        var nonEmpty = new ArrayList<PhysicalRect>(regions.size());
        var inBlocks = new ArrayList<SdlGpuRegion>(regions.size());
        for (var region : regions) {
            if (region.isEmpty()) {
                continue;
            }
            if (!destination.contains(region)) {
                throw new IllegalArgumentException(region + " is outside " + destination);
            }
            var blocks = blocks(format, destination.size(), region);
            var end = (long) (blocks.bottom() - 1) * rowBytes + (long) blocks.right() * block;
            if (end > source.remaining()) {
                throw new IllegalArgumentException(
                        region + " ends at byte " + end + ", and the source holds " + source.remaining());
            }
            nonEmpty.add(region);
            inBlocks.add(new SdlGpuRegion(blocks.x(), blocks.y(), blocks.width(), blocks.height()));
        }
        if (nonEmpty.isEmpty()) {
            return;
        }
        var upload = device.upload();
        // Staged a row of blocks at a time, which for a plain format is a row
        // of pixels.
        var offsets = GpuDevice.call(() -> upload.stage(source, rowBytes, block, inBlocks));
        var transfer = upload.buffer();
        // Cycling hands the texture fresh memory when the GPU still reads the
        // old, which is only right when every texel of every level, layer and
        // slice is about to be written: the whole of a one-level, one-layer,
        // one-slice texture.
        var whole = nonEmpty.size() == 1
                && texture.mipLevels() == 1
                && texture.layers() == 1
                && texture.depth() == 1
                && nonEmpty.getFirst().width() == destination.width()
                && nonEmpty.getFirst().height() == destination.height();
        for (var i = 0; i < nonEmpty.size(); i++) {
            var region = nonEmpty.get(i);
            sdl.upload(
                    transfer,
                    offsets[i],
                    sdlTexture,
                    level,
                    layer,
                    new SdlGpuRegion(region.x(), region.y(), region.width(), region.height()),
                    whole);
        }
    }

    /// `region` of a level `level` texels in size, in `format`'s blocks: the
    /// region itself for a format that is not compressed.
    ///
    /// @throws IllegalArgumentException when the region does not start on a
    ///                                  block, or ends neither on one nor at
    ///                                  the level's edge
    static PhysicalRect blocks(TextureFormat format, PhysicalSize level, PhysicalRect region) {
        var size = format.blockSize();
        var across = size.width();
        var down = size.height();
        if (region.x() % across != 0
                || region.y() % down != 0
                || (region.width() % across != 0 && region.right() != level.width())
                || (region.height() % down != 0 && region.bottom() != level.height())) {
            throw new IllegalArgumentException(region + " of a " + level + " level of " + format
                    + " does not start and end on its " + across + "x" + down + " blocks");
        }
        return new PhysicalRect(
                region.x() / across,
                region.y() / down,
                Math.ceilDiv(region.width(), across),
                Math.ceilDiv(region.height(), down));
    }

    /// The bytes of one tightly packed row of `width` texels of `format`.
    private static int packedRow(TextureFormat format, int width) {
        var row = format.bytesPerRow(width);
        if (row > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a row of " + width + " texels of " + format + " is past 2 GiB");
        }
        return (int) row;
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
        GpuDevice.run(() -> upload.stage(source));
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
