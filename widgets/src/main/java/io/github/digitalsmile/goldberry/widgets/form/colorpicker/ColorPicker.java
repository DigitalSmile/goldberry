package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A swatch button over a plane, two ramps and a hex field —
/// `docs/core-widgets.md` §4's `color-picker`.
///
/// ```kdl
/// field label="Accent" { color-picker bind="theme.accent" change="theme.set-accent" }
/// color-picker alpha=#true value="#88c0d0"
/// ```
///
/// ```java
/// ColorPicker.of(accent, this::recolour).presets(List.of(0xFF88C0D0, 0xFFBF616A))
/// ```
///
/// ## What it is made of
///
/// ```
/// color-picker           this node. Stateful, styles nothing, holds the hex
/// └── color-picker       PickerField — the keys, the popover, the affordance
///     ├── color-swatch   the swatch button §4 opens from
///     └── picker-toggle  the chevron beside it
/// ```
///
/// and in the popover:
///
/// ```
/// picker-panel
/// └── color-board        [ColorBoard]
///     ├── color-plane    saturation across, value up
///     ├── color-ramp.hue
///     ├── color-ramp.alpha   only when `alpha` is on
///     ├── text-input     the hex field
///     └── color-presets  the application's swatches
/// ```
///
/// **`date-picker`'s control with a different popover and a different field.**
/// The one departure from the other two pickers is what the closed control shows:
/// §4 says "a swatch button", so it is a swatch rather than a `text-input`, and
/// the hex field lives *inside* the popover where §4 puts it.
///
/// ## The hex field is the source of truth
///
/// §4 says so in as many words — "the hex field is the source of truth for the
/// same reason the date field is" — and the reason is `DatePicker`'s: a control
/// that held a colour and rendered it into the field would have to decide what the
/// field says while somebody is halfway through typing `#88c`.
///
/// So [ColorPickerState] holds **text**, and the plane and the ramps write into it
/// exactly as a user would. What they *also* hold, and the text cannot, is the
/// hue: every colour with no saturation is a grey with no hue, so a picker that
/// re-derived HSV from the hex each frame would swing the hue slider to red the
/// moment somebody dragged to the left edge. See [HsvColor].
///
/// ## HSV, not OKLCH
///
/// §4 asks for an OKLCH model and this one is HSV, which is a departure with a
/// reason: a rectangular saturation/value plane over OKLCH has large unreachable
/// regions, because OKLCH chroma has a gamut boundary that varies with hue and
/// lightness. [HsvColor] gives the argument in full. `Oklch` keeps the job §1.7
/// gave it — every colour *transition* still goes through it — and this control
/// interpolates nothing.
///
/// @param value      the hex the field starts with when nothing is bound
/// @param source     the `bind=` value, or null — a colour or its text
/// @param onChange   told the colour as `0xAARRGGBB` whenever one is committed
/// @param alpha      §4's `alpha=`: whether there is an alpha ramp at all
/// @param presets    the application's palette, possibly empty
/// @param disabled   whether it refuses focus and matches `:disabled`
/// @param attributes the `id`, classes and key the document wrote
@Markup("color-picker")
public record ColorPicker(
        String value,
        @Nullable Observable<?> source,
        @Nullable IntConsumer onChange,
        boolean alpha,
        List<Integer> presets,
        boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<ColorPicker>, Bindable<ColorPicker> {

    private static final Logger LOG = Logs.of(ColorPicker.class);

    /// What a picker holding nothing shows, and what an unparseable `value=`
    /// falls back to.
    public static final int DEFAULT = 0xFF000000;

    public ColorPicker {
        value = value == null ? "" : value;
        presets = List.copyOf(presets == null ? List.<Integer>of() : presets);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// An opaque black picker with no alpha ramp.
    public ColorPicker() {
        this("", null, null, false, List.of(), false, Attributes.NONE);
    }

    /// A picker showing `hex` and reporting every colour it commits.
    public ColorPicker(String hex, @Nullable IntConsumer onChange) {
        this(hex, null, onChange, false, List.of(), false, Attributes.NONE);
    }

    /// A picker following a property. The Java spelling of `bind=`.
    public static ColorPicker of(Observable<?> source, @Nullable IntConsumer onChange) {
        return new ColorPicker(
                "", Objects.requireNonNull(source, "source"), onChange, false, List.of(), false, Attributes.NONE);
    }

    /// This picker holding `hex` when nothing is bound.
    public ColorPicker value(String hex) {
        return new ColorPicker(
                Objects.requireNonNull(hex, "hex"), source, onChange, alpha, presets, disabled, attributes);
    }

    /// This picker with §4's alpha slider.
    ///
    /// `alpha=#false` is the default and "hides the alpha slider **and refuses
    /// translucent values**" — the second half matters as much as the first: a
    /// picker with no way to change alpha must not report one, or a `bind=`
    /// carrying `#88c0d080` would leave the control showing a colour it cannot
    /// express and a form holding one nobody chose.
    public ColorPicker alpha(boolean translucent) {
        return new ColorPicker(value, source, onChange, translucent, presets, disabled, attributes);
    }

    /// This picker offering `palette` — §4's "application-supplied palette of
    /// preset swatches".
    public ColorPicker presets(List<Integer> palette) {
        return new ColorPicker(
                value, source, onChange, alpha, Objects.requireNonNull(palette, "palette"), disabled, attributes);
    }

    /// This picker refusing focus and every press.
    public ColorPicker disabled(boolean refused) {
        return new ColorPicker(value, source, onChange, alpha, presets, refused, attributes);
    }

    @Override
    public ColorPicker withAttributes(Attributes replacement) {
        return new ColorPicker(value, source, onChange, alpha, presets, disabled, replacement);
    }

    @Override
    public ColorPicker bound(Observable<?> binding) {
        return new ColorPicker(value, binding, onChange, alpha, presets, disabled, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// What the picker starts from, as hex.
    ///
    /// A binding may hold an `Integer` — which is what `CssColor` is, so a model
    /// that keeps colours as colours is the common case — or a string, which is a
    /// model that keeps them as CSS. Both are meant, and `DatePicker#resolved`
    /// gives the argument.
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return switch (current) {
            case null -> "";
            case Integer argb -> HsvColor.hex(gate(argb));
            default -> String.valueOf(current);
        };
    }

    /// `argb` made acceptable to this picker — opaque unless it has an alpha
    /// ramp.
    ///
    /// The one gate this control has, and the pair to `date-picker`'s `allows`:
    /// asked by the field that parsed a colour, by the ramps before they move it
    /// and by a preset before it is offered, so there is no way for the three to
    /// disagree.
    public int gate(int argb) {
        return alpha ? argb : argb | 0xFF000000;
    }

    @Override
    public State<?> createState() {
        return new ColorPickerState();
    }

    /// Builds a `color-picker` from markup.
    ///
    /// `presets=` is deliberately absent: §4 calls the palette
    /// **application-supplied**, and a list of colours is not something §9's
    /// property syntax carries — the same reason `calendar` has no `@Markup` at
    /// all. A document gets the plane, the ramps and the field, which is the whole
    /// control minus a shortcut.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var reported = wiring.valued(node, "change");
        return new ColorPicker(
                Objects.requireNonNullElse(node.stringProperty("value"), ""),
                wiring.bound(node),
                // A document's `change` carries **hex**, because §9's valued
                // actions cross as a `String`. The two other pickers make the same
                // split for the same reason, and here the text is the value's own
                // spelling rather than a formatting choice.
                reported == null ? null : argb -> reported.accept(HsvColor.hex(argb)),
                node.booleanProperty("alpha"),
                List.of(),
                Wiring.disabled(node),
                Attributes.of(node));
    }

    /// The palette an application handed over, with anything unusable dropped.
    ///
    /// Logged rather than thrown, exactly as an unknown `filter=` is: a preset
    /// that does not parse is a typo already visible in the code, and a picker
    /// that refused to open is a worse way to find out.
    static List<Integer> palette(ColorPicker picker) {
        var usable = new ArrayList<Integer>(picker.presets().size());
        for (var preset : picker.presets()) {
            if (preset == null) {
                LOG.warn("a color-picker preset was null; dropping it");
                continue;
            }
            usable.add(picker.gate(preset));
        }
        return List.copyOf(usable);
    }

    /// Whether `text` names a colour this picker would accept.
    static @Nullable Integer parse(ColorPicker picker, String text) {
        var argb = HsvColor.parse(text);
        return argb == null ? null : picker.gate(argb);
    }
}
