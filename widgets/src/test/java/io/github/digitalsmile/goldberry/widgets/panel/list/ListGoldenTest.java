package io.github.digitalsmile.goldberry.widgets.panel.list;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// What a `list` looks like — the picture [ListTest] cannot take.
///
/// The thing worth seeing is §3's "selection = `--gb-selection` **full-row**":
/// the wash runs edge to edge because nothing insets a row, which is what
/// distinguishes a list's selection from a menu's highlighted item. And a
/// multi-selection list is the only one where "a run of chosen rows" is a shape
/// rather than a number — three adjacent washes read as one block, which is what
/// makes a swept range legible at all.
class ListGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final String SCENE = """
            list { padding: 8px; background: var(--gb-surface); width: 240px }
            """;

    private static final List<String> NORDICS = List.of("Norway", "Sweden", "Finland", "Denmark", "Iceland");

    private void paint(String name, Theme theme, Set<String> selected) {
        var host = new TestHost();
        var tree = new ElementTree(
                ListView.of(NORDICS).selection(Selection.MULTIPLE).selected(selected, values -> {}), host);

        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(name, 240, 176, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    @Test
    @DisplayName("a list with one row chosen, dark")
    void dark() {
        paint("list-dark", Theme.NORD_DARK, Set.of("Sweden"));
    }

    @Test
    @DisplayName("and the same list on the light theme")
    void light() {
        paint("list-light", Theme.NORD_LIGHT, Set.of("Sweden"));
    }

    /// A swept range, which is the state a picture is the only proof of: three
    /// adjacent rows have to read as **one block** rather than as three
    /// highlights, or a `Shift` sweep does not communicate what it selected.
    @Test
    @DisplayName("a run of chosen rows reads as one block")
    void range() {
        paint("list-range-dark", Theme.NORD_DARK, Set.of("Sweden", "Finland", "Denmark"));
    }
}
