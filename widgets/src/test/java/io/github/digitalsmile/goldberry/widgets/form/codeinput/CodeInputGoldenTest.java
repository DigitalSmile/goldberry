package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.select.Selector;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// What a `code-input` looks like — §2's row for it, photographed.
///
/// Every number in that row is a geometry no assertion in [CodeInputTest] can
/// see: a box 40 wide and 48 tall, 8 between them, **16 at the midpoint** and not
/// at every gap, a `title` character on the centre of each box, and a focus ring
/// on one box rather than around the six. Each of those is a thing that would
/// look wrong at a glance and pass every test in the file next to this one —
/// which is the same argument `FieldGoldenTest` makes.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class CodeInputGoldenTest {

    private final TestHost host = new TestHost();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A surface to sit on, so the boxes' own `--gb-surface` is not being read
    /// against the window's background.
    private static final String SCENE = """
            code-input { padding: 16px }
            """;

    /// A golden has no window to give the keyboard to, so focus arrives twice —
    /// and it has to, because the two halves of it live in different places.
    /// The **widget** is told through `onFocusChanged`, which is what puts the
    /// `active` class on the fourth box; the **element** is told through
    /// `setPseudoClass`, which is what makes `:focus-visible` match. `MenuGoldenTest`
    /// does the second half the same way.
    private void paint(String name, Theme theme, int width, int height, Widget content, boolean focused) {
        var tree = new ElementTree(content, host);
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());
        renderer.render(tree);
        if (focused) {
            var field = tree.root().children().getFirst();
            ((CodeField) field.widget()).onFocusChanged(true, true);
            tree.flush();
            // After the flush, because the rebuild it schedules is what replaces
            // the node whose pseudo-classes this is setting.
            tree.root().children().getFirst().setPseudoClass(Selector.PseudoClass.FOCUS_VISIBLE, true);
        }
        GoldenImage.assertMatches(name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static CodeInput typed(String code) {
        return new CodeInput(code, null, null, null, 6, CodeType.DIGITS, false, false, null);
    }

    /// Three filled boxes and three empty ones, which is the only frame that
    /// shows both of `code-box`'s edges at once.
    @Test
    @DisplayName("six boxes, half filled, on dark")
    void partlyFilledDark() {
        paint("code-input-dark", Theme.NORD_DARK, 320, 88, typed("123"), false);
    }

    @Test
    @DisplayName("and the same on the light theme")
    void partlyFilledLight() {
        paint("code-input-light", Theme.NORD_LIGHT, 320, 88, typed("123"), false);
    }

    /// §2.2: "focus moves between boxes". The ring is on the **fourth** box —
    /// the first empty one — and not around the field, which is the one thing
    /// about this control that a picture is the only way to check.
    @Test
    @DisplayName("the ring is on the active box, not around the six")
    void focused() {
        paint("code-input-focus", Theme.NORD_DARK, 320, 88, typed("123"), true);
    }

    /// `FocusGoldenPairTest` is what requires this and not this comment: every
    /// `*-focus.png` in the corpus must have a `-light` twin, because a focus
    /// ring is the one mark in the system with no second means of being seen and
    /// `--gb-focus` resolves differently per theme.
    @Test
    @DisplayName("and the same ring on the light theme")
    void focusedOnLight() {
        paint("code-input-focus-light", Theme.NORD_LIGHT, 320, 88, typed("123"), true);
    }

    /// An odd length is a flat row: one group, so the 16 at the midpoint never
    /// applies. Photographed beside the even case because the difference between
    /// them is the whole of why [CodeGroup] exists.
    @Test
    @DisplayName("five boxes are one flat row, with no group gap")
    void oddLength() {
        paint(
                "code-input-odd",
                Theme.NORD_DARK,
                320,
                88,
                new CodeInput("1234", null, null, null, 5, CodeType.DIGITS, false, false, null),
                false);
    }

    /// `mask=#true`, authenticator-style — bullets in the filled boxes and
    /// nothing anywhere that says what was typed.
    @Test
    @DisplayName("a masked code draws bullets")
    void masked() {
        paint(
                "code-input-masked",
                Theme.NORD_DARK,
                320,
                88,
                new CodeInput("1234", null, null, null, 6, CodeType.DIGITS, true, false, null),
                false);
    }
}
