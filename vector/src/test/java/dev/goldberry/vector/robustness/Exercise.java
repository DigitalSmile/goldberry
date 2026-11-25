package dev.goldberry.vector.robustness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.util.List;

import dev.goldberry.image.Image;
import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.vector.Svg;
import dev.goldberry.widgets.core.image.Fit;

/// What every damaged document is put through, and what it may answer.
///
/// Parse it, and if it parses, draw it at the sizes an `image` asks for (its
/// own shape, another shape, a sliver), in every `Fit`, and close it twice.
/// The contract: a document parses or is refused with an
/// [ImageDecodeException]; a parsed one draws a raster of exactly the size
/// asked; nothing else is thrown, and nothing crashes the process. The
/// sanitizer lane is what turns a read past the end of a buffer into a crash.
final class Exercise {

    /// What happened to one document.
    sealed interface Outcome {
        /// ThorVG read it, and every draw kept the contract.
        record Drawn(int rasters) implements Outcome {}

        /// It was refused, as a damaged document may be.
        record Refused(String reason) implements Outcome {}
    }

    /// The sizes drawn, each against both kinds of viewport: one with the
    /// document's own shape (resized) and one of another (parsed again).
    private static final List<PhysicalSize> SIZES =
            List.of(new PhysicalSize(24, 24), new PhysicalSize(37, 11), new PhysicalSize(1, 64));

    /// The largest side the document's own size is drawn at.
    private static final int LARGEST_OWN = 160;

    private Exercise() {}

    /// Runs `document` through parse, draw and close. `what` names it in a
    /// failure: the document, the damage and the seed.
    static Outcome run(byte[] document, String what) {
        Fuzz.trace(what, document);
        Svg svg;
        try {
            svg = Svg.parse(ByteBuffer.wrap(document), 0xFF20_8040);
        } catch (ImageDecodeException e) {
            return new Outcome.Refused(e.getMessage());
        } catch (RuntimeException e) {
            return fail(what + ": parse threw " + e, e);
        }
        var rasters = 0;
        try (svg) {
            var natural = svg.naturalSize();
            assertTrue(natural.width() >= 0 && natural.height() >= 0, () -> what + ": natural size " + natural);
            for (var size : SIZES) {
                rasters += draw(what, size, () -> svg.render(size));
                for (var fit : Fit.values()) {
                    rasters += draw(what, size, () -> svg.render(size, fit));
                }
            }
            var own = new PhysicalSize(clampSide(natural.width()), clampSide(natural.height()));
            rasters += draw(what, own, () -> svg.render(own));
        }
        svg.close();
        return new Outcome.Drawn(rasters);
    }

    /// One draw: the raster is the size asked.
    private static int draw(String what, PhysicalSize size, Render render) {
        Image image;
        try {
            image = render.render();
        } catch (RuntimeException e) {
            return fail(what + ": drawing at " + size + " threw " + e, e);
        }
        assertEquals(size.width(), image.width(), () -> what + ": width at " + size);
        assertEquals(size.height(), image.height(), () -> what + ": height at " + size);
        // Read a pixel from either end, so a raster is real memory to its end.
        image.argb(0, 0);
        image.argb(size.width() - 1, size.height() - 1);
        return 1;
    }

    /// The document's own size as pixels: at least one, and at most
    /// [#LARGEST_OWN], since a damaged size is any number and the limits have
    /// tests of their own.
    private static int clampSide(float side) {
        if (!(side >= 1)) {
            return 1;
        }
        return (int) Math.min(side, LARGEST_OWN);
    }

    @FunctionalInterface
    private interface Render {
        Image render();
    }
}
