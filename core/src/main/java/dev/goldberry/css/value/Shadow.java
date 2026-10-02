package dev.goldberry.css.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.layout.Length;
import dev.goldberry.log.Logs;

/// One entry of CSS's `box-shadow` — a drop shadow cast by a box, or an inner
/// shadow cast inside it.
///
/// ```css
/// box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
/// box-shadow: var(--gb-elevation-2);
/// box-shadow: inset 3px 0 0 var(--gb-accent), 0 1px 2px rgba(0, 0, 0, 0.2);
/// ```
///
/// ## A list
///
/// The property is a comma-separated list, and [#parse] returns all of it. The
/// first shadow in the list is drawn on top, as CSS says: outer shadows are
/// painted under the background, last first, and inner ones over the background
/// and under the border, last first.
///
/// ## Inset
///
/// An `inset` shadow is cast **inside** the padding box: the box's own shape,
/// moved by the offset and shrunk by the spread, is the hole the shadow is seen
/// around. It reaches nothing outside the box, so its outsets are zero.
///
/// ## What is refused
///
/// A shadow with **no colour**: CSS's default is `currentColor`, which the
/// subset does not have, and guessing black would paint a hard black halo where
/// an author meant a tinted one. One bad entry drops the whole declaration, as
/// CSS drops it.
///
/// ## Units and geometry
///
/// Logical pixels, resolved — the same rule [dev.goldberry.css.Decoration]
/// states. Percentages are refused: a percentage offset means "of this box's
/// size", and a box has no size until layout has run.
///
/// The shape cast is the **border box**, moved by `(offsetX, offsetY)` and grown
/// on every side by `spread`, with its corner radii grown to match. `blur` is
/// CSS's blur *radius*: the edge fades from opaque to nothing across it, centred
/// on the shape's edge, so the shadow reaches `blur / 2` beyond the spread shape
/// and no further. A shadow is drawn as a stack of rounded rectangles whose
/// alphas ramp across the blur, which is what a rasterizer with no blur can do.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
///
/// @param offsetX how far right the shadow is cast; negative is left
/// @param offsetY how far down the shadow is cast; negative is up
/// @param blur    the blur radius, never negative — the *whole* width of the
///                fade, half of it outside the shape and half inside
/// @param spread  how much bigger than the box the shape is; negative shrinks it.
///                For an inset shadow, how much smaller the hole is
/// @param argb    `0xAARRGGBB`, not premultiplied
/// @param inset   whether this is cast inside the box rather than behind it
public record Shadow(double offsetX, double offsetY, double blur, double spread, int argb, boolean inset) {

    private static final Logger LOG = Logs.of(Shadow.class);

    /// No shadow at all.
    public static final Shadow NONE = new Shadow(0, 0, 0, 0, CssColor.TRANSPARENT);

    /// A drop shadow, cast behind the box.
    public Shadow(double offsetX, double offsetY, double blur, double spread, int argb) {
        this(offsetX, offsetY, blur, spread, argb, false);
    }

    public Shadow {
        requireFinite(offsetX, "a shadow's x offset");
        requireFinite(offsetY, "a shadow's y offset");
        requireFinite(spread, "a shadow's spread");
        // Clamped rather than refused, for [Decoration]'s reason: these arrive
        // from a stylesheet, and the rule for a bad declaration is to drop it
        // and carry on. The parser refuses a negative blur where it is written,
        // which is where an author can be told about it.
        requireFinite(blur, "a shadow's blur radius");
        blur = Math.max(0, blur);
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
    /// [dev.goldberry.paint.tree.RenderTree], where painting
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
        // An inner shadow is drawn inside the box and reaches nothing past it.
        return hasInk() && !inset ? Math.max(0, towards + spread + reach()) : 0;
    }

    /// This shadow with its colour's alpha scaled by `alpha`.
    ///
    /// How `opacity` reaches a shadow — see [dev.goldberry.paint.Box#fade(double)].
    public Shadow fade(double alpha) {
        return alpha >= 1 ? this : new Shadow(offsetX, offsetY, blur, spread, CssColor.fade(argb, alpha), inset);
    }

    /// This shadow `t` of the way to `to`, for `transition: box-shadow`.
    ///
    /// Every component interpolates, colour included, which is CSS's own rule
    /// for a pair of shadows. An inner shadow and an outer one do not
    /// interpolate: the pair swaps half-way, as CSS's discrete rule says.
    ///
    /// The one asymmetry is [#NONE]: interpolating *from* no shadow would ramp
    /// the geometry up from zero as well as the alpha, so a card growing its
    /// elevation would appear to inflate. CSS says an absent shadow interpolates
    /// as the other one at zero alpha, and that is what this does — the shape
    /// arrives at full size and fades in.
    public Shadow mix(Shadow to, double t) {
        var from = this;
        if (!from.hasInk() && to.hasInk()) {
            from = to.transparent();
        } else if (from.hasInk() && !to.hasInk()) {
            to = from.transparent();
        }
        if (from.inset != to.inset) {
            return t < 0.5 ? from : to;
        }
        return new Shadow(
                Interpolate.lerp(from.offsetX, to.offsetX, t),
                Interpolate.lerp(from.offsetY, to.offsetY, t),
                Interpolate.lerp(from.blur, to.blur, t),
                Interpolate.lerp(from.spread, to.spread, t),
                CssColor.mix(from.argb, to.argb, t),
                to.inset);
    }

