package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.form.parts.PickerField;
import io.github.digitalsmile.goldberry.widgets.form.parts.PickerPanel;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;

/// What a [ColorPicker] holds: the hex in its field, and the hue the hex cannot
/// carry.
///
/// ## Two pieces of state, and the second one is the interesting one
///
/// §4 makes the hex field the source of truth, so `text` is the value — and it is
/// the *only* thing that leaves. But the plane's cursor and the hue ramp cannot be
/// derived from it, because the conversion is lossy exactly where a user drags:
/// every colour with `s == 0` is a grey with no hue, and black has neither hue nor
/// saturation. A picker that re-derived HSV each frame would swing the hue slider
/// to red the moment somebody dragged to the left edge, and lose it entirely at
/// the bottom.
///
/// So `dragging` is the editing state — `TextEdit`'s arrangement, one level up —
/// and the rule that keeps the two honest is: **the text wins whenever it changed
/// under us, and the HSV wins while it is what changed.**
final class ColorPickerState extends State<ColorPicker> implements PickerField.PickerActions {

    private static final org.slf4j.Logger LOG = Logs.of(ColorPickerState.class);

    /// See `DatePickerState`: a board is its own size and cannot stretch, so a
    /// floor would give a panel as wide as the control with the plane in a corner
    /// of it.
    private static final float NO_MINIMUM_WIDTH = 0;

    /// The hex, which is the value. §4's source of truth.
    private String text = "";

    /// The last hex that parsed — what `Esc` puts back.
    private String committed = "";

    private String lastOffered = "";

    /// What the plane and the ramps are showing. Kept because the text cannot
    /// carry a hue — see the class note.
    private HsvColor dragging = HsvColor.BLACK;

    private @Nullable Popup board;
    private @Nullable Host host;
    private @Nullable LogicalRect bounds;

    @Override
    protected void initState() {
        super.initState();
        lastOffered = widget().resolved();
        text = lastOffered;
        committed = lastOffered;
        dragging = HsvColor.ofArgb(argb());
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        follow();
        var picker = widget();
        var open = board;
        if (isOpen() && open != null) {
            open.content(board());
        }
        return new PickerField(
                "color-picker",
                new ColorSwatch(ColorSwatch.Kind.VALUE, argb(), picker.disabled() ? null : colour -> toggle()),
                isOpen(),
                picker.disabled(),
                picker.attributes(),
                this);
    }

    /// Takes a value the application changed and ignores the echo of the user's
    /// own keystroke — `text-input`'s `follow`, for its reason.
    ///
    /// It re-reads the hue as well, and **keeps the one being dragged** when the
    /// new colour has none: an application writing back `#808080` should not swing
    /// the ramp to red. [HsvColor#withArgb] is that rule.
    private void follow() {
        var offered = widget().resolved();
        if (offered.equals(lastOffered)) {
            return;
        }
        lastOffered = offered;
        if (offered.equals(text)) {
            return;
        }
        text = offered;
        committed = offered;
        dragging = dragging.withArgb(argb());
    }

    /// What the field currently means, or the picker's default when it means
    /// nothing.
    private int argb() {
        var parsed = ColorPicker.parse(widget(), text);
        return parsed == null ? ColorPicker.DEFAULT : parsed;
    }

    /// The popover's contents.
    private Widget board() {
        var picker = widget();
        return new PickerPanel(new ColorBoard(
                new ColorPlane(dragging, this::plane, picker.disabled()),
                new ColorRamp(ColorRamp.Kind.HUE, dragging, this::hue, picker.disabled()),
                picker.alpha() ? new ColorRamp(ColorRamp.Kind.ALPHA, dragging, this::alpha, picker.disabled()) : null,
                new TextInput(text, this::typed).placeholder("#000000").disabled(picker.disabled()),
                ColorPicker.palette(picker),
                this::preset));
    }

    // --- the hex field --------------------------------------------------------

    /// A keystroke in the hex field.
    ///
    /// A colour that parses is committed and moves the plane; anything else is
    /// left where it was typed and reported to nobody — `date-picker`'s rule, and
    /// its reason: deleting what somebody typed is how a field loses a keystroke
    /// they were halfway through.
    private void typed(String typed) {
        var parsed = ColorPicker.parse(widget(), typed);
        setState(() -> {
            text = typed;
            if (parsed != null) {
                dragging = dragging.withArgb(parsed);
            }
        });
        if (parsed != null) {
            commit(parsed, typed);
        }
    }

    // --- the plane and the ramps ----------------------------------------------

    /// The plane's cursor moved. Reported through the hex, like everything else.
    private void plane(double saturation, double value) {
        adopt(dragging.withPlane(saturation, value));
    }

    private void hue(double position) {
        adopt(dragging.withHue(position * 360));
    }

    private void alpha(double position) {
        adopt(dragging.withAlpha(position));
    }

    /// A preset was pressed. It replaces the colour outright, hue and all, which
    /// is what choosing a colour from a palette means.
    private void preset(int argb) {
        adopt(HsvColor.ofArgb(widget().gate(argb)));
    }

    /// Takes `next`, writes its hex into the field, and reports it.
    ///
    /// The gate is applied here as well as in the field, which is not belt and
    /// braces: `alpha=#false` "refuses translucent values", and a ramp is the one
    /// place a translucent one could otherwise be produced without anybody typing
    /// it.
    private void adopt(HsvColor next) {
        var gated = widget().alpha() ? next : next.opaque();
        var hex = gated.toHex();
        setState(() -> {
            dragging = gated;
            text = hex;
        });
        commit(gated.toArgb(), hex);
    }

    private void commit(int argb, String shown) {
        committed = shown;
        var onChange = widget().onChange();
        if (onChange != null) {
            onChange.accept(argb);
        }
    }

    // --- PickerActions --------------------------------------------------------

    @Override
    public boolean open() {
        var picker = widget();
        if (picker.disabled() || isOpen()) {
            return false;
        }
        if (host == null || bounds == null) {
            return false;
        }
        var opened = host.attachedPopup(
                board(), bounds, Placement.BELOW, NO_MINIMUM_WIDTH, (content, measured, available) -> content);
        if (opened.isEmpty()) {
            LOG.trace("this platform has no popup windows, so a color-picker cannot open its board");
            return false;
        }
        setState(() -> board = opened.get().lightDismiss(true).takesFocus(false));
        return true;
    }

    @Override
    public void toggle() {
        if (isOpen()) {
            close();
            return;
        }
        open();
    }

    @Override
    public boolean revert() {
        if (text.equals(committed)) {
            return false;
        }
        var restored = committed;
        setState(() -> text = restored);
        return true;
    }

    @Override
    public void located(LogicalRect self) {
        bounds = self;
    }

    private boolean isOpen() {
        if (board != null && !board.isOpen()) {
            board = null;
        }
        return board != null;
    }

    private void close() {
        var open = board;
        board = null;
        if (open != null && open.isOpen()) {
            open.close();
        }
    }

    @Override
    protected void dispose() {
        close();
        super.dispose();
    }

    /// What the field holds, for a test.
    String hexText() {
        return text;
    }

    /// What the plane and the ramps are showing, for a test.
    HsvColor draggingColour() {
        return dragging;
    }
}
