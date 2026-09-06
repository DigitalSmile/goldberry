package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.form.parts.PickerField;

/// §4's colour field, driven the way a user drives it.
///
/// The colour model is [HsvColorTest]'s. What is here is the seam: that the hex
/// field is the source of truth, that the plane and the ramps write into it, that
/// `alpha=#false` refuses translucency in **both** directions, and what the closed
/// swatch does.
class ColorPickerTest {

    private static final LogicalRect BOUNDS = new LogicalRect(new LogicalPoint(12, 40), new LogicalSize(240, 32));

    private final TestHost host = new TestHost();

    private ElementTree mounted(ColorPicker picker) {
        var tree = new ElementTree(picker, host);
        render(tree);
        return tree;
    }

    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private PickerField box(ElementTree tree) {
        return (PickerField) tree.root().children().getFirst().widget();
    }

    private ColorPickerState state(ElementTree tree) {
        return (ColorPickerState) tree.root().state().orElseThrow();
    }

    /// The popover's contents, built the way the state builds them — a golden has
    /// no window and neither does this, so the board is reached through the state
    /// rather than through a popup.
    private ColorBoard board(ElementTree tree) {
        box(tree).located(BOUNDS, BOUNDS);
        box(tree).picker().toggle();
        render(tree);
        var panel = (io.github.digitalsmile.goldberry.widgets.form.parts.PickerPanel)
                host.opened.getLast().content();
        return (ColorBoard) panel.content();
    }

    private String hex(ElementTree tree) {
        return state(tree).hexText();
    }

    @Nested
    @DisplayName("the hex field is the source of truth")
    class Typing {

        @Test
        @DisplayName("a colour typed into it is reported as soon as it parses")
        void typed() {
            var chosen = new ArrayList<Integer>();
            var tree = mounted(new ColorPicker("", chosen::add));
            var hexField = board(tree).hex();

            ((io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput) hexField)
                    .onChange()
                    .accept("#88c0d0");
            render(tree);

            assertEquals(List.of(0xFF88C0D0), chosen);
            assertEquals("#88c0d0", hex(tree));
        }

        @Test
        @DisplayName("a half-typed colour reports nothing and keeps what was typed")
        void halfTyped() {
            var chosen = new ArrayList<Integer>();
            var tree = mounted(new ColorPicker("", chosen::add));

            ((io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput)
                            board(tree).hex())
                    .onChange()
                    .accept("#88c");
            render(tree);

            // `#88c` is CSS's three-digit form, so it *is* a colour, and the
            // picker is right to take it — the genuinely half-typed case is one
            // character further along.
            assertEquals(List.of(0xFF8888CC), chosen);

            ((io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput)
                            board(tree).hex())
                    .onChange()
                    .accept("#88c0");
            render(tree);
            assertEquals("#88c0", hex(tree));
        }
    }

    @Nested
    @DisplayName("the plane and the ramps")
    class Dragging {

        /// They write hex into the field exactly as a user would, which is how the
        /// two halves stay in step with one parser between them.
        @Test
        @DisplayName("a drag on the plane writes hex into the field")
        void plane() {
            var chosen = new ArrayList<Integer>();
            var tree = mounted(new ColorPicker("#ff0000", chosen::add));

            press((ColorPlane) board(tree).plane(), 0.5, 0.5);
            render(tree);

            assertEquals(HsvColor.hex(chosen.getLast()), hex(tree));
        }

        /// The reason the picker keeps an `HsvColor` beside the text: dragging to
        /// the left edge is dragging to a grey, and a grey has no hue to put back.
        @Test
        @DisplayName("dragging to the grey edge keeps the hue for the slider")
        void keepsHue() {
            var tree = mounted(new ColorPicker("#00a0ff", null));
            var hue = state(tree).draggingColour().hue();

            press((ColorPlane) board(tree).plane(), 0, 1);
            render(tree);

            assertEquals(0, state(tree).draggingColour().saturation(), 0.001);
            assertEquals(hue, state(tree).draggingColour().hue(), 0.001);
        }

        /// §4: "arrows move the plane cursor by 1, `Shift`+arrows by 10" — of the
        /// plane's own hundred steps, which is what the model is in.
        @Test
        @DisplayName("arrows move the cursor by one step and Shift by ten")
        void arrows() {
            var tree = mounted(new ColorPicker("#808080", null));
            var plane = (ColorPlane) board(tree).plane();
            var before = state(tree).draggingColour().saturation();

            plane.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE, false, null));
            render(tree);
            assertEquals(before + ColorPlane.STEP, state(tree).draggingColour().saturation(), 0.001);

