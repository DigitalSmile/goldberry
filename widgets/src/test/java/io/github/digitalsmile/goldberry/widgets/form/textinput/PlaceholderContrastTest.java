package io.github.digitalsmile.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A placeholder has to be dimmer than a value **and** readable, and both of
/// those are numbers.
///
/// [io.github.digitalsmile.goldberry.widgets.ContrastTest] cannot do this one,
/// and says so in its own words: a translucent fill has no contrast ratio,
/// because the answer depends on what it is composited over. `--gb-text-placeholder`
/// and `--gb-surface-sunken` are **both** translucent — deliberately, so a field
/// is one step below whatever surface it happens to sit on (ADR-0168) — so the
/// only honest measurement composites them explicitly against each background a
/// field can be on, which is what this does.
///
/// The floor is §1.2's 4.5:1. A placeholder is a hint rather than content, and
/// the temptation is to treat it as incidental and let it go faint; at the first
/// alpha tried it was **2.4:1 on the light theme**, which is not a hint, it is a
/// smudge. The ceiling is the other half of the requirement: at full strength a
/// placeholder is indistinguishable from a value, which is the defect that
/// started this — `--gb-text-muted` applied correctly and was two rungs from
/// `--gb-text`, so nobody could tell an empty field from a filled one.
class PlaceholderContrastTest {

    /// §1.2's floor for text.
    private static final double MINIMUM = 4.5;

    /// A placeholder must be **at most** this share of a value's contrast, or it
    /// does not read as standing in for something.
    ///
    /// Two thirds, which is loose on purpose: what matters is that there is a
    /// visible gap, and pinning the exact ratio would make this a change-detector
    /// for a token somebody is allowed to tune.
    private static final double DIMMER_THAN_A_VALUE = 0.67;

    /// The backgrounds a field actually sits on, worst case first.
    private record Surface(String name, io.github.digitalsmile.goldberry.widget.Widget host) {
    }

    private static List<Surface> surfaces() {
        return List.of(
                // A card is the lightest thing a field sits on in the dark theme
                // and the whitest in the light one, so it is where a translucent
                // fill has least room and where the ratio is tightest.
                new Surface("card", new Card()),
                new Surface("panel", new Panel()));
    }

    private static ComputedStyle resolve(Theme theme,
            io.github.digitalsmile.goldberry.widget.Widget widget) {
        return ComputedStyle.of(
                new StyleResolver(Controls.stylesheets(theme))
                        .resolve(new ElementTree(widget).root()),
                CssLength.Context.DEFAULT);
    }

    /// The `text-input` node's style, and its placeholder part's.
    ///
    /// **Not the widget's.** [TextInput] is stateful and styles nothing — the
    /// node a stylesheet sees is the [TextField] it builds — so resolving the
    /// widget itself measures a box with no background and no colour at all. The
    /// first version of this test did exactly that and reported a field whose
    /// fill was its own backdrop, which is how it came to say a value had less
    /// contrast than a placeholder.
    private record Field(ComputedStyle node, ComputedStyle placeholder) {

        static Field of(Theme theme) {
            var tree = new ElementTree(new TextInput().placeholder("Jane Doe"));
            var resolver = new StyleResolver(Controls.stylesheets(theme));
            var node = tree.root().children().getFirst();
            // root -> text-input -> [selection, value, caret]
            var value = node.children().get(1);
            return new Field(
                    ComputedStyle.of(resolver.resolve(node), CssLength.Context.DEFAULT),
                    ComputedStyle.of(resolver.resolve(value), CssLength.Context.DEFAULT));
        }
    }

    /// `argb` composited over `backdrop`, which is what the painter does and what
    /// a contrast ratio has to be measured on.
    private static int over(int argb, int backdrop) {
        var alpha = ((argb >>> 24) & 0xFF) / 255.0;
        var r = channel(argb, 16, alpha, backdrop);
        var g = channel(argb, 8, alpha, backdrop);
        var b = channel(argb, 0, alpha, backdrop);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int channel(int argb, int shift, double alpha, int backdrop) {
        var top = (argb >>> shift) & 0xFF;
        var bottom = (backdrop >>> shift) & 0xFF;
        return (int) Math.round(top * alpha + bottom * (1 - alpha));
    }

    private static double contrast(int one, int other) {
        var a = luminance(one);
        var b = luminance(other);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int argb) {
        return 0.2126 * linear(((argb >> 16) & 0xFF) / 255.0)
                + 0.7152 * linear(((argb >> 8) & 0xFF) / 255.0)
                + 0.0722 * linear((argb & 0xFF) / 255.0);
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    @Test
    @DisplayName("a placeholder is legible on every surface a field sits on, in both themes")
    void isLegible() {
        var failures = new ArrayList<String>();
        var report = new StringBuilder();

        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            for (var surface : surfaces()) {
                var styles = Field.of(theme);
                var backdrop = resolve(theme, surface.host()).background();
                var field = over(styles.node().background(), backdrop);

                // The placeholder's colour is the **part's**, because that is
                // where the rule is; a value's is the **field's**, because a
                // `text-value` with no rule of its own inherits it.
                var placeholder = over(styles.placeholder().color(), field);
                var value = over(styles.node().color(), field);

                var placeholderRatio = contrast(placeholder, field);
                var valueRatio = contrast(value, field);
                var name = theme + " on a " + surface.name();

                report.append("%-28s field %06x  placeholder %.2f  value %.2f%n"
                        .formatted(name, field & 0xFFFFFF, placeholderRatio, valueRatio));

                if (placeholderRatio < MINIMUM) {
                    failures.add("%s: a placeholder at %.2f:1 is not readable (want %.1f:1)"
                            .formatted(name, placeholderRatio, MINIMUM));
                }
                if (placeholderRatio > valueRatio * DIMMER_THAN_A_VALUE) {
                    failures.add(("%s: a placeholder at %.2f:1 against a value at %.2f:1 does not"
                            + " read as standing in for one")
                            .formatted(name, placeholderRatio, valueRatio));
                }
            }
        }

        assertTrue(failures.isEmpty(),
                String.join("\n", failures) + "\n\n" + report);
    }

}
