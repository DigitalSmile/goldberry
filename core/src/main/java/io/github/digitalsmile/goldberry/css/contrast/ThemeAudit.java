package io.github.digitalsmile.goldberry.css.contrast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssColor;

/// §1.2's contrast floors, measured against **any** theme — including one the
/// application wrote ([ADR-0241]).
///
/// `ContrastTest` has checked the two themes the toolkit ships since ADR-0087,
/// and §10 lets an application replace every alias token, so the guarantee
/// stopped exactly where a third-party theme began. This is the same arithmetic
/// with the pairs discovered rather than listed, so it holds for tokens nobody
/// here has seen.
///
/// ## The pairs are found by convention, not by a list
///
/// A hard-coded list of the toolkit's own pairs would check a custom theme's
/// *overrides* and miss everything it added. The design system already names its
/// pairs consistently — `--gb-badge-warning-bg` carries
/// `--gb-badge-warning-text`, `--gb-button-primary-bg` carries
/// `--gb-button-primary-text` — so **every `--gb-<name>-bg` with a matching
/// `--gb-<name>-text` is a pair**, and an application that follows the same
/// convention for `--gb-mycard-bg` is checked for free.
///
/// The surface pairs are added explicitly, because `--gb-text` on `--gb-bg` is
/// the one relationship the convention cannot express: neither token is named for
/// the other.
///
/// ## What it will not measure
///
/// A translucent colour has no single ratio — what it composites over decides the
/// answer — so a pair with alpha on either side is **skipped rather than
/// measured**. `--gb-selection` and `button.ghost`'s wash are that case, and
/// scoring them here would read `transparent` as black and report a comfortable
/// pass. [#audit] therefore returns fewer findings than there are tokens, and
/// that is the honest number rather than a gap.
public final class ThemeAudit {

    /// The surfaces a window paints, worst-case first is not meaningful here —
    /// every one has to clear the floor.
    private static final List<String> SURFACES = List.of("--gb-bg", "--gb-surface", "--gb-surface-2");

    /// What sits on a surface as words. Muted text is held to the same floor:
    /// §1.2 has no rank for "less important text", and a hint nobody can read is
    /// a hint that was not written.
    private static final List<String> ON_SURFACE = List.of("--gb-text", "--gb-text-muted");

    private ThemeAudit() {}

    /// Measures every pair this stylesheet set defines.
    ///
    /// @param stylesheets the sheets a window would be given — the toolkit's base
    ///                    sheet and the theme, in cascade order
    /// @return one finding per measurable pair, passing and failing alike, in a
    ///         stable order
    public static List<ContrastFinding> audit(List<Stylesheet> stylesheets) {
        var resolver = new StyleResolver(List.copyOf(stylesheets));
        var root = new Root();
        var declared = resolver.customPropertiesFor(root);

        var findings = new ArrayList<ContrastFinding>();
        // Deduplicated by the pair, because the two halves below overlap by one
        // and it is not a coincidence worth removing: `--gb-bg` ends in `-bg`, so
        // the convention below derives `--gb-text` from it and finds the same
        // pair the surface list states outright. Both are right; reporting it
        // twice is not.
        var seen = new java.util.HashSet<String>();
        // The surface pairs first, because they are the ones §1.2 names first and
        // a report reads better with them at the top.
        for (var surface : SURFACES) {
            for (var ink : ON_SURFACE) {
                if (seen.add(surface + " " + ink)) {
                    measure(resolver, root, surface, ink, Contrast.TEXT_FLOOR).ifPresent(findings::add);
                }
            }
        }
        // Then everything the `-bg`/`-text` convention finds, in the order the
        // cascade collected it -- which is stable for a given set of sheets, and
        // is not sorted because a caller that wants an order has one opinion and
        // this has none.
        for (var name : declared.keySet()) {
            if (!name.endsWith("-bg")) {
                continue;
            }
            var ink = name.substring(0, name.length() - "-bg".length()) + "-text";
            if (declared.containsKey(ink) && seen.add(name + " " + ink)) {
                measure(resolver, root, name, ink, Contrast.TEXT_FLOOR).ifPresent(findings::add);
            }
        }
        return List.copyOf(findings);
    }

    /// The failures alone, which is what a start-up check wants.
    public static List<ContrastFinding> failures(List<Stylesheet> stylesheets) {
        return audit(stylesheets).stream().filter(finding -> !finding.passes()).toList();
    }

    /// One pair, or empty when either half is unset, unparseable or translucent.
    private static java.util.Optional<ContrastFinding> measure(
            StyleResolver resolver, StyleElement root, String background, String foreground, double floor) {
        var fill = colour(resolver, root, background);
        var ink = colour(resolver, root, foreground);
        if (fill == null || ink == null || !Contrast.isOpaque(fill) || !Contrast.isOpaque(ink)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                new ContrastFinding(background, foreground, fill, ink, Contrast.ratio(fill, ink), floor));
    }

    /// A token's resolved colour, or null.
    ///
    /// **Substituted rather than raw**, which is [StyleResolver#customProperty]'s
    /// whole reason: a custom property may hold another one, and
    /// `--gb-badge-warning-bg: var(--gb-warning)` is the natural way to write it.
    /// Reading the tokens without resolving sees `var(--gb-warning)`, decides it
    /// is not a colour, and skips the pair silently — which would make a theme
    /// written the ordinary way audit as having nothing to check.
    private static @Nullable Integer colour(StyleResolver resolver, StyleElement root, String name) {
        var tokens = resolver.customProperty(root, name);
        return tokens == null ? null : CssColor.parse(tokens);
    }

    /// The node the tokens are read against: no type, no classes, no parent.
    ///
    /// A theme is a `:root` layer and nothing else — not one selector in either
    /// shipped file is anything but `:root` — and `:root` is matched as
    /// `parent() == null` ([SelectorMatcher]), so this is the whole of the tree
    /// the audit needs. It deliberately implements no custom-property cache: one
    /// resolve is all that happens here, and a cache that is never hit is a field
    /// that can go stale.
    private static final class Root implements StyleElement {

        @Override
        public @Nullable String type() {
            return null;
        }

        @Override
        public @Nullable String id() {
            return null;
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public @Nullable StyleElement parent() {
            return null;
        }

        /// No state, and `:root` is not asked here — the matcher answers that one
        /// from [#parent] rather than from the element.
        @Override
        public boolean hasState(io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass state) {
            return false;
        }
    }
}
