package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

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
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.form.parts.PickerPanel;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;

/// What a `color-picker` looks like — and this is the widget in the catalog that
/// most needs a picture.
///
/// Everything it draws is **painted rather than styled**: the plane is a hue and
/// two gradients, the hue ramp is six stops, the alpha ramp is a chequerboard
/// under a fade. None of that is reachable from a stylesheet and none of it is
/// assertable — a plane painted in the wrong order (black then white instead of
/// white then black) is a grey square that passes every test in the file next to
/// this one.
///
/// The board is built directly rather than by opening the popover, because a
/// golden has no window and a popup is a platform window (ADR-0140).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class ColorPickerGoldenTest {

    private static final int NORD_FROST = 0xFF88C0D0;

    private final TestHost host = new TestHost();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final String SCENE = """
            #scene { padding: 12px; background: var(--gb-bg); align-items: flex-start }
            """;

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var scene = new Column(List.of(content), new Attributes("scene", Set.of(), "scene"));
        var tree = new ElementTree(scene, host);
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    /// The board, built the way the state builds it.
    private static Widget board(HsvColor colour, boolean alpha, List<Integer> presets) {
        return new PickerPanel(new ColorBoard(
                new ColorPlane(colour, (s, v) -> {}, false),
                new ColorRamp(ColorRamp.Kind.HUE, colour, position -> {}, false),
                alpha ? new ColorRamp(ColorRamp.Kind.ALPHA, colour, position -> {}, false) : null,
                new TextInput(colour.toHex(), null).placeholder("#000000"),
                presets,
                argb -> {}));
    }

    /// The closed control: §4's "swatch button", and the chevron beside it.
    @Test
    @DisplayName("the swatch button, on dark")
    void closedDark() {
        paint("color-picker-dark", Theme.NORD_DARK, 96, 56, new ColorPicker("#88c0d0", null));
    }

    @Test
    @DisplayName("and the same on the light theme")
    void closedLight() {
        paint("color-picker-light", Theme.NORD_LIGHT, 96, 56, new ColorPicker("#88c0d0", null));
    }

    /// The board with no alpha ramp, which is the default. The cursor sits where
    /// the colour is, and the plane behind it runs white to hue across and
    /// transparent to black down — in that order, which is the thing this picture
    /// is really watching.
    @Test
    @DisplayName("the board, at the default without alpha")
    void boardOpaque() {
        paint("color-picker-board", Theme.NORD_DARK, 248, 288, board(HsvColor.ofArgb(NORD_FROST), false, List.of()));
    }

    /// With the alpha ramp, whose chequerboard is what says transparent — a
    /// slider whose left half were the popover's own surface would tell a user
    /// nothing.
    @Test
    @DisplayName("and with the alpha ramp and a palette")
    void boardWithAlpha() {
        paint(
                "color-picker-board-alpha",
                Theme.NORD_DARK,
                248,
                336,
                board(
                        new HsvColor(193, 0.35, 0.82, 0.5),
                        true,
                        List.of(0xFF88C0D0, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B, 0xFFB48EAD, 0xFF5E81AC)));
    }

    /// The cursor picks black or white from the colour under it, which is the one
    /// mark on this control that has to be visible over everything it can sit on.
    @Test
    @DisplayName("the cursor at the bright corner, where it goes black")
    void cursorOnLight() {
        paint(
                "color-picker-board-bright",
                Theme.NORD_DARK,
                248,
                288,
                board(new HsvColor(50, 0.1, 1, 1), false, List.of()));
    }
}
