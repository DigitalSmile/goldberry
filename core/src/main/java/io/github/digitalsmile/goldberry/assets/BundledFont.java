package io.github.digitalsmile.goldberry.assets;

/// The faces that ship inside `goldberry-core`.
///
/// Exactly three, and that is the whole fallback chain: a primary family and an
/// emoji slot, with a monospace face for code. `docs/ARCHITECTURE.md` §6.1 is
/// explicit that there is no general fallback cascade in v1 — a character in
/// neither slot renders as `.notdef`, deliberately, because a cascade across
/// arbitrary system fonts is what makes text look different on every machine.
public enum BundledFont {

    /// Inter — the UI face, and what every metric in the design system is
    /// authored against. A variable font, so a weight between the named ones is
    /// a real instance rather than a synthetic smear.
    UI("fonts/InterVariable.ttf", "Inter"),

    /// JetBrains Mono — the code face. Also variable.
    CODE("fonts/JetBrainsMono.ttf", "JetBrains Mono"),

    /// OpenMoji, monochrome — the emoji slot (§6.2).
    ///
    /// The black variant by default, matching the toolkit's aesthetic and
    /// costing a fifth of what the colour build does. Colour is opt-in per text
    /// style and is not bundled until something can draw layered outlines.
    EMOJI("fonts/OpenMoji-black.ttf", "OpenMoji");

    private final String resource;
    private final String family;

    BundledFont(String resource, String family) {
        this.resource = resource;
        this.family = family;
    }

    /// The family name as the font itself declares it.
    public String family() {
        return family;
    }

    String resource() {
        return resource;
    }
}
