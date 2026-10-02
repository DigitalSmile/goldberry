package dev.goldberry.widgets.form.colorpicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// What a [ColorPicker]'s popover holds — `color-board`, a **part**.
///
/// ```
/// color-board          this node
/// ├── color-plane      the saturation/value plane
/// ├── color-ramp.hue   the hue slider
/// ├── color-ramp.alpha the alpha slider, when there is one
/// ├── text-input       the hex field — the source of truth
/// └── color-presets    the application's swatches, when there are any
/// ```
///
/// Everything a colour picker has, in this order, and the order is not
/// arbitrary: the plane is what a pointer reaches for, the ramps are what change
/// what the plane shows, and the hex field is what a keyboard reaches for and
/// what the whole thing is really holding. Presets last, because they are a
/// shortcut past all of it.
///
/// The hex field is a **real `text-input`**, like every other picker's field, so
/// the caret, the selection, the clipboard and the undo stack are the ones with
/// rules in them rather than a second set.
///
/// @param plane   the saturation/value plane
/// @param hue     the hue ramp
/// @param alpha   the alpha ramp, or null when the picker refuses translucency
/// @param hex     the hex field
/// @param presets the application's swatches, possibly empty
record ColorBoard(
        Widget plane, Widget hue, @Nullable Widget alpha, Widget hex, List<Integer> presets, IntConsumer onPreset)
        implements Widget.Leaf, Styled, Paints {

    ColorBoard {
        presets = List.copyOf(presets);
    }

    @Override
    public String cssType() {
        return "color-board";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(5);
        parts.add(plane);
        parts.add(hue);
        if (alpha != null) {
            parts.add(alpha);
        }
        parts.add(hex);
        if (!presets.isEmpty()) {
            parts.add(new ColorPresets(presets, onPreset));
        }
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// The application-supplied palette of preset swatches — `color-presets`, a
    /// **part**.
    ///
    /// A wrapping row, because a palette is however many colours an application
    /// has and a picker 200 points wide fits eight of them: the metrics size the swatch
    /// and the gap and says nothing about how many fit, which is a question only
    /// the width can answer. The CSS subset has `flex-wrap`, so the stylesheet
    /// answers it.
    record ColorPresets(List<Integer> colours, IntConsumer onPress) implements Widget.Leaf, Styled, Paints {

        ColorPresets {
            colours = List.copyOf(colours);
        }

        @Override
        public String cssType() {
            return "color-presets";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public List<Widget> children() {
            var swatches = new ArrayList<Widget>(colours.size());
            for (var colour : colours) {
                swatches.add(new ColorSwatch(ColorSwatch.Kind.PRESET, colour, onPress));
            }
            return List.copyOf(swatches);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }
}
