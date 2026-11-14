package dev.goldberry.natives.harfbuzz;

import java.util.Objects;

/// One OpenType feature set for a whole run: `tnum` on, `liga` off, `salt` at
/// its third alternate.
///
/// @param tag   four printable ASCII characters, case kept
/// @param value 0 turns the feature off, 1 on, and a larger number picks an
///              alternate where the feature has several
public record ShapingFeature(String tag, int value) {

    public ShapingFeature {
        Objects.requireNonNull(tag, "tag");
        if (tag.length() != 4 || !tag.chars().allMatch(c -> c >= 0x20 && c <= 0x7E)) {
            throw new IllegalArgumentException("a feature tag is four printable ASCII characters, not \"" + tag + "\"");
        }
        if (value < 0) {
            throw new IllegalArgumentException("a feature value is 0 or more, not " + value);
        }
    }

    /// The tag as HarfBuzz's `HB_TAG` packs it: the first character in the
    /// high byte.
    int packedTag() {
        return tag.charAt(0) << 24 | tag.charAt(1) << 16 | tag.charAt(2) << 8 | tag.charAt(3);
    }
}
