package io.github.digitalsmile.goldberry.icon;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendStrokeJoin;
import java.util.NoSuchElementException;
import java.util.Objects;

/// One bundled icon, parsed once and drawn many times.
///
/// Lucide's 1544 icons are **stroked**, not filled: each is a 24×24 box of 2px
/// round-capped, round-joined strokes with no fill at all (ADR-0033). That is
/// why an icon carries a [#strokeWidth()] as well as a path, and why drawing one
/// with `fill` produces a blob rather than a symbol.
///
/// **An icon belongs to a size**, the way a [io.github.digitalsmile.goldberry.text.Font]
/// does and for the same reason (ADR-0034): the path is built scaled, so the
/// coordinates handed to Blend2D are already the ones it rasterizes, and there
/// is no transform to get wrong at draw time. Drawing the same symbol at two
/// sizes is two `Icon`s.
///
/// Confined to the thread that created it, and must be closed.
public final class Icon implements AutoCloseable {

    /// Lucide's stroke ends and corners. Not a choice — it is how the set is
    /// drawn, and butt caps make every icon look clipped.
    static final BlendStrokeCap CAP = BlendStrokeCap.ROUND;
    static final BlendStrokeJoin JOIN = BlendStrokeJoin.ROUND;

    private final String name;
    private final double size;
    private final double strokeWidth;
    private final BlendPath path;

    private Icon(String name, double size, double strokeWidth, BlendPath path) {
        this.name = name;
        this.size = size;
        this.strokeWidth = strokeWidth;
        this.path = path;
    }

    /// A bundled Lucide icon at `size` logical pixels square.
    ///
    /// @throws NoSuchElementException if the set has no icon of that name
    /// @throws IllegalArgumentException if the size is not positive and finite
    public static Icon bundled(String name, double size) {
        Objects.requireNonNull(name, "name");
        var data = BundledAssets.icon(name).orElseThrow(() -> new NoSuchElementException(
                "no bundled icon named \"" + name + "\"."
                        + " BundledAssets.iconNames() lists the " + BundledAssets.iconNames().size()
                        + " there are."));
        return of(name, data, size);
    }

    /// An icon from path data in a 24×24 box, drawn at `size`.
    ///
    /// The escape hatch for an icon that is not in the bundled set. The box is
    /// still 24×24, because that is what the stroke width is relative to — data
    /// authored against a different box strokes at the wrong weight.
    ///
    /// @throws IllegalArgumentException if the size is not positive and finite,
    ///         or the path data is malformed
    public static Icon of(String name, String pathData, double size) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pathData, "pathData");
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException(
                    "an icon size must be a positive, finite number of logical pixels, and "
                            + size + " is not");
        }

        var scale = size / BundledAssets.ICON_SIZE;
        var path = BlendPath.create();
        try {
            SvgPath.appendTo(path, pathData, scale);
        } catch (RuntimeException | Error e) {
            path.close();
            throw e;
        }
        return new Icon(name, size, BundledAssets.ICON_STROKE_WIDTH * scale, path);
    }

    /// The icon's name, for diagnostics.
    public String name() {
        return name;
    }

    /// The box this icon draws in, in logical pixels. Square, always.
    public double size() {
        return size;
    }

    /// The stroke width this icon is drawn with, scaled from Lucide's 2px in a
    /// 24×24 box to whatever [#size()] is.
    public double strokeWidth() {
        return strokeWidth;
    }

    /// Draws the icon with its top-left at logical `(x, y)`.
    ///
    /// Stroked at [#strokeWidth()] with round caps and joins, which is how the
    /// set is drawn. Filling it instead produces a solid blob — Lucide's shapes
    /// are outlines, and most of them are not closed.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void draw(Frame frame, double x, double y, int argb) {
        Objects.requireNonNull(frame, "frame");
        frame.strokePath(x, y, path, strokeWidth, CAP, JOIN, argb);
    }

    /// The path, for anything that wants to draw it differently. Exposed the way
    /// [io.github.digitalsmile.goldberry.text.Font] exposes its Blend2D objects:
    /// `:core` builds them and `Frame` draws them, and hiding the type would
    /// mean a second drawing API that only this class could reach.
    public BlendPath path() {
        return path;
    }

    /// Releases the path. Idempotent.
    @Override
    public void close() {
        path.close();
    }

    @Override
    public String toString() {
        return "Icon[" + name + " @" + size + "]";
    }
}
