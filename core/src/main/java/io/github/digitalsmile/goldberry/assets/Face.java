package io.github.digitalsmile.goldberry.assets;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.text.font.FontSource;

/// What names one typeface: a family, one of the two weights, upright or italic.
///
/// [BundledFont] is one of these, and so is an application's
/// [io.github.digitalsmile.goldberry.text.font.FontSource]. They share the
/// description, and **one matching rule** for when a stylesheet asks for a
/// corner of the matrix a family does not have (`docs/gaps.md` G39, ADR-0349).
/// Two copies of that rule would disagree the first time one was changed, and
/// the result would be a shipped family that falls back differently from Inter.
///
/// Sealed over the two, because the font book opens each kind differently and
/// a third kind would be a face it could not open.
public sealed interface Face permits BundledFont, FontSource {

    /// The family name as a stylesheet writes it — `Inter`, `Forum`.
    String family();

    /// Which of the two weights this face is.
    BundledFont.Weight weight();

    /// Whether this face is upright or italic.
    BundledFont.Style style();

    /// The best face in `candidates` for a family, a weight and a style, or null
    /// when none of them is that family.
    ///
    /// CSS's font-matching order, family then style then weight:
    ///
    /// 1. the exact corner;
    /// 2. the style asked for, at regular weight — a slant is what a reader was
    ///    told to look for, so it beats the weight;
    /// 3. the weight asked for, upright;
    /// 4. the family's upright regular.
    ///
    /// A family with none of those four still answers null rather than an
    /// arbitrary face of its own. A family that ships only a semi-bold italic is
    /// a family nothing in a stylesheet can reach safely, and the book's
    /// fallback is the UI face rather than a guess.
    ///
    /// Family names are compared ignoring case, as CSS compares them.
    ///
    /// @param <F>        the kind of face — bundled, or shipped by an application
    /// @param candidates where to look, in no particular order
    static <F extends Face> @Nullable F match(
            Iterable<? extends F> candidates, String family, BundledFont.Weight weight, BundledFont.Style style) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(style, "style");
        F sameStyle = null;
        F sameWeight = null;
        F plain = null;
        for (var candidate : candidates) {
            if (!candidate.family().equalsIgnoreCase(family)) {
                continue;
            }
            var regular = candidate.weight() == BundledFont.Weight.REGULAR;
            var upright = candidate.style() == BundledFont.Style.UPRIGHT;
            if (candidate.weight() == weight && candidate.style() == style) {
                return candidate;
            }
            if (candidate.style() == style && regular) {
                sameStyle = candidate;
            }
            if (candidate.weight() == weight && upright) {
                sameWeight = candidate;
            }
            if (regular && upright) {
                plain = candidate;
            }
        }
        if (sameStyle != null) {
            return sameStyle;
        }
        return sameWeight != null ? sameWeight : plain;
    }
}
