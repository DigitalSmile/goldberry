package dev.goldberry.css;

import dev.goldberry.css.value.CssColor;
import dev.goldberry.css.value.Shadow;
import dev.goldberry.paint.Box;

/// What is drawn *around* a box rather than in it: the corner radius, the border,
/// the focus ring and the drop shadow.
///
/// One record rather than seven components on [ComputedStyle] and seven more on
/// [Box], because they are only ever read
/// together — the painter that draws a border needs the radius to draw it along,
/// the ring needs both to sit outside them, and the shadow needs the radius too,
/// because a shadow cast by a rounded box is rounded. Splitting them would put
/// seven arguments through every constructor call in the cascade for no reader's
/// benefit.
///
/// The shadow is here and not on [Box] for the sentence this class opens with: a
/// drop shadow is drawn **around** a box and not in it, it is geometry derived
/// from [#corners], and nothing reads it without also reading them.
///
/// The focus ring is a property rather than a widget's decision. The design
/// system pins it at 2px `--gb-focus`, 2px offset, following the control's
/// radius. A widget that drew its own would be a second place for that number to
/// live, and would have to know its own radius to follow it. As `outline` on
/// `:focus-visible` it is one rule in the toolkit-base layer, it inherits nothing
/// and affects no layout: CSS outlines are drawn outside the border box and take
/// no space, which is exactly what a ring at a 2px offset needs.
///
/// Units are logical pixels, resolved. Percentages are refused by the parser
/// rather than carried: a percentage radius means "of this box's size", and a box
/// does not know its size until layout has run, long after the cascade.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
///
/// @param corners       the four corner radii; [Corners#SQUARE] is a square box.
///                      See [Corners] for why a box ever wants four numbers
/// @param border        the four sides' widths and colours, drawn *inside* the
///                      box's edge and over its padding. See [Border] for why a
///                      box ever wants four sides
/// @param outlineWidth  ring thickness, drawn outside the edge
/// @param outlineColor  `0xAARRGGBB`, not premultiplied
/// @param outlineOffset the gap between the box's edge and the inside of the ring
/// @param shadow        the drop shadow cast behind the box, [Shadow#NONE] for
///                      the overwhelming majority of boxes
public record Decoration(
        Corners corners, Border border, double outlineWidth, int outlineColor, double outlineOffset, Shadow shadow) {

    /// Square corners, no border, no ring, no shadow — what every box starts as.
    public static final Decoration NONE =
            new Decoration(Corners.SQUARE, Border.NONE, 0, CssColor.TRANSPARENT, 0, Shadow.NONE);

    public Decoration {
        // Clamped rather than refused. These arrive from a stylesheet, and the
        // rule for a bad declaration is to drop it and carry on: a negative
        // radius should not take a window down mid-frame. The corners clamp
        // themselves, in [Corners], for the same reason.
        java.util.Objects.requireNonNull(corners, "corners");
        java.util.Objects.requireNonNull(border, "border");
        java.util.Objects.requireNonNull(shadow, "shadow");
        requireFinite(outlineWidth, "outline-width");
        requireFinite(outlineOffset, "outline-offset");
        outlineWidth = Math.max(0, outlineWidth);
    }

    /// A decoration with the same border on all four sides, which is every border
    /// the design system pins and the one a caller usually means.
    public Decoration(
            Corners corners,
            double borderWidth,
            int borderColor,
            double outlineWidth,
            int outlineColor,
            double outlineOffset,
            Shadow shadow) {
        this(corners, Border.all(borderWidth, borderColor), outlineWidth, outlineColor, outlineOffset, shadow);
    }

    /// Whether a border would put ink on the screen.
    ///
    /// Both halves matter: a 1px border in a fully transparent colour draws
    /// nothing, and so does a 0px one in `--gb-border`. Asked before building a
    /// path, because building one costs a native allocation.
    public boolean hasBorder() {
        return border.hasInk();
    }

    /// Whether a focus ring would put ink on the screen.
    public boolean hasOutline() {
        return outlineWidth > 0 && (outlineColor >>> 24) != 0;
    }

    /// Whether a shadow would put ink on the screen.
    public boolean hasShadow() {
        return shadow.hasInk();
    }

    /// Whether this is [#NONE] in effect — nothing to draw and nothing to round.
    public boolean isPlain() {
        return corners.isSquare() && !hasBorder() && !hasOutline() && !hasShadow();
    }

    /// The same radius on all four corners — `border-radius: 8px`, which is every
    /// radius the design system pins.
    public Decoration radius(double value) {
        return corners(Corners.all(value));
    }

    public Decoration corners(Corners value) {
        return new Decoration(value, border, outlineWidth, outlineColor, outlineOffset, shadow);
    }

    /// The same line on all four sides — `border: 1px solid red`.
    public Decoration border(double width, int argb) {
        return border(Border.all(width, argb));
    }

    public Decoration border(Border value) {
        return new Decoration(corners, value, outlineWidth, outlineColor, outlineOffset, shadow);
    }

    /// Every side's width, colours kept — `border-width: 2px`.
    public Decoration borderWidth(double value) {
        return border(border.widths(value, value, value, value));
    }

    /// Every side's colour, widths kept — `border-color: red`.
    public Decoration borderColor(int argb) {
        return border(border.colours(argb, argb, argb, argb));
    }

    public Decoration outline(double width, int argb, double offset) {
        return new Decoration(corners, border, width, argb, offset, shadow);
    }

    public Decoration outlineWidth(double value) {
        return new Decoration(corners, border, value, outlineColor, outlineOffset, shadow);
    }

    public Decoration outlineColor(int argb) {
        return new Decoration(corners, border, outlineWidth, argb, outlineOffset, shadow);
    }

    public Decoration outlineOffset(double value) {
        return new Decoration(corners, border, outlineWidth, outlineColor, value, shadow);
    }

    public Decoration shadow(Shadow value) {
        return new Decoration(corners, border, outlineWidth, outlineColor, outlineOffset, value);
    }

    /// This decoration with every colour's alpha scaled by `alpha`.
    ///
    /// How `opacity` reaches a border and a ring — see
    /// [Box#fade(double)], which is where
    /// the reasoning for multiplying alpha rather than compositing a layer is
    /// written down.
    public Decoration fade(double alpha) {
        if (alpha >= 1) {
            return this;
        }
        return new Decoration(
                corners,
                border.fade(alpha),
                outlineWidth,
                CssColor.fade(outlineColor, alpha),
                outlineOffset,
                shadow.fade(alpha));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, not " + value);
        }
    }
}
