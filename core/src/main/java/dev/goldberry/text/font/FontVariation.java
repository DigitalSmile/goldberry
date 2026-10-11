package dev.goldberry.text.font;

import java.util.List;
import java.util.Objects;

/// Where a variable face is set beyond its weight: `font-stretch` and
/// `font-variation-settings`, as the cascade resolved them.
///
/// ```css
/// .condensed { font-stretch: 87.5% }
/// .display   { font-variation-settings: "opsz" 32, "GRAD" -25 }
/// ```
///
/// Both inherit, as every font property does, and both are read by the font
/// book alone: a face with a `wdth` axis is drawn at the stretch, a face with
/// the axes a setting names is drawn there, and a static face ignores both.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
///
/// @param stretch  `font-stretch` as a percentage of the normal width: 100 is
///                 `normal`, 50 to 200
/// @param settings `font-variation-settings`, in the order written; empty for
///                 `normal`. A tag written twice keeps its last value
public record FontVariation(double stretch, List<Setting> settings) {

    /// `normal` and `normal`.
    public static final FontVariation NONE = new FontVariation(100, List.of());

    /// The narrowest and widest stretches CSS writes.
    public static final double MIN_STRETCH = 50;

    public static final double MAX_STRETCH = 200;

    public FontVariation {
        settings = List.copyOf(Objects.requireNonNull(settings, "settings"));
        if (!(stretch >= MIN_STRETCH && stretch <= MAX_STRETCH)) {
            throw new IllegalArgumentException("a font stretch is 50% to 200%, not " + stretch + "%");
        }
    }

    /// One `"tag" value` of `font-variation-settings`.
    ///
    /// @param tag   four printable ASCII characters, case kept: `wght` and `GRAD`
    ///              are different axes
    /// @param value the position along it, in the axis's own units
    public record Setting(String tag, double value) {

        public Setting {
            Objects.requireNonNull(tag, "tag");
            if (tag.length() != 4 || !tag.chars().allMatch(c -> c >= 0x20 && c <= 0x7E)) {
                throw new IllegalArgumentException(
                        "an axis tag is four printable ASCII characters, not \"" + tag + "\"");
            }
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("an axis position is finite, not " + value);
            }
        }
    }

    /// This, at `value` percent of the normal width.
    public FontVariation stretch(double value) {
        return new FontVariation(value, settings);
    }

    /// This, with `value` as its settings.
    public FontVariation settings(List<Setting> value) {
        return new FontVariation(stretch, value);
    }

    /// Whether this asks a face for nothing beyond its weight.
    public boolean isNone() {
        return stretch == 100 && settings.isEmpty();
    }
}
