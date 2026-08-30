package io.github.digitalsmile.goldberry.widgets.data;

import io.github.digitalsmile.goldberry.widget.style.Paints;

/// Which colour a series is drawn in — `charts.md` §2.
///
/// Eight slots, read from the theme as `--gb-chart-1…8` and **assigned in order**.
/// The order is not a preference: it is the mechanism that keeps adjacent series
/// distinguishable under colour-vision deficiency, and it was searched over all
/// 40 320 permutations rather than chosen
/// (ADR-0194).
///
/// ## Never cycled
///
/// A ninth series does **not** wrap around to slot 1, and does not get a
/// generated hue either — under CVD a ninth hue is indistinguishable from one of
/// the eight, and generating it would break the property the search established.
/// [#of] answers slot 8 for everything past it, which is deliberately a *visible*
/// wrong answer: two series in the same colour is a chart that needs folding into
/// "Other" or faceting into small multiples, and it should look like one.
///
/// ## Read from the cascade, not from a table
///
/// The values live in the theme files, so a theme owns them and an application
/// overrides one with an ordinary rule — `#revenue { --gb-chart-1: #b48ead }`
/// recolours one chart's first series and nothing else. A Java table would have
/// been simpler and would have made the palette the toolkit's rather than the
/// theme's (ADR-0195).
public final class SeriesPalette {

    /// How many slots the theme defines. Past this the answer repeats; see the
    /// class note on why that is the right kind of wrong.
    public static final int SLOTS = 8;

    /// What a slot is drawn in when the theme defines nothing — the derived
    /// dark-theme steps, so a chart rendered against no stylesheet at all is
    /// still eight distinguishable colours rather than eight black lines.
    private static final int[] FALLBACK = {
        0xFF73A340, 0xFFC46FB7, 0xFFB88A07, 0xFF5094E5,
        0xFFDA6A76, 0xFF02A3C1, 0xFFD9704F, 0xFF06A7A7,
    };

    private SeriesPalette() {}

    /// The colour of series `index`, counting from zero.
    ///
    /// @param context the render context, which resolves the token against the
    ///                node being drawn — so a rule on the chart wins over the
    ///                theme, exactly as a rule on anything else does
    /// @param index   the series' position, counting from zero
    public static int of(Paints.Context context, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("a series index counts from zero, and " + index + " does not");
        }
        var slot = Math.min(index, SLOTS - 1);
        return context.color("--gb-chart-" + (slot + 1), FALLBACK[slot]);
    }
}
