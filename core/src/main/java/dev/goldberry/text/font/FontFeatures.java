package dev.goldberry.text.font;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/// Which OpenType features text is shaped with: `font-variant-numeric` and
/// `font-feature-settings`, as the cascade resolved them.
///
/// ```css
/// .price  { font-variant-numeric: tabular-nums slashed-zero }
/// .code   { font-feature-settings: "liga" 0, "calt" 0 }
/// ```
///
/// Both inherit, as every font property does. The settings are applied after
/// the numeric variant, so a tag both name takes the settings' value, as CSS
/// orders them. A feature the face does not have does nothing.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
///
/// @param numeric  what `font-variant-numeric` turns on, in the order written;
///                 empty for `normal`
/// @param settings `font-feature-settings`, in the order written; empty for
///                 `normal`. A tag written twice keeps its last value
public record FontFeatures(List<Setting> numeric, List<Setting> settings) {

    /// `normal` and `normal`.
    public static final FontFeatures NONE = new FontFeatures(List.of(), List.of());

    public FontFeatures {
        numeric = List.copyOf(Objects.requireNonNull(numeric, "numeric"));
        settings = List.copyOf(Objects.requireNonNull(settings, "settings"));
    }

    /// One feature: `"tnum"` on, `"liga"` off, `"salt" 3`.
    ///
    /// @param tag   four printable ASCII characters, case kept
    /// @param value 0 off, 1 on, more for an alternate
    public record Setting(String tag, int value) {

        public Setting {
            Objects.requireNonNull(tag, "tag");
            if (tag.length() != 4 || !tag.chars().allMatch(c -> c >= 0x20 && c <= 0x7E)) {
                throw new IllegalArgumentException(
                        "a feature tag is four printable ASCII characters, not \"" + tag + "\"");
            }
            if (value < 0) {
                throw new IllegalArgumentException("a feature value is 0 or more, not " + value);
            }
        }
    }

    /// This, with `value` as its numeric variant.
    public FontFeatures numeric(List<Setting> value) {
        return new FontFeatures(value, settings);
    }

    /// This, with `value` as its settings.
    public FontFeatures settings(List<Setting> value) {
        return new FontFeatures(numeric, value);
    }

    /// Whether this asks for nothing.
    public boolean isNone() {
        return numeric.isEmpty() && settings.isEmpty();
    }

    /// Every feature to shape with: the numeric variant, then the settings over
    /// it, one value per tag. The order is the one each tag was first written
    /// in, so two equal requests give equal lists.
    public List<Setting> resolved() {
        if (settings.isEmpty()) {
            return numeric;
        }
        if (numeric.isEmpty()) {
            return settings;
        }
        var byTag = new LinkedHashMap<String, Setting>();
        for (var setting : numeric) {
            byTag.put(setting.tag(), setting);
        }
        for (var setting : settings) {
            byTag.put(setting.tag(), setting);
        }
        return List.copyOf(new ArrayList<>(byTag.values()));
    }
}
