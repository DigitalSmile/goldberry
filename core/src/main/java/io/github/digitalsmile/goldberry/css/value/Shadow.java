package io.github.digitalsmile.goldberry.css.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.css.parse.Token;
import io.github.digitalsmile.goldberry.css.parse.TokenType;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.log.Logs;

/// CSS's `box-shadow` — one drop shadow cast by a box.
///
/// ```css
/// box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
/// box-shadow: var(--gb-elevation-2);
/// ```
///
/// ## One shadow, not a list
///
/// CSS takes a comma-separated list and paints them back to front. This takes
/// **one**, and a list is read as its first entry with the rest logged and
/// dropped. Two reasons, and neither is that a list is hard to draw:
///
/// - Every shadow the design system pins is one shadow (`docs/design-system.md`
///   §1.5), and every one a theme ships is a single `--gb-elevation-*` token —
///   so a list has no author in this repository.
/// - The list idiom exists mostly to fake a blur profile a real Gaussian gives
///   you for free (`0 1px 2px, 0 2px 8px`), and this *is* a ramp already. Two
///   ramps stacked is two ramps' worth of fills for a difference nothing here
///   asks for.
///
/// The first entry rather than a refusal for the reason `border: 1px dashed red`
/// draws a solid line instead of nothing: drawing something is the more useful of
/// the two wrong answers, and it is logged so "my second shadow is missing" has
/// an answer (ADR-0310).
///
/// ## What is refused
///
/// `inset` — an inner shadow is a different drawing (it is clipped *to* the
/// border box rather than cast outside it) and no rule in the canon asks for one.
/// A shadow with **no colour** is refused too: CSS's default is `currentColor`,
/// which §8's subset does not have, and guessing black would paint a hard black
/// halo where an author meant a tinted one.
///
/// ## Units and geometry
///
/// Logical pixels, resolved — the same rule [io.github.digitalsmile.goldberry.css.Decoration]
/// states. Percentages are refused: a percentage offset means "of this box's
/// size", and a box has no size until Yoga has run.
///
/// The shape cast is the **border box**, moved by `(offsetX, offsetY)` and grown
/// on every side by `spread`, with its corner radii grown to match. `blur` is
/// CSS's blur *radius*: the edge fades from opaque to nothing across it, centred
/// on the shape's edge, so the shadow reaches `blur / 2` beyond the spread shape
/// and no further.
///
/// @param offsetX how far right the shadow is cast; negative is left
/// @param offsetY how far down the shadow is cast; negative is up
/// @param blur    the blur radius, never negative — the *whole* width of the
///                fade, half of it outside the shape and half inside
/// @param spread  how much bigger than the box the shape is; negative shrinks it
/// @param argb    `0xAARRGGBB`, not premultiplied
public record Shadow(double offsetX, double offsetY, double blur, double spread, int argb) {

    private static final Logger LOG = Logs.of(Shadow.class);

    /// No shadow at all — what every box starts as.
    public static final Shadow NONE = new Shadow(0, 0, 0, 0, CssColor.TRANSPARENT);

    public Shadow {
        offsetX = finite(offsetX, "a shadow's x offset");
        offsetY = finite(offsetY, "a shadow's y offset");
        spread = finite(spread, "a shadow's spread");
        // Clamped rather than refused, for [Decoration]'s reason: these arrive
        // from a stylesheet, and §8's rule for a bad declaration is to drop it
        // and carry on. The parser refuses a negative blur where it is written,
        // which is where an author can be told about it.
        blur = Math.max(0, finite(blur, "a shadow's blur radius"));
    }

    /// Whether this shadow would put ink on the screen.
    ///
    /// Asked before any geometry is built, because a shadow costs a run of fills
    /// and a fully transparent one costs the same run for nothing.
    public boolean hasInk() {
        return (argb >>> 24) != 0;
    }

    /// How far past the spread shape's edge the fade reaches — half the blur
    /// radius, per CSS.
    public double reach() {
        return blur / 2;
    }

    /// How far past the box's **left** edge this shadow is drawn.
    ///
    /// The four are separate because a shadow is asymmetric by construction: `0
    /// 8px 32px` reaches 24px below the box and 8px above it, and a single
    /// outset would repaint the larger of the two on all four sides. What reads
    /// them is the damage rectangle in
    /// [io.github.digitalsmile.goldberry.paint.tree.RenderTree], where painting
    /// one pixel outside the region declared dirty leaves a smear that survives
    /// until something else repaints over it.
    public double outsetLeft() {
        return outset(-offsetX);
    }

    public double outsetTop() {
        return outset(-offsetY);
    }

    public double outsetRight() {
        return outset(offsetX);
    }

    public double outsetBottom() {
        return outset(offsetY);
    }

    private double outset(double towards) {
        return hasInk() ? Math.max(0, towards + spread + reach()) : 0;
    }

    /// This shadow with its colour's alpha scaled by `alpha`.
    ///
    /// How `opacity` reaches a shadow — see [io.github.digitalsmile.goldberry.paint.Box#fade(double)].
    public Shadow fade(double alpha) {
        return alpha >= 1 ? this : new Shadow(offsetX, offsetY, blur, spread, CssColor.fade(argb, alpha));
    }

