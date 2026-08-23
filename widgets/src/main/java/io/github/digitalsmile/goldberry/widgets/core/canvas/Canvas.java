package io.github.digitalsmile.goldberry.widgets.core.canvas;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Painter;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import java.util.List;
import java.util.Set;

/// An immediate-mode drawing surface — `docs/core-widgets.md` §1's `canvas`.
///
/// ```java
/// new Canvas((frame, size) -> {
///     frame.fillRect(0, 0, size.width(), size.height() / 2, 0xFF88C0D0);
/// });
/// ```
///
/// ```kdl
/// canvas id="plot" class="chart"
/// ```
///
/// **The escape hatch, and the substrate.** `content-widgets.md` §3 builds every
/// chart on this rather than on a chart engine, which is what lets a chart inherit
/// the theme, the text stack, hit testing and the golden corpus. It is also what
/// an application reaches for when the catalog has no widget for what it wants —
/// a waveform, a seating plan, a colour wheel.
///
/// ## It is a box first
///
/// Background, border, radius, padding and every layout property are the
/// stylesheet's, exactly as for `panel`. The painter draws **inside the padding**
/// and is clipped to it, so `canvas { padding: 8px; background: var(--gb-surface) }`
/// is a framed drawing surface and not a surprise
/// ([ADR-0193](../../../../../../../../book/src/adr/0193-a-canvas-is-a-second-clip-depth.md)).
///
/// ## It has no size of its own
///
/// A canvas is not measured: it takes the size the layout gives it, and the
/// painter is told what that turned out to be. A canvas in a `row` with nothing
/// else to size it is zero wide, which is a stylesheet's job to fix
/// (`flex-grow: 1`, a `height`) and not a default this widget can guess — an
/// intrinsic size would be a number invented by the toolkit and drawn by the
/// application.
///
/// ## Markup names no painter yet
///
/// A `canvas` node inflates to a styled, sized surface that draws nothing. The
/// painter is Java, and naming one from a document would need the indirection
/// `icon` and `action` use — a registry the application owns
/// ([ADR-0043](../../../../../../../../book/src/adr/0043-icons-are-stroked-paths.md)).
/// That is filed rather than guessed at, because the shape of the registry
/// depends on whether a painter is a value or a method and nothing has needed
/// one yet.
///
/// @param painter    what to draw, or null for a surface that draws nothing
/// @param attributes `id` and `class`, exactly as on the other primitives
@Markup("canvas")
public record Canvas(Painter painter, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Canvas> {

    public Canvas {
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A canvas that draws `painter`.
    public Canvas(Painter painter) {
        this(painter, Attributes.NONE);
    }

    @Override
    public String cssType() {
        return "canvas";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public Canvas withAttributes(Attributes value) {
        return new Canvas(painter, value);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // No children: a canvas is a leaf that draws. Boxes inside it would be
        // laid out by Yoga and painted *over* whatever the painter drew, which is
        // a `stack` and not a canvas.
        return Box.of().style(style).painting(painter);
    }

    /// Builds a `canvas` from markup — see the class note on why it names no
    /// painter.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Canvas(null, Attributes.of(node));
    }
}
