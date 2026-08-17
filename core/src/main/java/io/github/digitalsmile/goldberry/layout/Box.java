package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssColor;
import io.github.digitalsmile.goldberry.css.Decoration;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.Justify;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Paragraph;
import java.util.List;
import java.util.Objects;

/// A rectangle with a flexbox style and children — the smallest thing that can
/// be laid out and painted.
///
/// This is not the widget model. The three-tree design in ADR-0004 is still
/// open, and inventing it here would be inventing it twice. What this is: the
/// join between the two engines that were bound separately — Yoga decides where
/// the rectangles go, Blend2D draws them — so that the seam between them is
/// exercised by something before the widget layer lands on top of it.
///
/// Immutable, and built by chaining: every method returns a new box. A tree of
/// these is a value, which is what makes it safe to hold one across frames and
/// what the eventual widget tree will also be.
public record Box(
        int background,
        Decoration decoration,
        double opacity,
        Cursor cursor,
        FlexDirection direction,
        Justify justifyContent,
        Align alignItems,
        StyleLength width,
        StyleLength height,
        Insets padding,
        StyleLength gap,
        double flexGrow,
        Text text,
        Glyph icon,
        Mark mark,
        List<Box> children,
        Object owner) {

    // `owner` is an opaque tag, not a field the layout or the paint reads.
    // Hit testing needs to get from a rectangle on screen back to whatever put
    // it there -- an Element, in the widget stack -- and the box tree is the
    // only thing that knows both. Typed as Object so `layout` keeps knowing
    // nothing about widgets (ADR-0054).

    /// Text filling a box, and the colour to draw it in.
    ///
    /// One component rather than two on [Box], because they are meaningless
    /// apart: a paragraph with no colour cannot be drawn and a colour with no
    /// paragraph is not text.
    ///
    /// @param paragraph the text, already shaped
    /// @param argb      `0xAARRGGBB`, not premultiplied
    public record Text(Paragraph paragraph, int argb) {
        public Text {
            Objects.requireNonNull(paragraph, "paragraph");
        }
    }

    /// An icon filling a box, and the colour to stroke it in.
    ///
    /// The same shape as [Text] and for the same reason: an icon with no colour
    /// cannot be drawn. Unlike text it needs no measure function — an icon knows
    /// its own size, which is why it can be a box at all
    /// ([ADR-0043](../../../../../../book/src/adr/0043-icons-are-stroked-paths.md)).
    ///
    /// @param icon the icon, already built at the size it will draw at
    /// @param argb `0xAARRGGBB`, not premultiplied
    public record Glyph(Icon icon, int argb) {
        public Glyph {
            Objects.requireNonNull(icon, "icon");
        }
    }

    /// The indicator inside a control: a checkbox's tick, its mixed-state dash,
    /// a radio's dot.
    ///
    /// A closed set of toolkit-drawn shapes rather than an [Icon], for one
    /// practical reason: an `Icon` owns native memory and has to be closed, so a
    /// widget — a value rebuilt every frame — cannot hold one, and the application
    /// registering an icon just to get a tick inside its own checkbox would be an
    /// absurd thing to ask ([ADR-0059]). These are three line segments and a
    /// circle; the painter draws them from the box's own rectangle.
    ///
    /// Stroked in [#argb] at [#thickness] logical pixels, inset into whatever
    /// rectangle the box was given. Sized by the box, not by the mark: a 16px
    /// glyph is the design system's number (§3) and belongs in a stylesheet, so
    /// the mark fills what it is handed.
    ///
    /// @param kind      which shape
    /// @param argb      `0xAARRGGBB`, not premultiplied
    /// @param thickness the stroke width in logical pixels
    public record Mark(Kind kind, int argb, double thickness) {

        /// The shapes a control's indicator can be.
        public enum Kind {

            /// A tick — `:checked` on a checkbox.
            CHECK,

            /// A horizontal bar — the mixed state of a tri-state checkbox, which
            /// is deliberately *not* a tick: "some of these are on" and "all of
            /// these are on" have to be distinguishable at a glance, and a
            /// greyed tick is not.
            DASH,

            /// A filled circle — `:checked` on a radio, which is why this is here
            /// before `radio` is.
            DOT
        }

        public Mark {
            Objects.requireNonNull(kind, "kind");
            if (!Double.isFinite(thickness) || thickness <= 0) {
                throw new IllegalArgumentException(
                        "a mark needs a positive stroke width, not " + thickness);
            }
        }

        /// This mark with its colour's alpha scaled — see [Box#fade(double)].
        Mark fade(double alpha) {
            return alpha >= 1 ? this : new Mark(kind, CssColor.fade(argb, alpha), thickness);
        }
    }

    /// Fully transparent — a box that lays out and paints nothing.
    public static final int TRANSPARENT = 0x00000000;

    public Box {
        Objects.requireNonNull(decoration, "decoration");
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(justifyContent, "justifyContent");
        Objects.requireNonNull(alignItems, "alignItems");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(padding, "padding");
        Objects.requireNonNull(gap, "gap");
        if (!Double.isFinite(flexGrow) || flexGrow < 0) {
            throw new IllegalArgumentException("flex-grow must be a non-negative number");
        }
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("opacity must be between 0 and 1, not " + opacity);
        }
        children = List.copyOf(children == null ? List.of() : children);
        // Yoga asks a measured node for its size and never lays its children
        // out, so a box that is both would silently lose them. Refused here
        // rather than at layout time, where the box that caused it is harder to
        // find. Text beside other content is a child box holding the text.
        if (icon != null && (text != null || !children.isEmpty())) {
            throw new IllegalArgumentException(
                    "a box with an icon may not also have text or children: it is sized by the"
                            + " icon. Put them side by side in a row.");
        }
        if (text != null && !children.isEmpty()) {
            throw new IllegalArgumentException(
                    "a box with text may not also have " + children.size() + " child(ren):"
                            + " its size comes from the text, so Yoga would never lay the"
                            + " children out. Put the text in a child of its own.");
        }
        // A mark is drawn to fill the box it is on, so anything else on that box
        // would be drawn under it. Refused for the same reason the two above are:
        // the alternative is a control that looks wrong for a reason nothing
        // reports.
        if (mark != null && (text != null || icon != null)) {
            throw new IllegalArgumentException(
                    "a box with a " + mark.kind() + " mark may not also have text or an icon:"
                            + " the mark fills the box. Put them side by side in a row.");
        }
    }

    /// An empty box that takes its size from its style and fills nothing.
    public static Box of() {
        return new Box(
                TRANSPARENT,
                Decoration.NONE,
                1.0,
                Cursor.DEFAULT,
                FlexDirection.ROW,
                Justify.FLEX_START,
                Align.STRETCH,
                StyleLength.UNDEFINED,
                StyleLength.UNDEFINED,
                Insets.ZERO,
                StyleLength.points(0),
                0,
                null,
                null,
                null,
                List.of(),
                null);
    }

    /// A box whose size comes from the text in it.
    ///
    /// The box becomes a **measured leaf**: Yoga proposes a width, the paragraph
    /// wraps at it, and the height that comes back is what the flexbox algorithm
    /// sizes the box around. That is the whole reason text can take part in
    /// layout rather than being drawn over the top of it.
    ///
    /// @param argb `0xAARRGGBB`, not premultiplied
    public static Box text(Paragraph paragraph, int argb) {
        return of().text(new Text(paragraph, argb));
    }

    public Box text(Text value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, padding, gap, flexGrow, value, icon, mark, children, owner);
    }

    /// A box that is one icon, sized by it.
    ///
    /// The answer to the question ADR-0043 left open — "an icon is not a `Box`,
    /// because nothing decides an icon's intrinsic size until the widget model
    /// does". The widget model is here, and the answer turned out to be simpler
    /// than expected: an icon is built at a size and that size *is* its
    /// intrinsic one, so it needs no measure function and no callback into C.
    ///
    /// @param argb `0xAARRGGBB`, not premultiplied
    public static Box icon(Icon icon, int argb) {
        var size = StyleLength.points((float) icon.size());
        return of().icon(new Glyph(icon, argb)).size(size, size);
    }

    public Box icon(Glyph value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, padding, gap, flexGrow, text, value, mark, children, owner);
    }

    /// A box that is one indicator — a checkbox's tick, a radio's dot.
    ///
    /// Unlike [#icon(Icon, int)] this does **not** size the box: a [Mark] is
    /// drawn to fill whatever rectangle it is given, and the design system puts
    /// that number — a 16px glyph (§3) — in a stylesheet where an application can
    /// override it.
    public Box mark(Mark value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, padding, gap, flexGrow, text, icon, value, children, owner);
    }

    /// A box filled with `argb` — `0xAARRGGBB`, not premultiplied, exactly as
    /// [io.github.digitalsmile.goldberry.Frame] takes it.
    public static Box filled(int argb) {
        return of().background(argb);
    }

    /// The shape the pointer takes over this box (§7.3).
    ///
    /// A property of the painted rectangle rather than of the widget, for the
    /// same reason hit testing is: what the cursor should be is a question about
    /// what is on screen, and the box tree is what is on screen (ADR-0054).
    public Box cursor(Cursor value) {
        return new Box(background, decoration, opacity, Objects.requireNonNull(value, "cursor"), direction,
                justifyContent, alignItems, width, height, padding, gap, flexGrow, text, icon,
                mark, children, owner);
    }

    public Box background(int argb) {
        return new Box(argb, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, padding, gap, flexGrow, text, icon, mark, children, owner);
    }

    /// How opaque this box **and everything under it** is drawn.
    ///
    /// Inherited down the box tree by the painter rather than by the cascade,
    /// which is what `opacity` means in CSS: it is not an inherited property, but
    /// its effect is, because it applies to the rendered subtree.
    public Box opacity(double value) {
        return new Box(background, decoration, value, cursor, direction, justifyContent,
                alignItems, width, height, padding, gap, flexGrow, text, icon, mark, children,
                owner);
    }

    /// The radius, border and focus ring drawn around this box.
    public Box decoration(Decoration value) {
        return new Box(background, Objects.requireNonNull(value, "decoration"), opacity, cursor, direction,
                justifyContent, alignItems, width, height, padding, gap, flexGrow, text, icon,
                mark, children, owner);
    }

    public Box direction(FlexDirection value) {
        return new Box(background, decoration, opacity, cursor, value, justifyContent, alignItems,
                width, height, padding, gap, flexGrow, text, icon, mark, children, owner);
    }

    public Box justifyContent(Justify value) {
        return new Box(background, decoration, opacity, cursor, direction, value, alignItems,
                width, height, padding, gap, flexGrow, text, icon, mark, children, owner);
    }

    public Box alignItems(Align value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, value,
                width, height, padding, gap, flexGrow, text, icon, mark, children, owner);
    }

    public Box size(StyleLength w, StyleLength h) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                w, h, padding, gap, flexGrow, text, icon, mark, children, owner);
    }

    /// The same padding on every edge — the common case, and what most of the
    /// toolkit's own boxes want.
    public Box padding(StyleLength value) {
        return padding(Insets.all(value));
    }

    public Box padding(Insets value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, value, gap, flexGrow, text, icon, mark, children, owner);
    }

    public Box gap(StyleLength value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, padding, value, flexGrow, text, icon, mark, children, owner);
    }

    /// Share of the free space this box takes along its parent's main axis.
    public Box grow(double value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, padding, gap, value, text, icon, mark, children, owner);
    }

    /// This box tagged with whatever produced it.
    ///
    /// Set by the renderer; read by hit testing. Nothing between the two looks
    /// at it.
    public Box owner(Object value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, padding, gap, flexGrow, text, icon, mark, children, value);
    }

    public Box children(Box... value) {
        return new Box(background, decoration, opacity, cursor, direction, justifyContent, alignItems,
                width, height, padding, gap, flexGrow, text, icon, mark, List.of(value), owner);
    }

    /// This box with every colour it carries faded by `alpha`.
    ///
    /// What `opacity` means once it has reached the painter. The alpha of the
    /// background, the text, the icon, the mark, the border and the ring are each
    /// multiplied; nothing else changes, and `alpha >= 1` returns this box
    /// unchanged so the ordinary case allocates nothing.
    ///
    /// **This is not CSS group opacity.** The specified behaviour is to render
    /// the subtree into a layer and composite that layer once, so two overlapping
    /// children at `opacity: .5` show the backdrop through both rather than
    /// showing one through the other. Multiplying alpha per box differs exactly
    /// where children overlap — and nothing in the widget canon overlaps: a
    /// control is a row of a mark, an icon and a label, laid out side by side by
    /// Yoga. The design system asks for opacity in one place, `:disabled` at 45%
    /// (§2.1), and there it is indistinguishable. `stack` is the widget that will
    /// make the difference visible, and the layer is what it will need
    /// ([ADR-0064]).
    ///
    /// @param alpha 0 to 1
    public Box fade(double alpha) {
        if (alpha >= 1) {
            return this;
        }
        return new Box(
                CssColor.fade(background, alpha),
                decoration.fade(alpha),
                // Applied, so it must not be applied again: the painter accumulates
                // down the tree and fades each box exactly once.
                1.0,
                cursor, direction, justifyContent, alignItems, width, height, padding, gap,
                flexGrow,
                text == null ? null : new Text(text.paragraph(), CssColor.fade(text.argb(), alpha)),
                icon == null ? null : new Glyph(icon.icon(), CssColor.fade(icon.argb(), alpha)),
                mark == null ? null : mark.fade(alpha),
                // Not applied to the children here. The painter walks the tree
                // and accumulates, so a nested opacity multiplies once per level
                // rather than once per level per ancestor.
                children,
                owner);
    }

    /// This box with every property a [ComputedStyle] carries applied to it.
    ///
    /// The join between the CSS engine and the two rendering engines, and the
    /// reason §8's property split is worth stating as an invariant: the layout
    /// half of the style lands on the fields Yoga reads, and the paint half on
    /// the ones Blend2D does. Nothing here interprets a string.
    ///
    /// `text`, `mark` and `children` are **not** taken from the style — a
    /// stylesheet decides how a node looks, not what it contains. But
    /// [ComputedStyle#color] does reach all three, because that is what `color`
    /// means.
    ///
    /// [ComputedStyle#opacity] is **not** applied here either, and that is
    /// deliberate rather than an omission: it belongs to the subtree, not to this
    /// box, so the painter accumulates it down the tree and calls [#fade(double)].
    /// Applying it here would fade a parent and leave its children at full
    /// strength.
    public Box style(ComputedStyle style) {
        Objects.requireNonNull(style, "style");
        return new Box(
                style.background(),
                style.decoration(),
                style.opacity(),
                style.cursor(),
                style.direction(),
                style.justifyContent(),
                style.alignItems(),
                style.width(),
                style.height(),
                style.padding(),
                style.gap(),
                style.flexGrow(),
                text == null ? null : new Text(text.paragraph(), style.color()),
                // `color` reaches an icon exactly as it reaches text: Lucide's
                // set is drawn to be tinted, and a stylesheet saying `color`
                // means the same thing to both.
                icon == null ? null : new Glyph(icon.icon(), style.color()),
                mark == null ? null : new Mark(mark.kind(), style.color(), mark.thickness()),
                children,
                owner);
    }
}
