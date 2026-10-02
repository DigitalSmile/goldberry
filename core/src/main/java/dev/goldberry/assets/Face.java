package dev.goldberry.assets;

import java.util.ArrayList;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.text.font.FontSource;

/// What names one typeface: a family, a numeric weight, upright or italic.
///
/// [BundledFont] is one of these, and so is an application's
/// [dev.goldberry.text.font.FontSource]. They share the description, and **one
/// matching rule** ([#match]) for when a stylesheet asks for a weight or a
/// style a family does not have. Two copies of that rule would disagree the
/// first time one was changed, and the result would be a shipped family that
/// falls back differently from Inter.
///
/// Sealed over the two, because the font book opens each kind differently and
/// a third kind would be a face it could not open.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
public sealed interface Face permits BundledFont, FontSource {

    /// The lightest weight CSS can write.
    int MIN_WEIGHT = 1;

    /// The heaviest weight CSS can write.
    int MAX_WEIGHT = 1000;

    /// The family name as a stylesheet writes it — `Inter`, `Forum`.
    String family();

    /// The CSS weight this file is drawn at: 400 for a regular, 600 for a
    /// semi-bold, anything from 1 to 1000.
    int weight();

    /// Whether this face is upright or italic.
    BundledFont.Style style();

    /// The best face in `candidates` for a family, a weight and a style, or null
    /// when none of them is that family.
    ///
    /// CSS's font-matching algorithm, family then style then weight:
    ///
    /// 1. only the faces of the family count;
    /// 2. if any of them has the style asked for, only those count; otherwise
    ///    all of them do, so italic code in a family with no italic stays
    ///    upright code;
    /// 3. among what is left, the weight asked for, or else the nearest by
    ///    CSS's rule: from 400 to 500, heavier faces up to 500 first, then
    ///    lighter ones, then heavier ones; below 400, lighter first; above
    ///    500, heavier first.
    ///
    /// So a family registered at 500, 600, 700 and 800 gives each of them to
    /// the stylesheet that asks, and `font-weight: 500` over Inter's 400 and
    /// 600 is the 400, the way a browser draws it.
    ///
    /// Family names are compared ignoring case, as CSS compares them.
    ///
    /// @param <F>        the kind of face — bundled, or shipped by an application
    /// @param candidates where to look, in no particular order
    /// @param weight     a CSS weight, 1 to 1000
    /// @throws IllegalArgumentException if the weight is outside 1 to 1000
    static <F extends Face> @Nullable F match(
            Iterable<? extends F> candidates, String family, int weight, BundledFont.Style style) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(style, "style");
        requireWeight(weight);
        var sameFamily = new ArrayList<F>();
        var styled = false;
        for (var candidate : candidates) {
            if (candidate.family().equalsIgnoreCase(family)) {
                sameFamily.add(candidate);
                styled |= candidate.style() == style;
            }
        }
        F best = null;
        for (var candidate : sameFamily) {
            if (styled && candidate.style() != style) {
                continue;
            }
            if (best == null || rank(weight, candidate.weight()) < rank(weight, best.weight())) {
                best = candidate;
            }
        }
        return best;
    }

    /// The same, for one of the two weights the toolkit names.
    static <F extends Face> @Nullable F match(
            Iterable<? extends F> candidates, String family, BundledFont.Weight weight, BundledFont.Style style) {
        return match(
                candidates, family, Objects.requireNonNull(weight, "weight").value(), style);
    }

    /// `weight`, if CSS can write it.
    ///
    /// @throws IllegalArgumentException if it is outside 1 to 1000
    static int requireWeight(int weight) {
        if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException("a font weight is 1 to 1000, as CSS writes it, not " + weight);
        }
        return weight;
    }

    /// Where a face at `available` stands in CSS's search order for `desired`;
    /// lower is searched first. Three bands, each ordered by distance.
    private static int rank(int desired, int available) {
        if (available == desired) {
            return 0;
        }
        var distance = Math.abs(available - desired);
        if (desired >= 400 && desired <= 500) {
            // Heavier up to 500, then lighter, then heavier past 500.
            if (available > desired && available <= 500) {
                return 1_000 + distance;
            }
            return (available < desired ? 2_000 : 3_000) + distance;
        }
        var preferred = desired < 400 ? available < desired : available > desired;
        return (preferred ? 1_000 : 2_000) + distance;
    }
}
