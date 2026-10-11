package dev.goldberry.text.font;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import dev.goldberry.css.Typography;
import dev.goldberry.css.value.FontUnits;

/// `ch` and `ex` measured in the faces a book of fonts draws with.
///
/// ```java
/// var units = FontMeasures.of(fonts).face("Inter", 400);
/// double ch = units.ch(); // the advance of "0", per em
/// ```
///
/// A face is measured once, at [#REFERENCE] logical pixels, the first time a
/// stylesheet asks for one of its units, and the answer is kept per family and
/// weight: per em, it is the same at every size. A face that gives no x-height
/// answers half an em for `ex`, as CSS says to.
///
/// Confined to the thread that draws with the book, as the book is.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public final class FontMeasures {

    /// The size a face is measured at. Large enough that rounding in the
    /// shaper's fixed-point advances is far below what a length could show.
    static final double REFERENCE = 64;

    private final Fonts fonts;
    private final Map<Key, Ratios> measured = new HashMap<>();

    private FontMeasures(Fonts fonts) {
        this.fonts = fonts;
    }

    /// Measures over `fonts`, which the caller keeps open.
    public static FontMeasures of(Fonts fonts) {
        return new FontMeasures(Objects.requireNonNull(fonts, "fonts"));
    }

    /// The units of one face, measured when first asked.
    public FontUnits face(String family, int weight) {
        return new Face(this, new Key(Objects.requireNonNull(family, "family"), weight));
    }

    /// `ch` and `ex` of a single font, whatever face a stylesheet names: what a
    /// renderer that draws everything in one font measures with.
    public static FontUnits of(Font font) {
        Objects.requireNonNull(font, "font");
        return new Single(measure(font));
    }

    private Ratios ratios(Key key) {
        return measured.computeIfAbsent(
                key,
                k -> measure(
                        fonts.of(new Typography(k.family(), REFERENCE, k.weight(), Typography.INITIAL.lineHeight()))));
    }

    private static Ratios measure(Font font) {
        var size = font.size();
        var ch = font.widthOf("0") / size;
        var ex = font.xHeight() / size;
        return new Ratios(ch > 0 ? ch : FontUnits.FALLBACK.ch(), ex > 0 ? ex : FontUnits.FALLBACK.ex());
    }

    private record Key(String family, int weight) {}

    private record Ratios(double ch, double ex) {}

    /// One face of a book, measured lazily. A record, so two contexts over the
    /// same face compare equal.
    private record Face(FontMeasures source, Key key) implements FontUnits {

        @Override
        public double ch() {
            return source.ratios(key).ch();
        }

        @Override
        public double ex() {
            return source.ratios(key).ex();
        }

        @Override
        public FontUnits face(String family, int weight) {
            var next = new Key(family, weight);
            return next.equals(key) ? this : new Face(source, next);
        }
    }

    /// One font for every face.
    private record Single(Ratios ratios) implements FontUnits {

        @Override
        public double ch() {
            return ratios.ch();
        }

        @Override
        public double ex() {
            return ratios.ex();
        }

        @Override
        public FontUnits face(String family, int weight) {
            return this;
        }
    }
}