    /// This shadow `t` of the way to `to`, for `transition: box-shadow`.
    ///
    /// Every component interpolates, colour included, which is CSS's own rule
    /// for a shadow pair of equal length — and a pair here is always of equal
    /// length, because there is only ever one.
    ///
    /// The one asymmetry is [#NONE]: interpolating *from* no shadow would ramp
    /// the geometry up from zero as well as the alpha, so a card growing its
    /// elevation would appear to inflate. CSS says an absent shadow interpolates
    /// as the other one at zero alpha, and that is what this does — the shape
    /// arrives at full size and fades in.
    public Shadow mix(Shadow to, double t) {
        var from = this;
        if (!from.hasInk() && to.hasInk()) {
            from = new Shadow(to.offsetX, to.offsetY, to.blur, to.spread, to.argb & 0x00FFFFFF);
        } else if (from.hasInk() && !to.hasInk()) {
            to = new Shadow(from.offsetX, from.offsetY, from.blur, from.spread, from.argb & 0x00FFFFFF);
        }
        return new Shadow(
                lerp(from.offsetX, to.offsetX, t),
                lerp(from.offsetY, to.offsetY, t),
                lerp(from.blur, to.blur, t),
                lerp(from.spread, to.spread, t),
                CssColor.mix(from.argb, to.argb, t));
    }

    /// Parses a `box-shadow` value.
    ///
    /// `<x> <y> [<blur>] [<spread>] <color>`, in CSS's order for the lengths and
    /// with the colour anywhere among them — `red 0 2px 4px` is the same
    /// declaration as `0 2px 4px red`, which is CSS's rule and is how a great
    /// many stylesheets are written.
    ///
    /// @return the shadow, or null if these tokens are not one
    public static @Nullable Shadow parse(List<Token> value, CssLength.Context context) {
        var entries = commaSeparated(value);
        if (entries.size() > 1) {
            LOG.debug(
                    "painting the first of {} shadows: box-shadow takes one here, and the rest are dropped",
                    entries.size());
        }
        var first = entries.getFirst();
        if (first.size() == 1 && first.getFirst().isIdent("none")) {
            return NONE;
        }
        return one(first, context);
    }

    /// One entry of the list — the whole of the value in every stylesheet that
    /// exists.
    private static @Nullable Shadow one(List<Token> entry, CssLength.Context context) {
        var lengths = new ArrayList<Double>();
        Integer argb = null;

        for (var part : parts(entry)) {
            if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
                var keyword = part.getFirst().text().toLowerCase(Locale.ROOT);
                if (keyword.equals("inset")) {
                    LOG.warn("dropping box-shadow: an `inset` shadow is not in the subset");
                    return null;
                }
            }
            var length = points(part, context);
            if (length != null) {
                if (lengths.size() == 4) {
                    return null;
                }
                lengths.add(length);
                continue;
            }
            var colour = CssColor.parse(part);
            if (colour == null || argb != null) {
                return null;
            }
            argb = colour;
        }

        if (lengths.size() < 2) {
            return null;
        }
        // No `currentColor` in the subset, and no guess: see the class note.
        if (argb == null) {
            LOG.warn("dropping box-shadow: it names no colour, and there is no `currentColor` to fall back on");
            return null;
        }
        var blur = lengths.size() > 2 ? lengths.get(2) : 0;
        if (blur < 0) {
            // CSS says so, and it is worth saying out loud: a negative blur is
            // the one part of this value that is an error rather than an effect.
            LOG.warn("dropping box-shadow: a blur radius may not be negative, and {} is", blur);
            return null;
        }
        return new Shadow(lengths.get(0), lengths.get(1), blur, lengths.size() > 3 ? lengths.get(3) : 0, argb);
    }

    /// A length in logical pixels, or null when this is not one.
    ///
    /// A percentage resolves through [CssLength] into
    /// [Length.Percent], which is not a
    /// number of pixels — so it falls out here rather than being carried, which
    /// is the same refusal `border-radius` makes.
    private static @Nullable Double points(List<Token> part, CssLength.Context context) {
        return CssLength.parse(part, context) instanceof Length.Points points ? (double) points.value() : null;
    }

    /// Splits on commas, into at least one entry.
    private static List<List<Token>> commaSeparated(List<Token> value) {
        var entries = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            depth = depth(token, depth);
            if (depth == 0 && token.is(TokenType.COMMA)) {
                entries.add(List.copyOf(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        entries.add(List.copyOf(current));
        return entries;
    }

    /// Splits one entry on whitespace, **not inside a function**.
    ///
    /// The same rule [io.github.digitalsmile.goldberry.css.ComputedStyle]'s own
    /// splitter follows, and for the same reason: the spaces in `rgba(0, 0, 0,
    /// 0.25)` belong to the function, and splitting on them hands `CssColor.parse`
    /// the fragment `rgba(0,` — which parses as nothing, so the whole declaration
    /// is dropped. Every elevation token in both themes is an `rgba()`, so this
    /// is not a corner case here; it is the only case.
    private static List<List<Token>> parts(List<Token> entry) {
        var parts = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : entry) {
            depth = depth(token, depth);
            if (depth == 0 && token.is(TokenType.WHITESPACE)) {
                if (!current.isEmpty()) {
                    parts.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty()) {
            parts.add(List.copyOf(current));
        }
        return parts;
    }

    private static int depth(Token token, int depth) {
        if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
            return depth + 1;
        }
        if (token.is(TokenType.CLOSE_PAREN)) {
            return Math.max(0, depth - 1);
        }
        return depth;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, not " + value);
        }
        return value;
    }

    @Override
    public String toString() {
        if (!hasInk()) {
            return "none";
        }
        var text = new StringBuilder()
                .append(px(offsetX))
                .append(' ')
                .append(px(offsetY))
                .append(' ')
                .append(px(blur));
        if (spread != 0) {
            text.append(' ').append(px(spread));
        }
        return text.append(' ').append(String.format("#%08x", argb)).toString();
    }

    private static String px(double value) {
        return value == Math.rint(value) ? (long) value + "px" : value + "px";
    }
}
