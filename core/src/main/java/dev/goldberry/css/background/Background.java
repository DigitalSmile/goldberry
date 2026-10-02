package dev.goldberry.css.background;

import java.util.List;
import java.util.Objects;

import dev.goldberry.css.value.CssColor;

/// What a box is filled with — CSS's `background`: a colour, and gradients
/// painted over it.
///
/// ```css
/// card { background: var(--gb-surface-1) }
/// .glow { background: radial-gradient(circle at top, #88c0d040, transparent 60%), var(--gb-surface-0) }
/// .running { background: repeating-linear-gradient(90deg, #a3be8c 0 6px, transparent 6px 12px) }
/// ```
///
/// Sealed, with two shapes. [Colour] is every box the toolkit draws and costs
/// what an `int` did; [Layers] is a box with gradients on it, or one whose
/// `background-position` has been moved. Both answer [#colour()], so a reader
/// that only wants the colour — contrast checks, a transition of
/// `background-color` — never asks which it has.
///
/// ## Layers
///
/// The [#layers()] are painted **last first**, over the colour, the way CSS
/// paints a comma list: the first layer written is the one on top. Each is a
/// [GradientLayer], filled over the box's border box with its corners.
///
/// ## What is not here
///
/// There are no images (`url()`), no `background-size`, `background-repeat`,
/// `background-clip` or `background-origin`. A gradient is laid over the
/// whole box, once, which is what each of those properties says when it is
/// left at its initial value. `background-position` moves every layer by the
/// lengths it names: with a layer the size of the box, a percentage or a
/// keyword moves it by nothing, and does here.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public sealed interface Background {

    /// No fill at all — what every box starts as.
    static Background none() {
        return Colour.NONE;
    }

    /// The colour under every layer, `0xAARRGGBB`, not premultiplied.
    int colour();

    /// The gradient layers, top first; empty for a plain colour.
    List<GradientLayer> layers();

    /// How far `background-position` moves every layer.
    BackgroundPosition position();

    /// A plain colour.
    static Background of(int argb) {
        return new Colour(argb);
    }

    /// A colour with `layers` over it, moved by `position` — or a plain
    /// [Colour] when there are no layers and nothing is moved.
    static Background of(int argb, List<GradientLayer> layers, BackgroundPosition position) {
        if (layers.isEmpty() && position.equals(BackgroundPosition.ZERO)) {
            return new Colour(argb);
        }
        return new Layers(argb, layers, position);
    }

    /// Whether anything would be drawn.
    default boolean hasInk() {
        return (colour() >>> 24) != 0 || !layers().isEmpty();
    }

    /// This background with its colour replaced and its layers kept — what
    /// `background-color` does.
    default Background colour(int argb) {
        return of(argb, layers(), position());
    }

    /// This background with its layers replaced — `background-image`.
    default Background layers(List<GradientLayer> value) {
        return of(colour(), value, position());
    }

    /// This background moved — `background-position`.
    default Background position(BackgroundPosition value) {
        return of(colour(), layers(), value);
    }

    /// This background with every colour's alpha scaled by `alpha`, which is
    /// how `opacity` reaches it.
    Background fade(double alpha);

    /// A box filled with one colour.
    ///
    /// @param colour `0xAARRGGBB`, not premultiplied
    record Colour(int colour) implements Background {

        /// Transparent: no fill at all.
        static final Colour NONE = new Colour(CssColor.TRANSPARENT);

        @Override
        public List<GradientLayer> layers() {
            return List.of();
        }

        @Override
        public BackgroundPosition position() {
            return BackgroundPosition.ZERO;
        }

        @Override
        public Background fade(double alpha) {
            return alpha >= 1 ? this : new Colour(CssColor.fade(colour, alpha));
        }
    }

    /// A colour with gradients over it.
    ///
    /// @param colour   painted first, under every layer
    /// @param layers   top first
    /// @param position how far every layer is moved
    record Layers(int colour, List<GradientLayer> layers, BackgroundPosition position) implements Background {

        public Layers {
            layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
            Objects.requireNonNull(position, "position");
        }

        @Override
        public Background fade(double alpha) {
            if (alpha >= 1) {
                return this;
            }
            return new Layers(
                    CssColor.fade(colour, alpha),
                    layers.stream().map(layer -> layer.fade(alpha)).toList(),
                    position);
        }
    }
}
