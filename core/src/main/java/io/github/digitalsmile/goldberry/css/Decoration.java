package io.github.digitalsmile.goldberry.css;

import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.paint.Box;

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
/// ## Why the shadow is here and not on `Box`
///
/// [Box] already carries twenty-seven components and `box-shadow` would have been
/// the twenty-eighth, with a wither to write in every one of the others. It
/// belongs here on the sentence this class opens with: a drop shadow is drawn
/// **around** a box and not in it, it is geometry derived from [#corners], and
/// nothing reads it without also reading them (ADR-0310).
///
/// ## Why the ring is a property and not a widget's decision
///
/// The design system pins the focus ring at 2px `--gb-focus`, 2px offset,
/// following the control's radius (`docs/design-system.md` §2.2). A widget that
/// drew its own would be a second place for that number to live, and would have
/// to know its own radius to follow it. As `outline` on `:focus-visible` it is one
/// rule in the toolkit-base layer, it inherits nothing and affects no layout — CSS
/// outlines are drawn outside the border box and take no space, which is exactly
/// what a ring at a 2px offset needs.
///
/// ## Units
///
/// Logical pixels, resolved. Percentages are refused by the parser rather than
/// carried: a percentage radius means "of this box's size", and a box does not
/// know its size until Yoga has run — long after the cascade.
///
/// @param corners       the four corner radii; [Corners#SQUARE] is a square box.
///                      One number until `border-radius: 7px 7px 0 0` needed two
///                      (ADR-0216) — see [Corners] for why a box ever wants that
/// @param borderWidth   border thickness, drawn *inside* the box's edge as CSS's
///                      `border-box` sizing requires
/// @param borderColor   `0xAARRGGBB`, not premultiplied
/// @param outlineWidth  ring thickness, drawn outside the edge
/// @param outlineColor  `0xAARRGGBB`, not premultiplied
/// @param outlineOffset the gap between the box's edge and the inside of the ring
/// @param shadow        the drop shadow cast behind the box, [Shadow#NONE] for
///                      the overwhelming majority of boxes
public record Decoration(
        Corners corners,
        double borderWidth,
        int borderColor,
        double outlineWidth,
        int outlineColor,
        double outlineOffset,
        Shadow shadow) {

    /// Square corners, no border, no ring, no shadow — what every box starts as.
    public static final Decoration NONE =
            new Decoration(Corners.SQUARE, 0, CssColor.TRANSPARENT, 0, CssColor.TRANSPARENT, 0, Shadow.NONE);

    public Decoration {
        // Clamped rather than refused. These arrive from a stylesheet, and §8's
        // rule for a bad declaration is to drop it and carry on — a negative
        // radius should not take a window down mid-frame. The corners clamp
        // themselves, in [Corners], for the same reason.
        java.util.Objects.requireNonNull(corners, "corners");
        java.util.Objects.requireNonNull(shadow, "shadow");
        borderWidth = Math.max(0, finite(borderWidth, "border-width"));
        outlineWidth = Math.max(0, finite(outlineWidth, "outline-width"));
        outlineOffset = finite(outlineOffset, "outline-offset");
    }

    /// Whether a border would put ink on the screen.
    ///
    /// Both halves matter: a 1px border in a fully transparent colour draws
    /// nothing, and so does a 0px one in `--gb-border`. Asked before building a
    /// path, because building one costs a native allocation.
    public boolean hasBorder() {
        return borderWidth > 0 && (borderColor >>> 24) != 0;
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
        return new Decoration(value, borderWidth, borderColor, outlineWidth, outlineColor, outlineOffset, shadow);
    }

    public Decoration border(double width, int argb) {
        return new Decoration(corners, width, argb, outlineWidth, outlineColor, outlineOffset, shadow);
    }

    public Decoration borderWidth(double value) {
        return new Decoration(corners, value, borderColor, outlineWidth, outlineColor, outlineOffset, shadow);
    }

    public Decoration borderColor(int argb) {
        return new Decoration(corners, borderWidth, argb, outlineWidth, outlineColor, outlineOffset, shadow);
    }

    public Decoration outline(double width, int argb, double offset) {
        return new Decoration(corners, borderWidth, borderColor, width, argb, offset, shadow);
    }

    public Decoration outlineWidth(double value) {
        return new Decoration(corners, borderWidth, borderColor, value, outlineColor, outlineOffset, shadow);
    }

    public Decoration outlineColor(int argb) {
        return new Decoration(corners, borderWidth, borderColor, outlineWidth, argb, outlineOffset, shadow);
    }

    public Decoration outlineOffset(double value) {
        return new Decoration(corners, borderWidth, borderColor, outlineWidth, outlineColor, value, shadow);
    }

    public Decoration shadow(Shadow value) {
        return new Decoration(corners, borderWidth, borderColor, outlineWidth, outlineColor, outlineOffset, value);
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
                borderWidth,
                CssColor.fade(borderColor, alpha),
                outlineWidth,
                CssColor.fade(outlineColor, alpha),
                outlineOffset,
                shadow.fade(alpha));
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, not " + value);
        }
        return value;
    }
}
