package io.github.digitalsmile.goldberry.natives.yoga;

import java.util.Locale;

/// A length in a Yoga style — a number and the unit it is in.
///
/// Yoga has no such type on the setting side. Where CSS writes one property with
/// four possible kinds of value, Yoga exports up to three separate functions
/// (`YGNodeStyleSetWidth`, `YGNodeStyleSetWidthPercent`,
/// `YGNodeStyleSetWidthAuto`) and expresses the fourth by passing `YGUndefined`
/// — a NaN — to the first of them. Binding that shape directly would put the
/// choice of function at every call site and make "unset this" read as "set this
/// to not-a-number".
///
/// So the value is modelled once and [YogaNode] dispatches on it. Because the
/// interface is sealed, the dispatch is an exhaustive `switch` with no default
/// arm: a kind of length added here fails to compile everywhere it is not
/// handled, rather than falling through to a silent no-op.
///
/// Not every property accepts every kind. Yoga simply does not export
/// `YGNodeStyleSetMaxWidthAuto`, so [#AUTO] on a maximum is not a value Yoga
/// could be told; [YogaNode] rejects it by name rather than dropping it.
public sealed interface StyleLength {

    /// Size to the content, or — for a margin — absorb the free space. The
    /// second meaning is what centres a node between `auto` margins.
    StyleLength AUTO = Keyword.AUTO;

    /// No value. Sets the property back to Yoga's own default, which is what
    /// removing a declaration from a stylesheet has to mean.
    ///
    /// Reaches Yoga as `YGUndefined`, which is a NaN. That is Yoga's convention,
    /// not a value a caller should have to produce.
    StyleLength UNDEFINED = Keyword.UNDEFINED;

    /// A length in points — logical pixels, before the window's scale is applied.
    ///
    /// @throws IllegalArgumentException if the value is NaN or infinite
    static StyleLength points(float value) {
        return new Points(value);
    }

    /// A percentage of the parent's corresponding dimension. `50` means 50%,
    /// not 0.5.
    ///
    /// @throws IllegalArgumentException if the value is NaN or infinite
    static StyleLength percent(float value) {
        return new Percent(value);
    }

    /// A length in points.
    record Points(float value) implements StyleLength {

        public Points {
            requireFinite(value, "points");
        }

        @Override
        public String toString() {
            return value + "px";
        }
    }

    /// A percentage of the parent's corresponding dimension.
    record Percent(float value) implements StyleLength {

        public Percent {
            requireFinite(value, "percent");
        }

        @Override
        public String toString() {
            return value + "%";
        }
    }

    /// The two lengths that carry no number.
    ///
    /// An enum rather than two singleton records so that they compare by
    /// identity and print as themselves.
    enum Keyword implements StyleLength {

        /// See [StyleLength#AUTO].
        AUTO,

        /// See [StyleLength#UNDEFINED].
        UNDEFINED;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /// Negative lengths are allowed — a negative margin is meaningful, and Yoga
    /// clamps the ones that are not. NaN is not: it is how Yoga spells
    /// [#UNDEFINED], so admitting it here would give one state two spellings and
    /// make `equals` disagree with itself. Infinity is rejected for the same
    /// reason it is not a length.
    private static void requireFinite(float value, String unit) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    Float.isNaN(value)
                            ? "a " + unit + " length may not be NaN — use StyleLength.UNDEFINED"
                            : "a " + unit + " length may not be infinite, and " + value + " is");
        }
    }
}