    /// The same shadow at zero alpha — what an absent one interpolates as.
    private Shadow transparent() {
        return new Shadow(offsetX, offsetY, blur, spread, argb & 0x00FFFFFF, inset);
    }

    /// A list of shadows `t` of the way to another, for `transition:
    /// box-shadow`.
    ///
    /// Pair by pair. The shorter list is padded with the longer one's own
    /// shadows at zero alpha, so a shadow that arrives fades in at its full
    /// size, as [#mix(Shadow, double)] does for one. A pair of an inner shadow
    /// and an outer one makes the whole list swap half-way, CSS's rule for a
    /// list that cannot interpolate.
    public static List<Shadow> mix(List<Shadow> from, List<Shadow> to, double t) {
        var size = Math.max(from.size(), to.size());
        var mixed = new ArrayList<Shadow>(size);
        for (var i = 0; i < size; i++) {
            var a = i < from.size() ? from.get(i) : to.get(i).transparent();
            var b = i < to.size() ? to.get(i) : from.get(i).transparent();
            if (a.inset != b.inset) {
                return t < 0.5 ? from : to;
            }
            mixed.add(a.mix(b, t));
        }
        return List.copyOf(mixed);
    }

    /// Parses a `box-shadow` value.
    ///
    /// A comma-separated list of `[inset] <x> <y> [<blur>] [<spread>] <color>`,
    /// in CSS's order for the lengths, with the colour and `inset` anywhere
    /// among them — `red 0 2px 4px` is the same declaration as `0 2px 4px red`,
    /// which is CSS's rule and is how a great many stylesheets are written.
    ///
    /// @return the shadows, first on top; empty for `none`; null if these tokens
    ///         are not a list of shadows
    public static @Nullable List<Shadow> parse(List<Token> value, CssLength.Context context) {
        var entries = commaSeparated(value);
        if (entries.size() == 1) {
            var only = withoutWhitespace(entries.getFirst());
            if (only.size() == 1 && only.getFirst().isIdent("none")) {
                return List.of();
            }
        }
        var shadows = new ArrayList<Shadow>(entries.size());
        for (var entry : entries) {
            var shadow = one(entry, context);
            if (shadow == null) {
                return null;
            }
            shadows.add(shadow);
        }
        return List.copyOf(shadows);
    }

    private static List<Token> withoutWhitespace(List<Token> tokens) {
        return tokens.stream().filter(token -> !token.is(TokenType.WHITESPACE)).toList();
    }

    /// One entry of the list.
    private static @Nullable Shadow one(List<Token> entry, CssLength.Context context) {
        var lengths = new ArrayList<Double>();
        Integer argb = null;
        var inset = false;

        for (var part : parts(entry)) {
            if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
                var keyword = part.getFirst().text().toLowerCase(Locale.ROOT);
                if (keyword.equals("inset")) {
                    if (inset) {
                        return null;
                    }
                    inset = true;
                    continue;
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
        return new Shadow(lengths.get(0), lengths.get(1), blur, lengths.size() > 3 ? lengths.get(3) : 0, argb, inset);
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
    /// The same rule [dev.goldberry.css.ComputedStyle]'s own
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

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, not " + value);
        }
    }

    @Override
    public String toString() {
        if (!hasInk()) {
            return "none";
        }
        var text = new StringBuilder();
        if (inset) {
            text.append("inset ");
        }
        text.append(px(offsetX)).append(' ').append(px(offsetY)).append(' ').append(px(blur));
        if (spread != 0) {
            text.append(' ').append(px(spread));
        }
        return text.append(' ').append(hex(argb)).toString();
    }

    /// The colour as CSS writes it — `#rrggbb` when it is opaque, `#rrggbbaa`
    /// when it is not.
    ///
    /// **Not `#%08x` of the packed value.** That prints `#aarrggbb`, and CSS reads
    /// eight hex digits as `#rrggbbaa` — so `Shadow.parse(shadow.toString())`
    /// would come back with the alpha read as red: `0x40000000` printed as
    /// `#40000000` parses to alpha `0x00` and is `Shadow.NONE`. A value whose
    /// `toString` does not round-trip through its own parser is a value that
    /// cannot be written into a stylesheet, which is what this one is for.
    private static String hex(int argb) {
        var alpha = (argb >>> 24) & 0xFF;
        var rgb = String.format("#%06x", argb & 0xFFFFFF);
        return alpha == 0xFF ? rgb : rgb + String.format("%02x", alpha);
    }

    private static String px(double value) {
        return value == Math.rint(value) ? (long) value + "px" : value + "px";
    }
}
