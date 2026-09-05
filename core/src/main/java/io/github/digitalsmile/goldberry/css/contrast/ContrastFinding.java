package io.github.digitalsmile.goldberry.css.contrast;

import java.util.Locale;

/// One measured token pair, and whether it clears its floor.
///
/// Returned by [ThemeAudit] for **every** pair it could measure rather than only
/// the failures, because an application tuning a theme wants to see how much room
/// a pair has as well as which one broke — and because a caller that only wants
/// the failures can say so in one `filter` while the other direction is
/// impossible.
///
/// @param background   the token naming the fill, `--gb-badge-warning-bg`
/// @param foreground   the token naming what sits on it
/// @param backgroundArgb what `background` resolved to
/// @param foregroundArgb what `foreground` resolved to
/// @param ratio        the WCAG 2.1 ratio between them
/// @param floor        the ratio §1.2 asks of this pair
public record ContrastFinding(
        String background, String foreground, int backgroundArgb, int foregroundArgb, double ratio, double floor) {

    /// Whether this pair clears its floor.
    public boolean passes() {
        return ratio >= floor;
    }

    /// A line for a report, with the ratio to two places.
    ///
    /// Here rather than at each call site because every caller that has ever
    /// wanted one wanted the same line, and a `toString` on a record is the
    /// component list — useful for a debugger and unreadable in a build log.
    public String describe() {
        return String.format(
                Locale.ROOT,
                "%s on %s: %.2f:1 (needs %.1f:1)%s",
                foreground,
                background,
                ratio,
                floor,
                passes() ? "" : " — FAILS");
    }
}
