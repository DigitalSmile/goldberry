package io.github.digitalsmile.goldberry.widgets.controls.spinner;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// How big a [Spinner] is — `docs/core-widgets.md` §3's `size`.
///
/// ## Why this is a value and not only a class
///
/// [io.github.digitalsmile.goldberry.widgets.overlay.message.Message] draws the
/// line: a variant that is **only a skin** should be a class rather than a
/// second vocabulary only Java can write, which is why
/// [io.github.digitalsmile.goldberry.widgets.controls.badge.Badge] spells its
/// variants as classes. A spinner's size is not only a skin, for the reason a
/// `message`'s kind is not: it decides something the stylesheet cannot say.
///
/// A spinner is a **ring**, and a ring has a stroke. §8's CSS subset has no
/// property for the weight of a mark the painter draws — `Box.Mark` carries it
/// as a number — so a 32px spinner drawn with a 16px spinner's 2px stroke is a
/// thin hoop, and nothing in a stylesheet could have fixed it.
///
/// ## The diameter is still the stylesheet's
///
/// Each size carries a **nominal** diameter, and it is a fallback rather than
/// the truth: `controls.css` sets `width` and `height` per class, an application
/// overrides them the ordinary way, and [Spinner] reads the width the cascade
/// actually resolved. The number here is what a spinner falls back to when that
/// width is `auto` or a percentage — a length no ring can be drawn from.
///
/// That is what keeps the two from drifting. A stylesheet that says `48px` gets
/// a stroke weighted for 48px without this enum being touched.
public enum SpinnerSize {

    /// Beside a line of text, or inside a control that is busy. Small enough
    /// that §1.6's 2px line weight would be half the ring.
    SMALL(12),

    /// The default, and the one every existing `spinner` already is: §3's
    /// "small indeterminate activity indicator" at 16px.
    MEDIUM(16),

    /// On its own, standing for a whole region that is not ready — a pane, a
    /// tab, the box a `web-view`'s page will occupy ([ADR-0445]). At this size
    /// it is the only thing in the box, so it is read as the subject rather than
    /// as a decoration on something else.
    LARGE(32);

    /// The stroke, as a fraction of the diameter.
    ///
    /// One ratio for every size, so a large spinner is a big picture of a small
    /// one rather than a different shape. It is **exactly** `2 / 16` — today's
    /// 2px stroke at today's 16px — which is what makes [#MEDIUM] render
    /// identically to every spinner drawn before this enum existed, goldens
    /// included.
    static final double THICKNESS_RATIO = 2.0 / 16.0;

    private final double diameter;

    SpinnerSize(double diameter) {
        this.diameter = diameter;
    }

    /// The diameter this size is drawn at when the cascade resolved no usable
    /// width, in logical pixels. See the class note: `controls.css` is where the
    /// real number lives.
    public double diameter() {
        return diameter;
    }

    /// The stroke for a ring of `diameter`, never thinner than a pixel.
    ///
    /// Takes the diameter rather than reading [#diameter()], because the size
    /// that matters is the one the cascade resolved.
    public static double thicknessFor(double diameter) {
        return Math.max(1, diameter * THICKNESS_RATIO);
    }

    /// The class this size puts on the node — `spinner.large`.
    ///
    /// Always present, including for [#MEDIUM], so an application can select a
    /// size without knowing which one the toolkit calls the default.
    /// `controls.css` writes a rule only for the two that are not it.
    public String cssClass() {
        return name().toLowerCase(Locale.ROOT);
    }

    /// Reads `size="large"` from markup.
    ///
    /// Absent is [#MEDIUM] — a spinner with no size is still a spinner, and
    /// refusing to build one would take a window down over a missing word. A
    /// size that is *misspelt* is refused, because that is a document saying
    /// something it does not mean. `Message.Kind.of` draws the same line.
    public static SpinnerSize of(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "a spinner's size is \"small\", \"medium\" or \"large\", not \"" + text + "\"", e);
        }
    }
}
