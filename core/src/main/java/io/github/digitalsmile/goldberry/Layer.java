package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import java.util.Objects;
import java.util.function.Consumer;

/// An offscreen surface a subtree is rendered into and composited back from.
///
/// ## Why a subtree would want one
///
/// **Group opacity.** CSS says `opacity` renders the element and its descendants
/// into a buffer and composites *that* once. Goldberry has always multiplied
/// alpha into each box's colours instead, which differs exactly where two
/// children overlap: faded separately, the lower one shows through the upper.
/// [ADR-0064](../../../book/src/adr/0064-a-rounded-rectangle-is-four-cubics.md)
/// stated the difference as an open question and predicted `stack` would make it
/// visible; this is the answer.
///
/// **A raster worth keeping.** A node whose opacity or transform is animating
/// changes where and how faintly it is drawn, not what it looks like. If its
/// pixels are in a layer, a frame of that animation is a blit rather than a
/// repaint of the subtree — which is `docs/design-system.md` §1.7's "layer
/// promotion", and the reason a layer is an object with a lifetime rather than a
/// scratch buffer.
///
/// ## What it is
///
/// A [PixelBuffer] and the size it was allocated at. Nothing else: the Blend2D
/// image is created and destroyed inside each use, exactly as it is for a window
/// frame, because an image is a view over pixels and views are cheap.
///
/// The pixels are **premultiplied BGRA**, like every buffer in the toolkit, and
/// are cleared to fully transparent at the start of each [#paint] — a layer is
/// composited *over* its parent, so anything left from a previous use would show
/// through wherever the new content does not cover.
///
/// Confined to the UI thread and must be closed.
public final class Layer implements AutoCloseable {

    private final PixelBuffer pixels;
    private final PhysicalSize size;
    private final Thread owner = Thread.currentThread();

    private boolean closed;

    /// Whether the raster in here is still the one a caller wants to blit.
    ///
    /// Not interpreted here at all — [io.github.digitalsmile.goldberry.layout.RenderTree]
    /// decides what makes a layer stale and this only remembers the answer. It
    /// lives on the layer rather than beside it so that the flag cannot outlive
    /// the pixels it describes.
    private boolean valid;

    private Layer(PhysicalSize size) {
        this.size = size;
        this.pixels = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
    }

    /// An empty layer of `size` **physical** pixels.
    ///
    /// Physical rather than logical because this is a raster: it is blitted pixel
    /// for pixel onto a frame at the same scale, and a logical size would have to
    /// be rounded somewhere, which is where a layer picks up a half-pixel seam
    /// against the thing it is drawn over.
    public static Layer of(PhysicalSize size) {
        Objects.requireNonNull(size, "size");
        if (size.isEmpty()) {
            throw new IllegalArgumentException(
                    "a layer needs a positive size, and " + size + " has none");
        }
        return new Layer(size);
    }

    /// The size of the raster, in physical pixels.
    public PhysicalSize size() {
        return size;
    }

    /// Whether the raster is marked as still good — see [#valid(boolean)].
    public boolean isValid() {
        return valid && !closed;
    }

    /// Marks the raster good or stale. The caller decides what those mean.
    public Layer valid(boolean value) {
        this.valid = value;
        return this;
    }

    /// Renders into this layer, replacing whatever was in it.
    ///
    /// The frame handed to `painter` is in **logical** coordinates at `scale`,
    /// exactly like a window's, and its origin is this layer's top-left corner —
    /// so a caller drawing a subtree into a layer has to translate the subtree's
    /// absolute position away, and that translation is the only arithmetic a
    /// layer costs.
    ///
    /// Cleared to transparent first. Ended before returning, because pixels a
    /// context has not finished with are not pixels worth compositing.
    ///
    /// Leaves the layer **valid**: it has just been painted, so by definition it
    /// holds what the painter drew.
    public void paint(DisplayScale scale, Consumer<Frame> painter) {
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(painter, "painter");
        requireUsable();

        var frame = new Frame(pixels, scale);
        try {
            // Transparent, not a colour: this is composited over its parent and
            // an opaque clear would paint a rectangle over whatever is behind it.
            frame.fill(0x00000000);
            painter.accept(frame);
        } finally {
            frame.end();
        }
        valid = true;
    }

    /// The buffer, for [Frame#drawLayer].
    PixelBuffer pixels() {
        requireUsable();
        return pixels;
    }

    @Override
    public void close() {
        closed = true;
        valid = false;
    }

    public boolean isClosed() {
        return closed;
    }

    private void requireUsable() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a Layer belongs to the thread that created it, and this is not it");
        }
        if (closed) {
            throw new IllegalStateException("this layer has been closed");
        }
    }

    @Override
    public String toString() {
        return "Layer[" + size + (valid ? ", valid" : ", stale") + (closed ? ", closed" : "") + "]";
    }
}
