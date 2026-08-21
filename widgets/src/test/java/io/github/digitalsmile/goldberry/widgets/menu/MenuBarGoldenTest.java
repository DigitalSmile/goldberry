package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a menu bar looks like (§14, [ADR-0163]).
///
/// [MenuBarTest] pins the model — which headings are described, what opens, what
/// is registered. These are the images that say the *drawing* adds up, and there
/// are two things here no assertion reaches: a heading has to read as part of a
/// bar rather than as a row of small buttons, and the heading whose menu is
/// showing has to be distinguishable from the one merely under the pointer,
/// which is two very similar overlays next to each other.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class MenuBarGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Attributes id(String value) {
        return new Attributes(value, Set.of(), value);
    }

    private void paint(String name, Theme theme, int width, int height, Widget bar,
            PseudoState... states) {

        var scene = new Column(List.of(bar), id("scene"));
        var tree = new ElementTree(scene);
        for (var state : states) {
            state.applyTo(tree);
        }
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #scene { padding: 12px; background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    /// Which heading gets which pseudo-class, set by hand for `TabsGoldenTest`'s
    /// reason: an image is about what a state looks like, and the router's tests
    /// are about whether input reaches it.
    ///
    /// The bar is a composition node over a `menubar`, whose children are the
    /// headings — so a heading is two levels down from the scene.
    private record PseudoState(int heading, Selector.PseudoClass pseudoClass) {

        void applyTo(ElementTree tree) {
            tree.root().children().getFirst()        // MenuBar, the composition node
                    .children().getFirst()           // the menubar row it describes
                    .children().get(heading)
                    .setPseudoClass(pseudoClass, true);
        }
    }

    private static MenuBar bar() {
        return new MenuBar(
                new Item("File").submenu(
                        new Item("Open…", () -> { }).accelerator("Ctrl+O"),
                        new Separator(),
                        new Item("Quit", () -> { }).accelerator("Ctrl+Q")),
                new Item("Edit").submenu(new Item("Undo", () -> { }).accelerator("Ctrl+Z")),
                new Item("View").submenu(new Item("Zoom In", () -> { })),
                new Item("Help").submenu(new Item("About", () -> { })));
    }

    /// The image this widget exists to be checked by: four headings in a row on a
    /// surface, with the page behind them.
    @Test
    @DisplayName("a bar of four headings, on dark")
    void restingDark() {
        paint("menubar-dark", Theme.NORD_DARK, 360, 60, bar());
    }

    @Test
    @DisplayName("the same bar on the light theme")
    void restingLight() {
        paint("menubar-light", Theme.NORD_LIGHT, 360, 60, bar());
    }

    @Test
    @DisplayName("hover on a heading")
    void hover() {
        paint("menubar-hover", Theme.NORD_DARK, 360, 60, bar(),
                new PseudoState(1, Selector.PseudoClass.HOVER));
    }

    /// The focus ring, which is what says the bar is one tab stop that the arrows
    /// then move within.
    @Test
    @DisplayName("the keyboard is on a heading")
    void focusRing() {
        paint("menubar-focus", Theme.NORD_DARK, 360, 60, bar(),
                new PseudoState(2, Selector.PseudoClass.FOCUS_VISIBLE));
    }

    /// The heading whose menu is showing.
    ///
    /// Built by hand rather than by opening a menu, because opening one needs a
    /// platform window and this test has none — and because what is being checked
    /// is that `.open` is *visibly different from* `:hover`, which is a question
    /// about two stylesheet rules rather than about a popup. [MenuBarTest] is
    /// what says the class arrives when a menu is actually down.
    @Test
    @DisplayName("the heading whose menu is showing is marked, and not as a hover")
    void open() {
        paint("menubar-open", Theme.NORD_DARK, 360, 60, new MenuBarRow(
                List.of(
                        new MenuTitle("File", null, false,
                                Attributes.NONE.id("t0").classes("open"), () -> { }, () -> { }),
                        new MenuTitle("Edit", null, false, Attributes.NONE.id("t1"),
                                () -> { }, () -> { }),
                        new MenuTitle("View", null, false, Attributes.NONE.id("t2"),
                                () -> { }, () -> { }),
                        new MenuTitle("Help", null, false, Attributes.NONE.id("t3"),
                                () -> { }, () -> { })),
                Attributes.NONE));
    }

    /// A heading that cannot be opened, which is the one state a bar shares with
    /// every other control in the catalog.
    @Test
    @DisplayName("a disabled heading")
    void disabled() {
        paint("menubar-disabled", Theme.NORD_DARK, 360, 60, new MenuBar(
                new Item("File").submenu(new Item("Open…", () -> { })),
                new Item("Edit").submenu(new Item("Undo", () -> { })).disabled(true),
                new Item("Help").submenu(new Item("About", () -> { }))));
    }
}
