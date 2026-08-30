package io.github.digitalsmile.goldberry.widgets.overlay.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.DialogAction.Role;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// What a `dialog` looks like: over a window, in both themes, and caught opening.
///
/// The pictures carry two things no assertion does. The **scrim** is one — a
/// number in a stylesheet says nothing about whether the window behind it is
/// still readable, which is what §1.2 asks of a veil. The **action bar** is the
/// other: §7's affirmative-right order is a fact about where the eye lands, and
/// the roles being right in a list is not the same claim.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class DialogGoldenTest {

    private Clock.Virtual clock;
    private TestHost host;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        clock = Clock.virtual();
        host = new TestHost();
    }

    /// A window with something in it, so the veil has something to dim. A dialog
    /// over an empty grey rectangle is a picture of a dialog; this is a picture
    /// of a *modal*.
    private static final String SCENE = """
            #window { padding: 16px; gap: 8px; background: var(--gb-bg);
                      width: 460px; height: 300px }
            /* The scrim takes what is left of the window, which in a real one is
               everything: `Dialogs.show` puts it in a *filling* overlay, and an
               overlay is inset to the window's four edges. Here it is a sibling
               in a column, so it has to be told to grow -- and being told is
               what makes this picture a modal rather than a panel. */
            dialog-scrim { flex-grow: 1 }
            """;

    private static Widget window() {
        return new Column(
                List.of(
                        new Text("A window with a document in it.", Attributes.NONE.classes("prose")),
                        new Text("The veil dims this and takes every press.", Attributes.NONE.classes("caption")),
                        new Dialog(
                                "Unsaved changes",
                                List.of(
                                        new Text("Your draft has not been saved. Discarding it cannot be" + " undone."),
                                        new DialogAction("Don't save", Role.NEUTRAL, () -> {}),
                                        new DialogAction("Keep editing", Role.DISMISSIVE, () -> {}),
                                        new DialogAction("Discard", Role.AFFIRMATIVE, () -> {})),
                                Attributes.NONE.id("unsaved"))),
                Attributes.NONE.id("window"));
    }

    private WidgetRenderer rendererFor(Theme theme) {
        return new WidgetRenderer(
                        List.of(
                                Controls.baseStylesheet(),
                                theme.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                        TestFont.get())
                .clock(clock);
    }

    /// One frame to start the opening, then past the end of it.
    ///
    /// The dialog is a child of the scene here rather than a filling
    /// [io.github.digitalsmile.goldberry.Overlay], because a golden paints a box
    /// tree and not a window — `Dialogs.show` is what puts one in the overlay
    /// layer, and `DialogTest` is where that is asserted. What the scrim does to
    /// the layout is the same either way: it fills what it is given.
    private void paintOpen(String name, Theme theme) {
        var tree = new ElementTree(window(), host);
        var renderer = rendererFor(theme);

        renderer.render(tree);
        clock.advance(300);
        var settled = renderer.render(tree);
        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "a dialog that has been open 300ms is still moving");

        GoldenImage.assertMatches(name, 460, 300, 1.0f, frame -> BoxPainter.paint(frame, settled));
    }

    @Test
    @DisplayName("a dialog over a window, dark")
    void dark() {
        paintOpen("dialog-dark", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("a dialog over a window, light")
    void light() {
        paintOpen("dialog-light", Theme.NORD_LIGHT);
    }

    /// §3: "in: veil `opacity` overlay + panel `opacity` & `scale` 0.96→1".
    /// Halfway through, the veil is half dark and the panel is slightly small —
    /// a frame no wall clock can take.
    @Test
    @DisplayName("a dialog caught halfway open")
    void opening() {
        var tree = new ElementTree(window(), host);
        var renderer = rendererFor(Theme.NORD_DARK);

        renderer.render(tree);
        assertTrue(renderer.isAnimating(), "the dialog did not start opening");
        clock.advance(120);
        var midway = renderer.render(tree);

        GoldenImage.assertMatches("dialog-opening", 460, 300, 1.0f, frame -> BoxPainter.paint(frame, midway));
    }

    /// §3: "out: base, reverse", and §1.7's `closing` — the panel is still
    /// mounted and no longer takes input.
    @Test
    @DisplayName("a dialog caught halfway closed")
    void closing() {
        var tree = new ElementTree(window(), host);
        var renderer = rendererFor(Theme.NORD_DARK);
        renderer.render(tree);
        clock.advance(300);
        renderer.render(tree);

        Described.first(tree, DialogPanel.class)
                .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null));
        tree.flush();

        renderer.render(tree);
        clock.advance(80);
        var midway = renderer.render(tree);

        GoldenImage.assertMatches("dialog-closing", 460, 300, 1.0f, frame -> BoxPainter.paint(frame, midway));
    }
}
