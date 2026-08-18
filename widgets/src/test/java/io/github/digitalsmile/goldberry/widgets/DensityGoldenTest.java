package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Row;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
import io.github.digitalsmile.goldberry.widgets.controls.radio.Radio;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a density looks like (§1.3, [ADR-0074]).
///
/// [DensityTest] checks that the token moved. This checks that the *control* did,
/// which is a different claim: a height token can resolve to 28 and land on a
/// control whose label then sits off-centre, or whose glyph has been squeezed, or
/// whose focus ring still traces the 32px box it used to be. None of those is
/// visible to a value assertion, and all three are what a density gets wrong.
///
/// Both images are the same scene, so the pair is the assertion — a diff of one
/// against the other should show three controls four pixels shorter and nothing
/// else moved.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class DensityGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Density density) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK, density));
        // The scene's own frame, not the controls'. `align-items: flex-start` so
        // the row does not stretch its children to its own height, which would
        // hide the very thing these images are about.
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, """
                #scene { padding: 12px; gap: 16px; align-items: flex-start;
                         background: var(--gb-bg) }
                """));

        var renderer = new WidgetRenderer(sheets, TestFont.get());
        GoldenImage.assertMatches(name, 460, 56, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(scene()))));
    }

    /// One of each sized control, so a density that reached only some of them
    /// shows up as a ragged row rather than as a passing test.
    private Widget scene() {
        return new Row(
                List.of(
                        new Button("Save", null, null, false, id("save")),
                        new Checkbox("Frost", Checkbox.Value.CHECKED, null, null, false, id("frost")),
                        new Radio("dark", "Dark", true, null, false, id("dark"))),
                id("scene"));
    }

    private static Attributes id(String id) {
        return new Attributes(id, Set.of(), id);
    }

    @Test
    @DisplayName("the catalog at regular — 32px controls, §1.3's default")
    void regular() {
        paint("controls-density-regular", Density.REGULAR);
    }

    @Test
    @DisplayName("the same scene at compact — 28px controls, and a 16px glyph that did not move")
    void compact() {
        // The glyphs are the check: §1.3 shrinks the control and says nothing
        // about what is inside it, so the tick and the dot stay 16px and the row
        // closes around them. A density that scaled its contents would look
        // plausible here and be wrong -- it would be a zoom, not a density.
        paint("controls-density-compact", Density.COMPACT);
    }
}
