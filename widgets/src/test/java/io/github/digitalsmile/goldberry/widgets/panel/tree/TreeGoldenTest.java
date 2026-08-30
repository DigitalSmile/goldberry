package io.github.digitalsmile.goldberry.widgets.panel.tree;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.panel.Described;

/// What a `tree` looks like with two levels open — the picture [TreeTest] cannot
/// take.
///
/// The thing worth seeing is §2's "indent 20 per level; chevron 16 in the indent
/// gutter": the labels of a folder and a file at one level line up, because a
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
        // widget's own state and no document can set it (ADR-0184).
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

    /// §3's `checkable="cascade"`, and the one state a picture is the only proof
    /// of: **the mixed mark is a bar and not a greyed tick**.
    ///
    /// Norway is ticked and Scotland is not, so United Kingdom reads unchecked,
    /// Europe reads mixed, and the difference between "some of these" and "all of
    /// these" is a thing a reader can see at a glance rather than a claim
    /// ([ADR-0210]).
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
