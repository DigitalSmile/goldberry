package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a `select` actually looks like, closed and open (§14, [ADR-0050]).
///
/// [SelectTest] pins the tree and the keyboard; these are the images that say the
/// two halves are one control. Three things here are not reachable by any
/// assertion:
///
/// - **The field reads as a field.** It is one step off the surface behind it
///   with a 1px edge and a 4px radius, which is §3 filing this control with
///   `text-input` rather than with the buttons — and it is exactly what
///   disappears when a token stops resolving, with no length changing.
/// - **The chevron points down.** `CHEVRON_END` turned a quarter is what a mark
///   with a transform would have been, and this is the image that says the mark
///   drawn is the one meant.
/// - **The list's selected row and its highlighted row are different things.**
///   `--gb-selection` is a translucent wash and `--gb-overlay-hover` is another;
///   an arrow moves the second over the first, and two washes that came out the
///   same colour would be a control whose keyboard is invisible ([ADR-0141]).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class SelectGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Attributes id(String id, String... classes) {
        return new Attributes(id, Set.of(classes), id);
    }

    private static final String SCENE = """
            #row   { padding: 12px; gap: 8px; align-items: center;
                     background: var(--gb-bg) }
            #panel { padding: 12px; gap: 8px; align-items: flex-start;
                     background: var(--gb-surface) }
            """;

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var tree = new ElementTree(content);
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static Select themes(String value) {
        return new Select(value,
                List.of(new Option("light", "Light"),
                        new Option("dark", "Dark"),
                        new Option("dim", "Dim")),
                null, null, "Choose a theme", false, id("theme"));
    }

    /// The control at rest: a value, an edge, and a mark saying there is more.
    @Test
    @DisplayName("a closed select with a value, on dark")
    void closedDark() {
        paint("select-dark", Theme.NORD_DARK, 220, 56,
                new Row(List.of(themes("dark")), id("row")));
    }

    @Test
    @DisplayName("the same control on the light theme")
    void closedLight() {
        paint("select-light", Theme.NORD_LIGHT, 220, 56,
                new Row(List.of(themes("light")), id("row")));
    }

    /// Nothing chosen, and the placeholder one rank down.
    ///
    /// The image is the check: §1.2's contrast floor applies to a placeholder as
    /// much as to prose, and "quieter" that turns out to be unreadable is the
    /// mistake ADR-0121 made with `--gb-text-subtle` and took back out.
    @Test
    @DisplayName("nothing chosen reads as a placeholder, not as a value")
    void placeholder() {
        paint("select-placeholder", Theme.NORD_DARK, 220, 56,
                new Row(List.of(themes(null)), id("row")));
    }

    /// A field that refuses, at §2.1's 45% — over the border and the mark as well
    /// as the label, because opacity multiplies down the subtree ([ADR-0077]).
    @Test
    @DisplayName("a disabled select fades whole")
    void disabled() {
        paint("select-disabled", Theme.NORD_DARK, 220, 56,
                new Row(List.of(themes("dark").disabled(true)), id("row")));
    }

    /// The field on a panel rather than on the window, which is the case a fill
    /// of `--gb-surface` would have failed — see the note on `select`'s
    /// background in `controls.css`.
    @Test
    @DisplayName("a select on a panel is still a field")
    void onSurface() {
        paint("select-on-surface", Theme.NORD_DARK, 220, 56,
                new Column(List.of(themes("dim")), id("panel")));
    }

    /// The open list, with the value on one row and the keyboard on another.
    ///
    /// The pseudo-classes are set by hand rather than by opening a real popup,
    /// for `ButtonGoldenTest`'s reason: an image is about what a state looks like,
    /// and whether input reaches it is [SelectPopupTest]'s question. What is drawn
    /// is the popup's own tree — the panel is the root, so nothing inherits into
    /// it and every colour in this image comes from `select-list`'s own rules.
    @Test
    @DisplayName("the open list, with the chosen row and the highlighted row apart")
    void openList() {
        var list = new SelectList(List.of(
                new Option("light", "Light").inAList(),
                new Option("dark", "Dark", null, true, null, false, Attributes.NONE, false),
                new Option("dim", "Dim").inAList()));
        var tree = new ElementTree(list);
        // The third row is where an arrow has moved to; the second is the value.
        highlight(tree.root().children().get(2));

        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                TestFont.get());

        GoldenImage.assertMatches("select-list-dark", 180, 112, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static void highlight(Element row) {
        row.setPseudoClass(Selector.PseudoClass.FOCUS_VISIBLE, true);
    }
}
