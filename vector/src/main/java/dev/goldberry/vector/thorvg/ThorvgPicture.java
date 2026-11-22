package dev.goldberry.vector.thorvg;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PixelFormat;

/// A document ThorVG has parsed: a `Tvg_Paint` picture and the size it reports.
///
/// Every method runs inside [ThorvgEngine#call] and says so with an assertion;
/// none takes the lock itself, so that a caller can parse, draw and release in
/// one turn of it.
///
/// @param handle the picture; owned by whoever holds this record until
///               [#release]
/// @param width  its natural width, in the document's user units
/// @param height its natural height
public record ThorvgPicture(MemorySegment handle, float width, float height) {

    /// `Tvg_Matrix`: nine floats, row-major.
    private static final int MATRIX_FLOATS = 9;

    public ThorvgPicture {
        Objects.requireNonNull(handle, "handle");
    }

    /// Parses `document`, which ThorVG copies, so the array may change after.
    ///
    /// @throws ImageDecodeException when ThorVG refuses the bytes, or reports a
    ///                              size that is not a positive, finite number
    public static ThorvgPicture load(VectorLibrary library, byte[] document) {
        assert ThorvgEngine.isHeldByCurrentThread();
        var calls = library.picture();
        try (var arena = Arena.ofConfined()) {
            var bytes = arena.allocate(document.length);
            MemorySegment.copy(document, 0, bytes, ValueLayout.JAVA_BYTE, 0, document.length);
            var picture = calls.newPicture().call();
            if (picture.equals(MemorySegment.NULL)) {
                throw new ImageDecodeException("ThorVG could not make a picture for the document");
            }
            var loaded = calls.loadData()
                    .call(picture, bytes, document.length, arena.allocateFrom("svg"), MemorySegment.NULL, true);
            if (loaded != TvgResult.SUCCESS.code()) {
                calls.release().call(picture);
                throw new ImageDecodeException("not an SVG document ThorVG can read: " + TvgResult.describe(loaded));
            }
            var width = arena.allocate(JAVA_FLOAT);
            var height = arena.allocate(JAVA_FLOAT);
            var sized = calls.getSize().call(picture, width, height);
            var w = width.get(JAVA_FLOAT, 0);
            var h = height.get(JAVA_FLOAT, 0);
            if (sized != TvgResult.SUCCESS.code() || !(w > 0) || !(h > 0) || !Float.isFinite(w) || !Float.isFinite(h)) {
                calls.release().call(picture);
                throw new ImageDecodeException("the SVG document has no size to draw at: ThorVG reports " + w + "x" + h
                        + (sized == TvgResult.SUCCESS.code() ? "" : ", " + TvgResult.describe(sized)));
            }
            return new ThorvgPicture(picture, w, h);
        }
    }

    /// Draws a copy of this picture into `target`, `width` by `height` pixels at
    /// (`x`, `y`). The picture itself is not touched, so it can be drawn again.
    ///
    /// @param target premultiplied BGRA over direct memory, zeroed, which is
    ///               what [PixelBuffer#allocate] hands out
    /// @throws IllegalStateException when ThorVG refuses a step, which for a
    ///                               document it parsed is its own fault
    public void drawInto(VectorLibrary library, PixelBuffer target, float x, float y, float width, float height) {
        assert ThorvgEngine.isHeldByCurrentThread();
        if (target.format() != PixelFormat.BGRA32_PREMULTIPLIED
                || !target.pixels().isDirect()) {
            throw new IllegalArgumentException("ThorVG draws into direct premultiplied BGRA, not " + target);
        }
        var calls = library.picture();
        var canvasCalls = library.canvas();
        var copy = calls.duplicate().call(handle);
        if (copy.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("ThorVG could not copy a picture to draw it");
        }
        var canvas = canvasCalls.create().call(ThorvgEngine.ENGINE_OPTION_DEFAULT);
        if (canvas.equals(MemorySegment.NULL)) {
            calls.release().call(copy);
            throw new IllegalStateException("ThorVG could not make a canvas");
        }
        var owned = false;
        try (var arena = Arena.ofConfined()) {
            check("tvg_picture_set_size", calls.setSize().call(copy, width, height));
            var matrix = arena.allocate(JAVA_FLOAT, MATRIX_FLOATS);
            matrix.setAtIndex(JAVA_FLOAT, 0, 1);
            matrix.setAtIndex(JAVA_FLOAT, 2, x);
            matrix.setAtIndex(JAVA_FLOAT, 4, 1);
            matrix.setAtIndex(JAVA_FLOAT, 5, y);
            matrix.setAtIndex(JAVA_FLOAT, 8, 1);
            check("tvg_paint_set_transform", calls.setTransform().call(copy, matrix));

            var size = target.size();
            check(
                    "tvg_swcanvas_set_target",
                    canvasCalls
                            .setTarget()
                            .call(
                                    canvas,
                                    MemorySegment.ofBuffer(target.pixels()),
                                    target.stride() / 4,
                                    size.width(),
                                    size.height(),
                                    ThorvgEngine.COLORSPACE_ARGB8888));
            check("tvg_canvas_add", canvasCalls.add().call(canvas, copy));
            owned = true;
            // No clear: the buffer is new, and zeroed already.
            check("tvg_canvas_draw", canvasCalls.draw().call(canvas, false));
            check("tvg_canvas_sync", canvasCalls.sync().call(canvas));
        } finally {
            // The canvas releases the copy once it holds it; before that, it is ours.
            if (!owned) {
                calls.release().call(copy);
            }
            canvasCalls.destroy().call(canvas);
        }
    }

    /// Frees the picture. Nothing may use this record after.
    public void release(VectorLibrary library) {
        assert ThorvgEngine.isHeldByCurrentThread();
        library.picture().release().call(handle);
    }

    private static void check(String function, int result) {
        if (result != TvgResult.SUCCESS.code()) {
            throw new IllegalStateException(function + " failed: " + TvgResult.describe(result));
        }
    }
}
