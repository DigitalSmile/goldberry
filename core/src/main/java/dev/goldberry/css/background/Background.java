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
/// `background-position`, `-size`, `-repeat`, `-origin` and `-clip` are comma
/// lists matched to the layers by position, and a list shorter than the layers
/// is repeated, as CSS repeats it: [#position(int)], [#size(int)],
/// [#repeat(int)], [#origin(int)] and [#clip(int)] answer for one layer. A
/// layer is placed in its origin's [BackgroundBox] and cut to its clip's; the
/// colour is cut to the last layer's clip.
///
/// ## What is not here
///
/// There is no `background-attachment`: a layer scrolls with its box, which is
/// what `scroll`, the initial value, says.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public sealed interface Background {

    /// Every layer at its natural size: CSS's initial `background-size`.
    List<BackgroundSize> AUTO_SIZE = List.of(BackgroundSize.AUTO);

    /// Every layer tiled: CSS's initial `background-repeat`.
    List<BackgroundRepeat> REPEAT = List.of(BackgroundRepeat.REPEAT);

    /// Every layer at the top left: CSS's initial `background-position`.
    List<BackgroundPosition> ZERO = List.of(BackgroundPosition.ZERO);

    /// Every layer placed inside the border: CSS's initial `background-origin`.
    List<BackgroundBox> PADDING_BOX = List.of(BackgroundBox.PADDING_BOX);

    /// Every layer drawn under the border too: CSS's initial `background-clip`.
    List<BackgroundBox> BORDER_BOX = List.of(BackgroundBox.BORDER_BOX);

    /// No fill at all — what every box starts as.
    static Background none() {
        return Colour.NONE;
    }

    /// The colour under every layer, `0xAARRGGBB`, not premultiplied.
    int colour();

    /// The layers, top first; empty for a plain colour.
    List<CssImage> layers();

    /// The `background-position` list, matched to the layers by
    /// [#position(int)].
    List<BackgroundPosition> positions();

    /// The first layer's position: all of them, unless a list says otherwise.
    default BackgroundPosition position() {
        return positions().getFirst();
    }

    /// The `background-size` list, matched to the layers by [#size(int)].
    List<BackgroundSize> sizes();

    /// The `background-repeat` list, matched to the layers by [#repeat(int)].
    List<BackgroundRepeat> repeats();

    /// The `background-origin` list, matched to the layers by [#origin(int)].
    List<BackgroundBox> origins();

    /// The `background-clip` list, matched to the layers by [#clip(int)].
    List<BackgroundBox> clips();

    /// A plain colour.
    static Background of(int argb) {
        return new Colour(argb);
    }

    /// A colour with `layers` over it, moved by `position` — or a plain
    /// [Colour] when there are no layers and nothing is moved.
    static Background of(int argb, List<? extends CssImage> layers, BackgroundPosition position) {
        return of(argb, layers, List.of(position), AUTO_SIZE, REPEAT, PADDING_BOX, BORDER_BOX);
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
        return of(argb, layers, List.of(position), sizes, repeats, PADDING_BOX, BORDER_BOX);
    }

    /// A colour with `layers` over it, each placed, sized, tiled and cut as the
    /// lists say — or a plain [Colour] when every list is at its initial value.
    static Background of(
            int argb,
            List<? extends CssImage> layers,
            List<BackgroundPosition> positions,
            List<BackgroundSize> sizes,
            List<BackgroundRepeat> repeats,
            List<BackgroundBox> origins,
            List<BackgroundBox> clips) {
        if (layers.isEmpty()
                && positions.equals(ZERO)
                && sizes.equals(AUTO_SIZE)
                && repeats.equals(REPEAT)
                && origins.equals(PADDING_BOX)
                && clips.equals(BORDER_BOX)) {
            return new Colour(argb);
        }
        return new Layers(argb, List.copyOf(layers), positions, sizes, repeats, origins, clips);
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

    /// Where layer `index` is placed: the list's entry, repeated when the list
    /// is shorter than the layers.
    default BackgroundPosition position(int index) {
        var list = positions();
        return list.get(index % list.size());
    }

    /// The rectangle layer `index` is placed in.
    default BackgroundBox origin(int index) {
        var list = origins();
        return list.get(index % list.size());
    }

    /// The rectangle layer `index` is cut to.
    default BackgroundBox clip(int index) {
        var list = clips();
        return list.get(index % list.size());
    }

    /// The rectangle the colour is cut to: the last layer's clip, or the
    /// first entry when there are no layers.
    default BackgroundBox colourClip() {
        return clip(Math.max(0, layers().size() - 1));
    }

    /// This background with its colour replaced and its layers kept — what
    /// `background-color` does.
    default Background colour(int argb) {
        return of(argb, layers(), positions(), sizes(), repeats(), origins(), clips());
    }

    /// This background with its layers replaced — `background-image`.
    default Background layers(List<? extends CssImage> value) {
        return of(colour(), value, positions(), sizes(), repeats(), origins(), clips());
    }

    /// This background with every layer at one place.
    default Background position(BackgroundPosition value) {
        return positions(List.of(value));
    }

    /// This background with its layers placed — `background-position`.
    default Background positions(List<BackgroundPosition> value) {
        return of(colour(), layers(), value, sizes(), repeats(), origins(), clips());
    }

    /// This background with its layers sized — `background-size`.
    default Background sizes(List<BackgroundSize> value) {
        return of(colour(), layers(), positions(), value, repeats(), origins(), clips());
    }

    /// This background with its layers tiled — `background-repeat`.
    default Background repeats(List<BackgroundRepeat> value) {
        return of(colour(), layers(), positions(), sizes(), value, origins(), clips());
    }

    /// This background with its layers placed in other boxes —
    /// `background-origin`.
    default Background origins(List<BackgroundBox> value) {
        return of(colour(), layers(), positions(), sizes(), repeats(), value, clips());
    }

    /// This background with its layers cut to other boxes — `background-clip`.
    default Background clips(List<BackgroundBox> value) {
        return of(colour(), layers(), positions(), sizes(), repeats(), origins(), value);
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
        public List<BackgroundPosition> positions() {
            return ZERO;
        }

        @Override
        public List<BackgroundBox> origins() {
            return PADDING_BOX;
        }

        @Override
        public List<BackgroundBox> clips() {
            return BORDER_BOX;
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
    /// @param colour    painted first, under every layer
    /// @param layers    top first
    /// @param positions `background-position`, one or more, matched to the layers
    /// @param sizes     `background-size`, one or more, matched to the layers
    /// @param repeats   `background-repeat`, one or more, matched to the layers
    /// @param origins   `background-origin`, one or more, matched to the layers
    /// @param clips     `background-clip`, one or more, matched to the layers
    record Layers(
            int colour,
            List<CssImage> layers,
            List<BackgroundPosition> positions,
            List<BackgroundSize> sizes,
            List<BackgroundRepeat> repeats,
            List<BackgroundBox> origins,
            List<BackgroundBox> clips)
            implements Background {

        public Layers {
            layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
            positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
            sizes = List.copyOf(Objects.requireNonNull(sizes, "sizes"));
            repeats = List.copyOf(Objects.requireNonNull(repeats, "repeats"));
            origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
            clips = List.copyOf(Objects.requireNonNull(clips, "clips"));
            if (positions.isEmpty() || sizes.isEmpty() || repeats.isEmpty() || origins.isEmpty() || clips.isEmpty()) {
                throw new IllegalArgumentException("a background has one of each list at least");
            }
        }

        /// A colour with gradients over it, every one at its initial size and
        /// repeat.
        ///
        /// The record's whole shape before layers had a size and a repeat, kept
        /// so code written against that shape still compiles; nothing here calls it.
        @SuppressWarnings("unused")
        public Layers(int colour, List<? extends CssImage> layers, BackgroundPosition position) {
            this(colour, List.copyOf(layers), List.of(position), AUTO_SIZE, REPEAT, PADDING_BOX, BORDER_BOX);
        }

        @Override
        public Background fade(double alpha) {
            if (alpha >= 1) {
                return this;
            }
            return new Layers(
                    CssColor.fade(colour, alpha),
                    layers.stream().map(layer -> layer.fade(alpha)).toList(),
                    positions,
                    sizes,
                    repeats,
                    origins,
                    clips);
        }
    }
}
