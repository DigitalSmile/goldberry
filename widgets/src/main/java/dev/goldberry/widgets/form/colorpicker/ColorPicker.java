package dev.goldberry.widgets.form.colorpicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.attr.Bindable;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A swatch button that opens a popover with a saturation/value plane, a hue
/// ramp, an optional alpha ramp, a hex field and the application's presets.
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
///     ├── color-swatch   the swatch button the popover opens from
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
/// a swatch button rather than a `text-input`, with the hex field *inside* the
/// popover.
///
/// ## The hex field is the source of truth
///
/// For the same reason the date field is `DatePicker`'s: a control that held a
/// colour and rendered it into the field would have to decide what the field
/// says while somebody is halfway through typing `#88c`.
///
/// So [ColorPickerState] holds **text**, and the plane and the ramps write into it
/// exactly as a user would. What they *also* hold, and the text cannot, is the
/// hue: every colour with no saturation is a grey with no hue, so a picker that
/// re-derived HSV from the hex each frame would swing the hue slider to red the
/// moment somebody dragged to the left edge. See [HsvColor].
///
/// ## HSV, not OKLCH
///
/// The toolkit's colour transitions run in OKLCH; this control's model is HSV,
/// for a reason: a rectangular saturation/value plane over OKLCH has large
/// unreachable regions, because OKLCH chroma has a gamut boundary that varies
/// with hue and lightness. [HsvColor] gives the argument in full. `Oklch` keeps
/// its job — every colour *transition* still goes through it — and this control
/// interpolates nothing.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#color-picker).
///
/// @param value      the hex the field starts with when nothing is bound
/// @param source     the `bind=` value, or null — a colour or its text
/// @param onChange   told the colour as `0xAARRGGBB` whenever one is committed
/// @param alpha      `alpha=`: whether there is an alpha ramp at all
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

    /// What a picker holding nothing shows, and what an unparseable `value=`
    /// falls back to.
    public static final int DEFAULT = 0xFF000000;

    /// Written out so that the parameters taking null for a default can say so.
    public ColorPicker(
            @Nullable String value,
            @Nullable Observable<?> source,
            @Nullable IntConsumer onChange,
            boolean alpha,
            @Nullable List<Integer> presets,
            boolean disabled,
            @Nullable Attributes attributes) {
        value = value == null ? "" : value;
        presets = List.copyOf(presets == null ? List.<Integer>of() : presets);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.value = value;
        this.source = source;
        this.onChange = onChange;
        this.alpha = alpha;
        this.presets = presets;
        this.disabled = disabled;
        this.attributes = attributes;
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

    /// This picker with an alpha slider.
    ///
    /// `alpha=#false` is the default: it hides the alpha slider **and refuses
    /// translucent values** — the second half matters as much as the first: a
    /// picker with no way to change alpha must not report one, or a `bind=`
    /// carrying `#88c0d080` would leave the control showing a colour it cannot
    /// express and a form holding one nobody chose.
    public ColorPicker alpha(boolean translucent) {
        return new ColorPicker(value, source, onChange, translucent, presets, disabled, attributes);
    }

    /// This picker offering `palette`, the application's own preset swatches.
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
    /// `presets=` is deliberately absent: the palette is
    /// **application-supplied**, and a list of colours is not something markup's
    /// property syntax carries — the same reason `calendar` has no `@Markup` at
    /// all. A document gets the plane, the ramps and the field, which is the whole
    /// control minus a shortcut.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var reported = wiring.valued(node, "change");
        return new ColorPicker(
                Objects.requireNonNullElse(node.stringProperty("value"), ""),
                wiring.bound(node),
                // A document's `change` carries **hex**, because markup's valued
                // actions cross as a `String`. The two other pickers make the same
                // split for the same reason, and here the text is the value's own
                // spelling rather than a formatting choice.
                reported == null ? null : argb -> reported.accept(HsvColor.hex(argb)),
                node.booleanProperty("alpha"),
                List.of(),
                Wiring.disabled(node),
                Attributes.of(node));
    }

    /// The palette an application handed over, each colour through [#gate].
    ///
    /// Nothing is dropped: the constructor's `List.copyOf` has already refused a
    /// null preset.
    static List<Integer> palette(ColorPicker picker) {
        var usable = new ArrayList<Integer>(picker.presets().size());
        for (var preset : picker.presets()) {
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