            ((ColorPlane) board(tree).plane())
                    .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.of(Mod.SHIFT), false, null));
            render(tree);
            assertEquals(
                    before + ColorPlane.STEP + ColorPlane.COARSE_STEP,
                    state(tree).draggingColour().saturation(),
                    0.001);
        }

        /// A control with the keyboard owns its arrows, or `Left` would walk the
        /// focus scope of the popover it is in.
        @Test
        @DisplayName("and are consumed even at an edge")
        void consumedAtTheEdge() {
            var tree = mounted(new ColorPicker("#000000", null));
            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT, Modifiers.NONE, false, null);

            ((ColorPlane) board(tree).plane()).onKey(event);

            assertTrue(event.isConsumed());
        }

        @Test
        @DisplayName("the hue ramp turns the wheel")
        void hue() {
            var tree = mounted(new ColorPicker("#ff0000", null));

            press((ColorRamp) board(tree).hue(), 0.5, 0.5);
            render(tree);

            assertEquals(180, state(tree).draggingColour().hue(), 1);
        }

        private static void press(io.github.digitalsmile.goldberry.input.handler.Handles target, double x, double y) {
            var event = new PointerEvent(
                    PointerEvent.Kind.PRESSED,
                    0,
                    0,
                    PointerEvent.Button.PRIMARY,
                    1,
                    Float.NaN,
                    Float.NaN,
                    Modifiers.NONE,
                    null);
            event.localTo(new PointerEvent.Local((float) (x * 200), (float) (y * 160), 200, 160));
            target.onPointer(event);
        }
    }

    @Nested
    @DisplayName("alpha")
    class Translucency {

        /// §4: "`alpha=#false` (the default) hides the alpha slider **and refuses
        /// translucent values**". Both halves, because a picker with no way to
        /// change alpha must not report one.
        @Test
        @DisplayName("is off by default, and there is no ramp for it")
        void hidden() {
            assertEquals(null, board(mounted(new ColorPicker())).alpha());
            assertTrue(board(mounted(new ColorPicker().alpha(true))).alpha() != null);
        }

        @Test
        @DisplayName("and a translucent value typed or bound comes back opaque")
        void refused() {
            var bound = Property.of(0x8088C0D0);
            var tree = mounted(ColorPicker.of(bound, null));

            assertEquals("#88c0d0", hex(tree));
        }

        @Test
        @DisplayName("but a picker that has one keeps it")
        void kept() {
            var bound = Property.of(0x8088C0D0);
            var tree = mounted(ColorPicker.of(bound, null).alpha(true));

            assertEquals("#88c0d080", hex(tree));
        }
    }

    @Nested
    @DisplayName("the closed control")
    class Swatch {

        /// §4 calls it "a swatch button", so it is one — focusable, and `Space`
        /// opens the popover.
        @Test
        @DisplayName("is a focusable swatch and not a field")
        void swatchButton() {
            var tree = mounted(new ColorPicker("#88c0d0", null));
            var swatch = (ColorSwatch) box(tree).field();

            assertEquals(ColorSwatch.Kind.VALUE, swatch.kind());
            assertEquals(0xFF88C0D0, swatch.argb());
            assertTrue(swatch.isFocusable());
            assertEquals("#88c0d0", swatch.accessibleName());
        }

        /// A palette of twelve colours would be twelve Tab stops inside a popover,
        /// and §4 gives them no roving mechanism to be one stop with.
        @Test
        @DisplayName("and a preset is not focusable")
        void presetsAreNotTabStops() {
            var tree = mounted(new ColorPicker().presets(List.of(0xFF88C0D0)));
            var presets = (ColorBoard.ColorPresets) board(tree).children().getLast();
            var swatch = (ColorSwatch) presets.children().getFirst();

            assertFalse(swatch.isFocusable());
        }

        @Test
        @DisplayName("a preset replaces the colour outright")
        void preset() {
            var chosen = new ArrayList<Integer>();
            var tree = mounted(new ColorPicker("#ff0000", chosen::add).presets(List.of(0xFF88C0D0)));
            var presets = (ColorBoard.ColorPresets) board(tree).children().getLast();
            var swatch = (ColorSwatch) presets.children().getFirst();

            var event = new PointerEvent(
                    PointerEvent.Kind.CLICKED,
                    0,
                    0,
                    PointerEvent.Button.PRIMARY,
                    1,
                    Float.NaN,
                    Float.NaN,
                    Modifiers.NONE,
                    null);
            swatch.onPointer(event);
            render(tree);

            assertEquals(List.of(0xFF88C0D0), chosen);
            assertEquals("#88c0d0", hex(tree));
        }

        @Test
        @DisplayName("a disabled picker has no press at all")
        void disabled() {
            var tree = mounted(new ColorPicker().disabled(true));
            var swatch = (ColorSwatch) box(tree).field();

            assertFalse(swatch.isFocusable());
        }
    }

    @Nested
    @DisplayName("the popover")
    class Popover {

        @Test
        @DisplayName("asks for no minimum width, because a board cannot stretch")
        void noMinimumWidth() {
            var tree = mounted(new ColorPicker());
            box(tree).located(BOUNDS, BOUNDS);

            box(tree).picker().toggle();
            render(tree);

            assertEquals(0f, host.opened.getLast().minimumWidth());
        }
    }

    @Nested
    @DisplayName("what a document writes")
    class FromMarkup {

        private ColorPicker inflated(String kdl) {
            return (ColorPicker)
                    Widgets.inflater().inflateAll(KdlParser.parse(kdl)).getFirst();
        }

        @Test
        @DisplayName("value and alpha")
        void properties() {
            var picker = inflated("color-picker value=\"#88c0d0\" alpha=#true");

            assertEquals("#88c0d0", picker.value());
            assertTrue(picker.alpha());
        }

        /// §4 calls the palette application-supplied, and a list of colours is not
        /// something §9's property syntax carries — `calendar`'s reason for having
        /// no `@Markup` at all, one option down.
        @Test
        @DisplayName("but not the palette, which a document cannot carry")
        void noPresets() {
            assertTrue(inflated("color-picker").presets().isEmpty());
        }
    }
}
