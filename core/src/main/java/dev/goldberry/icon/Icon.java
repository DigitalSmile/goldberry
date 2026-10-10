package dev.goldberry.icon;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.assets.BundledAssets;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Stroke;

/// One icon from the bundled Lucide set, parsed once at one size and drawn many
/// times.
///
/// ```java
/// var plus = Icon.bundled("plus", 16);
/// plus.draw(frame, x, y, 0xFF1F2937);
/// ```
///
/// Markup never builds one: `icon="plus"` on a widget resolves against the
/// `Icons` registry the application owns, and the application makes the `Icon`.
///
/// Lucide's 1870 icons are **stroked**, not filled: each is a 24×24 box of 2px
/// round-capped, round-joined strokes with no fill at all. That is why an icon
/// carries a [#strokeWidth()] as well as a path, and why drawing one with `fill`
/// produces a blob rather than a symbol.
///
/// **An icon belongs to a size**, the way a [dev.goldberry.text.font.Font]
/// does: the path is built scaled, so the coordinates handed to the rasterizer
/// are already the ones it draws, and there is no transform to get wrong at draw
/// time. Drawing the same symbol at two sizes is two `Icon`s.
///
/// Immutable and safe to share between threads: its geometry is a [Path], a
/// value, not a native allocation, so there is nothing to release and
/// [#close()] does nothing.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#icons).
public final class Icon implements AutoCloseable {

    /// Lucide's stroke ends and corners. Not a choice — it is how the set is
    /// drawn, and butt caps make every icon look clipped.
    ///
    /// The width is the icon's own, so this is the pen without it: [#draw] and
    /// anything drawing an [#outline()] by hand both start here rather than
    /// naming two enum constants and hoping they match.
    public static Stroke pen(double strokeWidth) {
        return Stroke.round(strokeWidth);
    }

    private final String name;
    private final double size;
    private final double strokeWidth;
    private final Path path;

    private Icon(String name, double size, double strokeWidth, Path path) {
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
        var data = BundledAssets.icon(name)
                .orElseThrow(() -> new NoSuchElementException("no bundled icon named \"" + name + "\"."
                        + " BundledAssets.iconNames() lists the "
                        + BundledAssets.iconNames().size()
                        + " there are."));
        return of(name, data, size);
    }

    /// A bundled Lucide icon at `size` logical pixels square, or nothing when
    /// the set has no icon of that name.
    ///
    /// The lookup for a name that comes from outside the program: a
    /// configuration file, a server, a document being edited. An application
    /// draws a fallback of its own on [Optional#empty()] rather than catching
    /// the exception [#bundled] throws, which is the right answer for a name
    /// written in the source.
    ///
    /// @throws IllegalArgumentException if the size is not positive and finite
    public static Optional<Icon> find(String name, double size) {
        Objects.requireNonNull(name, "name");
        return BundledAssets.icon(name).map(data -> of(name, data, size));
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
                    "an icon size must be a positive, finite number of logical pixels, and " + size + " is not");
        }

        var scale = size / BundledAssets.ICON_SIZE;
        var builder = Path.builder();
        SvgPath.appendTo(builder, pathData, scale);
        return new Icon(name, size, BundledAssets.ICON_STROKE_WIDTH * scale, builder.build());
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
        frame.strokePath(x, y, path, pen(strokeWidth), argb);
    }

    /// The outline, for anything that wants to draw it differently — filled, at
    /// another weight, or written out as SVG.
    ///
    /// A [Path] is a **value**: handing one out hands out no native resource
    /// with a thread and a lifetime, so the caller may keep it for as long as it
    /// likes.
    public Path outline() {
        return path;
    }

    /// Does nothing, and is kept so that a `try`-with-resources over an icon
    /// keeps compiling.
    ///
    /// An `Icon` is two doubles, a name and a [Path], all plain Java values, and
    /// it is garbage like anything else. [AutoCloseable] stays so that closing
    /// one is harmless rather than a compile error.
    @Override
    public void close() {
        // Deliberately empty -- see the javadoc.
    }

    @Override
    public String toString() {
        return "Icon[" + name + " @" + size + "]";
    }
}
