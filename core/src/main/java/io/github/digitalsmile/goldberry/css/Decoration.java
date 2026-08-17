package io.github.digitalsmile.goldberry.css;

/// What is drawn *around* a box rather than in it: the corner radius, the border
/// and the focus ring.
///
/// One record rather than six components on [ComputedStyle] and six more on
/// [io.github.digitalsmile.goldberry.layout.Box], because they are only ever read
/// together — the painter that draws a border needs the radius to draw it along,
/// and the ring needs both to sit outside them. Splitting them would put six
/// arguments through every constructor call in the cascade for no reader's
/// benefit.
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
/// @param radius        corner radius; 0 is a square corner
/// @param borderWidth   border thickness, drawn *inside* the box's edge as CSS's
///                      `border-box` sizing requires
/// @param borderColor   `0xAARRGGBB`, not premultiplied
/// @param outlineWidth  ring thickness, drawn outside the edge
/// @param outlineColor  `0xAARRGGBB`, not premultiplied
/// @param outlineOffset the gap between the box's edge and the inside of the ring
public record Decoration(
        double radius,
        double borderWidth,
        int borderColor,
        double outlineWidth,
        int outlineColor,
        double outlineOffset) {

    /// Square corners, no border, no ring — what every box starts as.
    public static final Decoration NONE =
            new Decoration(0, 0, CssColor.TRANSPARENT, 0, CssColor.TRANSPARENT, 0);

    public Decoration {
        // Clamped rather than refused. These arrive from a stylesheet, and §8's
        // rule for a bad declaration is to drop it and carry on — a negative
        // radius should not take a window down mid-frame.
        radius = Math.max(0, finite(radius, "radius"));
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

    /// Whether this is [#NONE] in effect — nothing to draw and nothing to round.
    public boolean isPlain() {
        return radius == 0 && !hasBorder() && !hasOutline();
    }

    public Decoration radius(double value) {
        return new Decoration(value, borderWidth, borderColor, outlineWidth, outlineColor,
                outlineOffset);
    }

    public Decoration border(double width, int argb) {
        return new Decoration(radius, width, argb, outlineWidth, outlineColor, outlineOffset);
    }

    public Decoration borderWidth(double value) {
        return new Decoration(radius, value, borderColor, outlineWidth, outlineColor, outlineOffset);
    }

    public Decoration borderColor(int argb) {
        return new Decoration(radius, borderWidth, argb, outlineWidth, outlineColor, outlineOffset);
    }

    public Decoration outline(double width, int argb, double offset) {
        return new Decoration(radius, borderWidth, borderColor, width, argb, offset);
    }

    public Decoration outlineWidth(double value) {
        return new Decoration(radius, borderWidth, borderColor, value, outlineColor, outlineOffset);
    }

    public Decoration outlineColor(int argb) {
        return new Decoration(radius, borderWidth, borderColor, outlineWidth, argb, outlineOffset);
    }

    public Decoration outlineOffset(double value) {
        return new Decoration(radius, borderWidth, borderColor, outlineWidth, outlineColor, value);
    }

    /// This decoration with every colour's alpha scaled by `alpha`.
    ///
    /// How `opacity` reaches a border and a ring — see
    /// [io.github.digitalsmile.goldberry.layout.Box#fade(double)], which is where
    /// the reasoning for multiplying alpha rather than compositing a layer is
    /// written down.
    public Decoration fade(double alpha) {
        if (alpha >= 1) {
            return this;
        }
        return new Decoration(radius, borderWidth, CssColor.fade(borderColor, alpha),
                outlineWidth, CssColor.fade(outlineColor, alpha), outlineOffset);
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, not " + value);
        }
        return value;
    }
}
