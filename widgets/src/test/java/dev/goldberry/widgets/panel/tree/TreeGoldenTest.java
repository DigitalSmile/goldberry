package dev.goldberry.widgets.panel.tree;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.panel.Described;

/// What a `tree` looks like with two levels open — the picture [TreeTest] cannot
/// take.
///
/// The thing worth seeing is the indent of 20 per level with a 16 chevron in the
/// indent gutter: the labels of a folder and a file at one level line up, because a
/// leaf keeps the chevron's box and draws nothing in it. A tree that dropped the
/// box on its leaves would step every file half a chevron to the left, which
/// reads as a second level of nesting that is not there.
class TreeGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final String SCENE = """
            tree { padding: 8px; background: var(--gb-surface); width: 240px }
            """;

    private void paint(String name, Theme theme) {
        var host = new TestHost();
        var tree = new ElementTree(
                new Tree(
                        List.of(
                                TreeNode.of(
                                        "europe",
                                        "Europe",
                                        TreeNode.leaf("no", "Norway"),
                                        TreeNode.of("uk", "United Kingdom", TreeNode.leaf("sct", "Scotland"))),
                                TreeNode.leaf("asia", "Asia")),
                        "no",
                        value -> {}),
                host);

        // Opened from the keyboard, which is the only way in: expansion is the
        // widget's own state and no document can set it.
        open(tree, "europe");
        open(tree, "uk");

        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(name, 240, 176, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static void open(ElementTree tree, String id) {
        Described.of(tree, TreeRow.class).stream()
                .filter(row -> row.node().id().equals(id))
                .findFirst()
                .orElseThrow()
                .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE, false, null));
        tree.flush();
    }

    @Test
    @DisplayName("a tree with two levels open, dark")
    void dark() {
        paint("tree-dark", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("and the same tree on the light theme")
    void light() {
        paint("tree-light", Theme.NORD_LIGHT);
    }

    /// `checkable="cascade"`, and the one state a picture is the only proof of:
    /// **the mixed mark is a bar and not a greyed tick**.
    ///
    /// Norway is ticked and Scotland is not, so United Kingdom reads unchecked,
    /// Europe reads mixed, and the difference between "some of these" and "all of
    /// these" is a thing a reader can see at a glance rather than a claim.
    @Test
    @DisplayName("a cascade tree, with a branch that is only partly ticked")
    void cascade() {
        var host = new TestHost();
        var tree = new ElementTree(
                new Tree(
                                List.of(
                                        TreeNode.of(
                                                "europe",
                                                "Europe",
                                                TreeNode.leaf("no", "Norway"),
                                                TreeNode.of("uk", "United Kingdom", TreeNode.leaf("sct", "Scotland"))),
                                        TreeNode.leaf("asia", "Asia")),
                                "no",
                                value -> {})
                        .checkable(Checkable.CASCADE)
                        .checked(java.util.Set.of("no"), values -> {}),
                host);

        open(tree, "europe");
        open(tree, "uk");

        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(
                "tree-cascade-dark", 240, 176, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }
}
