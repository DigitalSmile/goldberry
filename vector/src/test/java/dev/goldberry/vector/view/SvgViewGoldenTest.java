package dev.goldberry.vector.view;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.vector.VectorNativesRequirement;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Row;

/// `svg-view` as the theme draws it: one icon in `currentColor` at three sizes,
/// the same icon in the accent colour, a document with colours of its own, and
/// one that failed.
///
/// `./gradlew :vector:test -Dgoldberry.golden.update=true` rewrites it.
@DisplayName("svg-view, against a golden")
class SvgViewGoldenTest {

    /// A tick in a circle, stroked in `currentColor`.
    private static final String TICK = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <path d="M7 12.5l3.5 3.5L17 9"/>
            </svg>
            """;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    private static Attributes classes(String... names) {
        return new Attributes(null, Set.of(names), null);
    }

    @Test
    @DisplayName("icons at three sizes and in the accent, a document's own colours, and a failure")
    void row() {
        var tick = SvgSource.text(TICK);
        var row = new Row(
                List.of(
                        SvgView.decorative(tick).withAttributes(classes("small")),
                        SvgView.decorative(tick),
                        SvgView.decorative(tick).withAttributes(classes("large")),
                        SvgView.decorative(tick).withAttributes(classes("large", "accent")),
                        SvgView.decorative(SvgSource.text(SvgViewTest.HALVES)).withAttributes(classes("large")),
                        SvgView.decorative(SvgSource.text("not a document")).withAttributes(classes("large"))),
                new Attributes("page", Set.of(), null));
        var sheets = List.of(
                Controls.baseStylesheet(),
                Theme.NORD_DARK.load(),
                VectorStyles.stylesheet(),
                Stylesheet.parse(CascadeLayer.APPLICATION, """
                        #page { gap: 12px; padding: 12px; align-items: center; background: var(--gb-bg);
                                color: var(--gb-text) }
                        svg-view.small { width: 16px; height: 16px }
                        svg-view.large { width: 48px; height: 48px }
                        svg-view.accent { color: var(--gb-accent) }
                        """));
        GoldenImage.assertMatches(
                "svg-view",
                320,
                72,
                1f,
                (size, scale) ->
                        Offscreen.of(size).scale(scale).stylesheets(sheets).render(row));
    }
}
