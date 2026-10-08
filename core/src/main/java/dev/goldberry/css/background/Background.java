package dev.goldberry.css.background;

import java.util.List;
import java.util.Objects;

import dev.goldberry.css.image.CssImage;
import dev.goldberry.css.value.CssColor;

/// What a box is filled with — CSS's `background`: a colour, and gradients and
/// pictures painted over it.
///
/// ```css
/// card { background: var(--gb-surface-1) }
/// .glow { background: radial-gradient(circle at top, #88c0d040, transparent 60%), var(--gb-surface-0) }
/// .running { background: repeating-linear-gradient(90deg, #a3be8c 0 6px, transparent 6px 12px) }
/// .panel { background: url("classpath:/ui/leather.png") repeat, #3b2a1e }
/// ```
///
/// Sealed, with two shapes. [Colour] is every box the toolkit draws and costs
/// what an `int` did; [Layers] is a box with gradients or pictures on it, or one
/// whose `background-position`, `-size` or `-repeat` has been set. Both answer
/// [#colour()], so a reader that only wants the colour — contrast checks, a
/// transition of `background-color` — never asks which it has.
///
/// ## Layers
///
/// The [#layers()] are painted **last first**, over the colour, the way CSS
/// paints a comma list: the first layer written is the one on top. Each is a
/// [CssImage]: a [GradientLayer], filled over the box's border box, or a
/// `url()` picture, drawn at its [BackgroundSize] and tiled as its
/// [BackgroundRepeat] says. Either is clipped to the box's corners.
///
/// `background-size` and `background-repeat` are comma lists matched to the
/// layers by position, and a list shorter than the layers is repeated, as CSS
/// repeats it: [#size(int)] and [#repeat(int)] answer for one layer.
///
/// ## What is not here
///
/// There is no `background-clip`, `background-origin` or
/// `background-attachment`: a layer is placed in and clipped to the border box,
/// which is what each says at its initial value. `background-position` is one
/// offset for every layer, and only a length moves one: a percentage or a
/// keyword is the top left corner.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public sealed interface Background {

    /// Every layer at its natural size: CSS's initial `background-size`.
    List<BackgroundSize> AUTO_SIZE = List.of(BackgroundSize.AUTO);

    /// Every layer tiled: CSS's initial `background-repeat`.
    List<BackgroundRepeat> REPEAT = List.of(BackgroundRepeat.REPEAT);

    /// No fill at all — what every box starts as.
    static Background none() {
        return Colour.NONE;
    }

    /// The colour under every layer, `0xAARRGGBB`, not premultiplied.
    int colour();

    /// The layers, top first; empty for a plain colour.
    List<CssImage> layers();

    /// How far `background-position` moves every layer.
    BackgroundPosition position();

    /// The `background-size` list, matched to the layers by [#size(int)].
    List<BackgroundSize> sizes();

    /// The `background-repeat` list, matched to the layers by [#repeat(int)].
    List<BackgroundRepeat> repeats();

    /// A plain colour.
    static Background of(int argb) {
        return new Colour(argb);
    }

    /// A colour with `layers` over it, moved by `position` — or a plain
    /// [Colour] when there are no layers and nothing is moved.
    static Background of(int argb, List<? extends CssImage> layers, BackgroundPosition position) {
        return of(argb, layers, position, AUTO_SIZE, REPEAT);
    }

    /// A colour with `layers` over it, moved, sized and tiled — or a plain
    /// [Colour] when every one of those is at its initial value.
    ///
    /// The lists are kept when there are no layers, so a `background-size`
    /// applied before the `background-image` it sizes is not lost.
    static Background of(
            int argb,
            List<? extends CssImage> layers,
            BackgroundPosition position,
            List<BackgroundSize> sizes,
            List<BackgroundRepeat> repeats) {
        if (layers.isEmpty()
                && position.equals(BackgroundPosition.ZERO)
                && sizes.equals(AUTO_SIZE)
                && repeats.equals(REPEAT)) {
            return new Colour(argb);
        }
        return new Layers(argb, List.copyOf(layers), position, sizes, repeats);
    }

    /// Whether anything would be drawn.
    default boolean hasInk() {
        return (colour() >>> 24) != 0 || !layers().isEmpty();
    }

    /// Whether a layer is a picture, whose pixels arrive after the box does.
    default boolean hasPictures() {
        for (var layer : layers()) {
            if (layer instanceof CssImage.Url) {
                return true;
            }
        }
        return false;
    }

    /// The size of layer `index`: the list's entry, repeated when the list is
    /// shorter than the layers.
    default BackgroundSize size(int index) {
        var list = sizes();
        return list.get(index % list.size());
    }

    /// Whether layer `index` is tiled: the list's entry, repeated when the list is
    /// shorter than the layers.
    default BackgroundRepeat repeat(int index) {
        var list = repeats();
        return list.get(index % list.size());
    }

    /// This background with its colour replaced and its layers kept — what
    /// `background-color` does.
    default Background colour(int argb) {
        return of(argb, layers(), position(), sizes(), repeats());
    }

    /// This background with its layers replaced — `background-image`.
    default Background layers(List<? extends CssImage> value) {
        return of(colour(), value, position(), sizes(), repeats());
    }

    /// This background moved — `background-position`.
    default Background position(BackgroundPosition value) {
        return of(colour(), layers(), value, sizes(), repeats());
    }

    /// This background with its layers sized — `background-size`.
    default Background sizes(List<BackgroundSize> value) {
        return of(colour(), layers(), position(), value, repeats());
    }

    /// This background with its layers tiled — `background-repeat`.
    default Background repeats(List<BackgroundRepeat> value) {
        return of(colour(), layers(), position(), sizes(), value);
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
        public List<CssImage> layers() {
            return List.of();
        }

        @Override
        public BackgroundPosition position() {
            return BackgroundPosition.ZERO;
        }

        @Override
        public List<BackgroundSize> sizes() {
            return AUTO_SIZE;
        }

        @Override
        public List<BackgroundRepeat> repeats() {
            return REPEAT;
        }

        @Override
        public Background fade(double alpha) {
            return alpha >= 1 ? this : new Colour(CssColor.fade(colour, alpha));
        }
    }

    /// A colour with gradients and pictures over it.
    ///
    /// @param colour   painted first, under every layer
    /// @param layers   top first
    /// @param position how far every layer is moved
    /// @param sizes    `background-size`, one or more, matched to the layers
    /// @param repeats  `background-repeat`, one or more, matched to the layers
    record Layers(
            int colour,
            List<CssImage> layers,
            BackgroundPosition position,
            List<BackgroundSize> sizes,
            List<BackgroundRepeat> repeats)
            implements Background {

        public Layers {
            layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
            Objects.requireNonNull(position, "position");
            sizes = List.copyOf(Objects.requireNonNull(sizes, "sizes"));
            repeats = List.copyOf(Objects.requireNonNull(repeats, "repeats"));
            if (sizes.isEmpty() || repeats.isEmpty()) {
                throw new IllegalArgumentException("a background has one size and one repeat at least");
            }
        }

        /// A colour with gradients over it, every one at its initial size and
        /// repeat.
        public Layers(int colour, List<? extends CssImage> layers, BackgroundPosition position) {
            this(colour, List.copyOf(layers), position, AUTO_SIZE, REPEAT);
        }

        @Override
        public Background fade(double alpha) {
            if (alpha >= 1) {
                return this;
            }
            return new Layers(
                    CssColor.fade(colour, alpha),
                    layers.stream().map(layer -> layer.fade(alpha)).toList(),
                    position,
                    sizes,
                    repeats);
        }
    }
}
