package io.github.digitalsmile.goldberry.assets;

import org.jspecify.annotations.Nullable;

/// The faces that ship inside `goldberry-core`.
///
/// Three families, and that is the whole fallback chain: a primary family and an
/// emoji slot, with a monospace face for code. `docs/ARCHITECTURE.md` §6.1 is
/// explicit that there is no general fallback cascade in v1 — a character in
/// neither slot renders as `.notdef`, deliberately, because a cascade across
/// arbitrary system fonts is what makes text look different on every machine.
///
/// ## A weight is a face here, not an axis
///
/// Inter and JetBrains Mono are variable files, and instancing `wght` at runtime
/// would be the general answer. It needs symbols bound in **both** HarfBuzz and
/// Blend2D and therefore three new export branches — the machinery that has
/// caught the same local-symbol bug three times. `docs/design-system.md` §1.4
/// ships exactly two weights, so the second one is a second face
/// (ADR-0066).
/// [Weight] is what a caller asks for; this enum is what answers.
///
/// ## And an italic is a face too
///
/// For the same reason and one step further on: Inter's italic is **drawn**, with
/// different letterforms, so it is a file rather than a transform. The alternative
/// without a file is shearing the upright glyphs, which is a *synthetic oblique* —
/// a decision about type design, not a workaround (`docs/gaps.md` G27, ADR-0323).
///
/// Two weights × two styles is four Inter faces, and the matrix closes on purpose:
/// a stylesheet that asks for a semibold italic heading gets one, rather than the
/// nearest of three.
public enum BundledFont {

    /// Inter Regular (400) — the UI face, and what every metric in the design
    /// system is authored against.
    UI("fonts/InterVariable.ttf", "Inter", Weight.REGULAR, Style.UPRIGHT),

    /// Inter SemiBold (600) — `body-strong`, and the weight §3 puts on a button's
    /// label.
    UI_STRONG("fonts/Inter-SemiBold.ttf", "Inter", Weight.SEMI_BOLD, Style.UPRIGHT),

    /// Inter Italic (400) — emphasis in running text, and the face a board's
    /// italic button asks for.
    UI_ITALIC("fonts/Inter-Italic.ttf", "Inter", Weight.REGULAR, Style.ITALIC),

    /// Inter SemiBold Italic (600) — the fourth corner of the matrix, so
    /// `font-weight: 600; font-style: italic` is a face rather than a compromise.
    UI_STRONG_ITALIC("fonts/Inter-SemiBoldItalic.ttf", "Inter", Weight.SEMI_BOLD, Style.ITALIC),

    /// JetBrains Mono — the code face. `mono` is specified at 400 only and
    /// upright only, so there is no strong or italic companion until something
    /// asks for one.
    CODE("fonts/JetBrainsMono.ttf", "JetBrains Mono", Weight.REGULAR, Style.UPRIGHT),

    /// OpenMoji, monochrome — the emoji slot (§6.2).
    ///
    /// The black variant by default, matching the toolkit's aesthetic and
    /// costing a fifth of what the colour build does. Colour is opt-in per text
    /// style and is not bundled until something can draw layered outlines.
    EMOJI("fonts/OpenMoji-black.ttf", "OpenMoji", Weight.REGULAR, Style.UPRIGHT);

    /// The two weights `docs/design-system.md` §1.4 ships.
    ///
    /// A closed pair rather than CSS's 100–900 ladder, because the design system
    /// specifies two and Principle 3 says a screen needing a third extends the
    /// system rather than improvising it. `font-weight: 700` resolves to the
    /// nearer of these, the way CSS's own matching algorithm resolves a weight
    /// no face provides — so a stylesheet that asks for bold gets SemiBold rather
    /// than nothing.
    public enum Weight {

        /// 400. `body`, `caption`, `mono`.
        REGULAR(400),

        /// 600. `display`, `title`, `heading`, `body-strong`.
        SEMI_BOLD(600);

        private final int value;

        Weight(int value) {
            this.value = value;
        }

        /// The CSS number — 400 or 600.
        public int value() {
            return value;
        }

        /// The shipped weight nearest to `css`.
        ///
        /// CSS's matching algorithm in the only form two faces need: everything
        /// at or below 500 is regular, everything above is semi-bold. `bold`
        /// (700) and `black` (900) both land on SemiBold, which is the honest
        /// answer — the alternative is a heading that silently renders at 400.
        public static Weight nearest(double css) {
            return css > 500 ? SEMI_BOLD : REGULAR;
        }
    }

    /// Upright or italic — CSS's `font-style`, in the two values a shipped face
    /// can honour.
    ///
    /// **`oblique` is absent and it is not an omission.** CSS's `oblique` asks for
    /// a *slant*, which without a slanted face means shearing the upright glyphs;
    /// Inter's italic is a different drawing rather than a sheared one, so
    /// answering `oblique` with it would be answering a different question, and
    /// answering it with a shear would be a type-design decision taken by a
    /// stylesheet (ADR-0323). A declaration that writes it is dropped with the
    /// usual warning.
    public enum Style {

        /// Roman — CSS's `normal`, and what every face did before this existed.
        UPRIGHT,

        /// Italic — a drawn face, with its own letterforms.
        ITALIC;

        /// The name as it is written in CSS — `normal`, not `UPRIGHT`.
        public String cssName() {
            return this == UPRIGHT ? "normal" : "italic";
        }
    }

    private final String resource;
    private final String family;
    private final Weight weight;
    private final Style style;

    BundledFont(String resource, String family, Weight weight, Style style) {
        this.resource = resource;
        this.family = family;
        this.weight = weight;
        this.style = style;
    }

    /// The family name as the font itself declares it.
    public String family() {
        return family;
    }

    /// Which of the two shipped weights this face is.
    public Weight weight() {
        return weight;
    }

    /// Whether this face is upright or italic.
    public Style style() {
        return style;
    }

    /// The bundled face for a family and a weight, upright.
    public static @Nullable BundledFont of(String family, Weight weight) {
        return of(family, weight, Style.UPRIGHT);
    }

    /// The bundled face for a family, a weight and a style.
    ///
    /// **Style before weight**, which is CSS's own font-matching order (family,
    /// then style, then weight) and the one that reads right: a family that has the
    /// style at another weight uses it, because a slant is what a reader was told
    /// to look for. A family with **no** such style falls back to the weight it was
    /// asked for, upright — `JetBrains Mono` ships one face, so italic code stays
    /// upright code rather than becoming italic Inter, which the family filter has
    /// already ruled out anyway.
    ///
    /// Falls back to the family's upright regular in the end, because refusing
    /// would mean throwing from inside a paint pass.
    public static @Nullable BundledFont of(String family, Weight weight, Style style) {
        BundledFont sameStyle = null;
        BundledFont sameWeight = null;
        BundledFont plain = null;
        for (var candidate : values()) {
            if (!candidate.family.equalsIgnoreCase(family)) {
                continue;
            }
            if (candidate.weight == weight && candidate.style == style) {
                return candidate;
            }
            if (candidate.style == style && candidate.weight == Weight.REGULAR) {
                sameStyle = candidate;
            }
            if (candidate.weight == weight && candidate.style == Style.UPRIGHT) {
                sameWeight = candidate;
            }
            if (candidate.weight == Weight.REGULAR && candidate.style == Style.UPRIGHT) {
                plain = candidate;
            }
        }
        // The style it asked for at the wrong weight beats the weight it asked for
        // in the wrong style, which is the order the note above explains.
        if (sameStyle != null) {
            return sameStyle;
        }
        return sameWeight != null ? sameWeight : plain;
    }

    String resource() {
        return resource;
    }
}
