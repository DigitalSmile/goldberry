package dev.goldberry.camera.mjpeg;

import java.nio.ByteBuffer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.camera.backend.BgraPool;
import dev.goldberry.image.ChannelOrder;
import dev.goldberry.image.Image;
import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.image.PixelTarget;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.render.model.PhysicalSize;

/// Decodes a camera's Motion JPEG frames, one at a time on one thread, into a
/// [BgraPool], so each picture keeps its bytes until two more have been
/// decoded after it.
///
/// The decoder is the rasterizer's, through [Image#decodeInto]: the frame goes
/// straight from the camera's buffer into the pool, with no copy between. A
/// frame without Huffman tables is first copied into a buffer of the
/// decoder's own with JPEG's standard ones put in before its scan
/// ([StandardHuffmanTables]). A frame of a new size makes a new pool; the
/// pictures of the old one stay as they are.
///
/// Not thread-safe: the camera's capture thread owns it.
public final class MjpegDecoder {

    private @Nullable BgraPool pool;
    private ByteBuffer patched = ByteBuffer.allocateDirect(0);
    private long decoded;
    private long givenTables;

    /// Decodes the JPEG from `jpeg`'s position to its limit, leaving the
    /// buffer as it was, into the next slot.
    ///
    /// @return the picture, opaque BGRA at the frame's own size
    /// @throws ImageDecodeException when the frame is not a JPEG the
    ///         rasterizer decodes; no picture handed out before is touched
    public VideoPicture decode(ByteBuffer jpeg, long ptsNanos) {
        var frame = jpeg.slice();
        var header = JpegHeader.read(frame);
        var source = frame;
        if (!header.definesHuffmanTables()) {
            source = withStandardTables(frame, header.scanOffset());
            givenTables++;
        }
        // The pool's next slot is named, not taken, until the decode is done.
        Image.decodeInto(source, ChannelOrder.BGRA, this::target);
        var into = pool;
        if (into == null) {
            throw new IllegalStateException("decoded with no pool to decode into");
        }
        decoded++;
        return into.written(ptsNanos);
    }

    /// Frames decoded so far.
    public long decoded() {
        return decoded;
    }

    /// How many frames came without Huffman tables and were given the
    /// standard ones.
    public long givenTables() {
        return givenTables;
    }

    private PixelTarget target(PhysicalSize size) {
        var into = pool;
        if (into == null || !into.fits(size.width(), size.height())) {
            into = new BgraPool(size.width(), size.height());
            pool = into;
        }
        return new PixelTarget(into.next(), into.stride());
    }

    /// `frame` with the standard tables' segment put in at `scan`, in a buffer
    /// reused from frame to frame.
    private ByteBuffer withStandardTables(ByteBuffer frame, int scan) {
        var tables = StandardHuffmanTables.segment();
        var total = frame.remaining() + tables.remaining();
        if (patched.capacity() < total) {
            // A little room over, so a frame that grows by a few bytes does not
            // allocate again.
            patched = ByteBuffer.allocateDirect(total + total / 4);
        }
        var out = patched.clear();
        out.put(0, frame, 0, scan);
        out.put(scan, tables, 0, tables.remaining());
        out.put(scan + tables.remaining(), frame, scan, frame.remaining() - scan);
        return out.slice(0, total);
    }
}
