package dev.goldberry.css.contrast;

/// WCAG 2.1's contrast arithmetic, and the two floors the design system states.
///
/// ```java
/// double ratio = Contrast.ratio(0xFF2E3440, 0xFFECEFF4);
/// boolean legible = Contrast.meets(bg, text, Contrast.TEXT_FLOOR);
/// ```
///
/// Public rather than a private helper of the audit, because an application may
/// swap every alias token, and a theme that pairs `--gb-badge-warning-bg` with an
/// unreadable `--gb-badge-warning-text` is a legibility bug only its author can
/// check for.
///
/// **Alpha is ignored, and every caller has to know it.** A translucent colour
/// has no single ratio, because what it composites over decides the answer.
/// `button.ghost` and `--gb-selection` are exactly that case, and measuring them
/// here would score `transparent` as black and report a pass. [ThemeAudit] skips
/// what it cannot measure rather than measuring it badly; [#isOpaque] is the
/// check.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html#colour).
public final class Contrast {

    /// The floor for text below 20px, which is all the text in the catalog:
    /// `caption` is 11px and `body` is 13.
    public static final double TEXT_FLOOR = 4.5;

    /// The floor for anything that is **not** text — a glyph, a border, an
    /// indicator. Lower because a shape is not read letter by letter, and it is
    /// still a floor: a warning triangle nobody can see defeats the rule that
    /// forbids colour as the only carrier of meaning, since that rule assumes the
    /// thing carrying it is visible.
    public static final double NON_TEXT_FLOOR = 3.0;

    private Contrast() {}

    /// WCAG 2.1's contrast ratio, `(L1 + 0.05) / (L2 + 0.05)`, between two
    /// `0xAARRGGBB` colours.
    ///
    /// Symmetric: which argument is the background does not change the answer,
    /// which is why neither is named for a role.
    public static double ratio(int argbA, int argbB) {
        var a = relativeLuminance(argbA);
        var b = relativeLuminance(argbB);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /// WCAG 2.1 relative luminance, from an `0xAARRGGBB`.
    public static double relativeLuminance(int argb) {
        var r = linear(((argb >> 16) & 0xFF) / 255.0);
        var g = linear(((argb >> 8) & 0xFF) / 255.0);
        var b = linear((argb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /// Whether `argbA` against `argbB` clears `floor`.
    public static boolean meets(int argbA, int argbB, double floor) {
        return ratio(argbA, argbB) >= floor;
    }

    /// Whether a colour is fully opaque, and therefore has a ratio at all.
    ///
    /// The check every caller owes before measuring: see the class comment.
    public static boolean isOpaque(int argb) {
        return ((argb >>> 24) & 0xFF) == 0xFF;
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
