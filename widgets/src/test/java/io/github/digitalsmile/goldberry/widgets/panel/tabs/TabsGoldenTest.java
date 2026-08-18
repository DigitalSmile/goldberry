package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a tab strip looks like (§14, [ADR-0050]).
///
/// [TabsTest] pins the model: which content is built, what is asked for, what the
/// colour is. These are the images that say the *drawing* adds up — and there are
/// two things here no assertion reaches. The selected tab's underline has to sit
/// **on** the list's rule rather than above or below it, which is two borders
/// meeting and is exactly the kind of thing that resolves to perfectly reasonable
/// numbers while looking wrong. And a tab given a colour of its own has to carry
/// it in both its label and its underline, which is one declaration
/// (`currentColor`) doing two jobs.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class TabsGoldenTest {

    private Icon icon;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        icon = Icon.bundled("folder", 16);
    }

    @AfterEach
    void tearDown() {
        if (icon != null) {
            icon.close();
        }
    }

    private static Attributes id(String id) {
        return new Attributes(id, Set.of(), id);
    }

    private void paint(String name, Theme theme, int width, int height, Widget strip,
            PseudoState... states) {
        var scene = new Column(List.of(strip), id("scene"));
        var tree = new ElementTree(scene);
        for (var state : states) {
            state.applyTo(tree.root().children().getFirst());
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

    /// Which header gets which pseudo-class, set by hand for `ButtonGoldenTest`'s
    /// reason: an image is about what a state looks like, and the router's tests
    /// are about whether input reaches it.
    private record PseudoState(int tab, Selector.PseudoClass pseudoClass) {

        /// The strip is a composition node over a `tabs`, whose child 0 is the
        /// `tab-list`, whose child 0 is the rule and whose child 1 is the
        /// headers' viewport — so a header is two levels further down than it
        /// looks. The viewport is there so a strip wider than its window scrolls
        /// rather than overflowing (ADR-0118).
        void applyTo(io.github.digitalsmile.goldberry.widget.Element strip) {
            strip.children().getFirst()     // tabs
                    .children().getFirst()  // tab-list
                    .children().get(1)      // Scroll, the composition node
                    .children().getFirst()  // the scroll viewport it builds
                    .children().getFirst()  // scroll-content
                    .children().get(tab)
                    .setPseudoClass(pseudoClass, true);
        }
    }

    private Tabs strip() {
        return new Tabs("editor",
                new Tab("editor", "Editor", new Text("The selected tab's content.")).icon(icon),
                new Tab("log", "Log"),
                new Tab("output", "Output"));
    }

    /// The image this widget exists to be checked by: three headers, one
    /// underlined, and the panel's content below the rule.
    @Test
    @DisplayName("three tabs with the first selected, on dark")
    void selectedDark() {
        paint("tabs-dark", Theme.NORD_DARK, 380, 110, strip());
    }

    @Test
    @DisplayName("the same strip on the light theme")
    void selectedLight() {
        paint("tabs-light", Theme.NORD_LIGHT, 380, 110, strip());
    }

    /// A tab with a colour of its own: the label *and* the underline take it, from
    /// one `currentColor` declaration.
    @Test
    @DisplayName("a tab carries its own colour into its underline")
    void colour() {
        paint("tabs-colour", Theme.NORD_DARK, 380, 110, new Tabs("log",
                new Tab("editor", "Editor"),
                new Tab("log", "Log", new Text("A tab coloured after what it shows."))
                        .colour(0xFFBF616A),
                new Tab("output", "Output")));
    }

    /// Closable tabs and the add affordance, which are the two ends of "a strip's
    /// list is the application's".
    @Test
    @DisplayName("closable tabs and the add affordance")
    void closableAndAddable() {
        paint("tabs-closable", Theme.NORD_DARK, 380, 110, new Tabs("editor",
                new Tab("editor", "Editor", new Text("Both ends of the list.")).closable(true),
                new Tab("log", "Log").closable(true))
                .onNew(() -> { }));
    }

    /// Hover on an unselected tab, which is the state that says the row is
    /// interactive at all.
    @Test
    @DisplayName("hover on an unselected tab")
    void hover() {
        paint("tabs-hover", Theme.NORD_DARK, 380, 110, strip(),
                new PseudoState(1, Selector.PseudoClass.HOVER));
    }

    /// The focus ring is *inside* the header, which is the one place in the
    /// catalog it has to be: a ring at §2.2's usual 2px offset would be drawn
    /// outside the strip's rule and over the tab beside it.
    @Test
    @DisplayName("the focus ring sits inside the header")
    void focusRing() {
        paint("tabs-focus", Theme.NORD_DARK, 380, 110, strip(),
                new PseudoState(2, Selector.PseudoClass.FOCUS_VISIBLE));
    }
}
