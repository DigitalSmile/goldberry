package io.github.digitalsmile.goldberry.assets;

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
/// ([ADR-0066](../../../../../../book/src/adr/0066-a-weight-is-a-face-and-color-inherits.md)).
/// [Weight] is what a caller asks for; this enum is what answers.
public enum BundledFont {

    /// Inter Regular (400) — the UI face, and what every metric in the design
    /// system is authored against.
    UI("fonts/InterVariable.ttf", "Inter", Weight.REGULAR),

    /// Inter SemiBold (600) — `body-strong`, and the weight §3 puts on a button's
    /// label.
    UI_STRONG("fonts/Inter-SemiBold.ttf", "Inter", Weight.SEMI_BOLD),

    /// JetBrains Mono — the code face. `mono` is specified at 400 only, so there
    /// is no strong companion until something asks for one.
    CODE("fonts/JetBrainsMono.ttf", "JetBrains Mono", Weight.REGULAR),

    /// OpenMoji, monochrome — the emoji slot (§6.2).
    ///
    /// The black variant by default, matching the toolkit's aesthetic and
    /// costing a fifth of what the colour build does. Colour is opt-in per text
    /// style and is not bundled until something can draw layered outlines.
    EMOJI("fonts/OpenMoji-black.ttf", "OpenMoji", Weight.REGULAR);

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

    private final String resource;
    private final String family;
    private final Weight weight;

    BundledFont(String resource, String family, Weight weight) {
        this.resource = resource;
        this.family = family;
        this.weight = weight;
    }

    /// The family name as the font itself declares it.
    public String family() {
        return family;
    }

    /// Which of the two shipped weights this face is.
    public Weight weight() {
        return weight;
    }

    /// The bundled face for a family and a weight.
    ///
    /// Falls back to the family's regular when it has no face at that weight —
    /// `JetBrains Mono` has no SemiBold, and refusing would mean bold code text
    /// throwing from inside a paint pass.
    public static BundledFont of(String family, Weight weight) {
        BundledFont fallback = null;
        for (var candidate : values()) {
            if (!candidate.family.equalsIgnoreCase(family)) {
                continue;
            }
            if (candidate.weight == weight) {
                return candidate;
            }
            if (candidate.weight == Weight.REGULAR) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    String resource() {
        return resource;
    }
}
