package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.time.LocalTime;

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

/// What a [TimePicker] holds: the text in its field, and the popover under it.
///
/// `DatePickerState`'s shape, and the same argument for it — §4 makes the typed
/// field the source of truth for both pickers, so what lives here is a `String`
/// and a `LocalTime` is what [TimeFormat#parse] makes of it. The wheels write
/// text into the field exactly as a user would, so a value takes one path and is
/// parsed in one place.
final class TimePickerState extends State<TimePicker> implements PickerField.PickerActions {

    private static final org.slf4j.Logger LOG = Logs.of(TimePickerState.class);

    /// What [#open] asks the popup facility for.
    ///
    /// Zero, for the reason `date-picker` states: a dropdown asks for at least
    /// the width of the control it drops from because a list's rows *stretch*, and
    /// a column set does not — three wheels are three fixed widths, so a floor
    /// would produce a panel as wide as the field with the wheels stranded in it.
    private static final float NO_MINIMUM_WIDTH = 0;

    private String text = "";
    private String committed = "";
    private String lastOffered = "";

    private @Nullable Popup wheels;
    private @Nullable Host host;
    private @Nullable LogicalRect bounds;

    @Override
    protected void initState() {
        super.initState();
        lastOffered = widget().resolved();
        text = lastOffered;
        committed = lastOffered;
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        follow();
        var picker = widget();
        var open = wheels;
        if (isOpen() && open != null) {
            open.content(columns());
        }
        return new PickerField(
                "time-picker",
                new TextInput(text, this::typed)
                        .placeholder(picker.placeholder())
                        .disabled(picker.disabled()),
                isOpen(),
                picker.disabled(),
                picker.attributes(),
                this);
    }

    /// Takes a value the application changed and ignores the echo of the user's
    /// own keystroke — `text-input`'s `follow`, for its reason.
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
    }

    /// What the field currently means, or null when it is not a time yet.
    private @Nullable LocalTime parsed() {
        return widget().resolvedFormat().parse(text);
    }

    /// The wheels, configured from this picker.
    private Widget columns() {
        var picker = widget();
        return new PickerPanel(
                new TimeColumns(parsed(), picker.fallback(), picker.precision(), picker::allows, this::turned));
    }

    // --- the field ------------------------------------------------------------

    /// A keystroke in the field.
    ///
    /// Blank clears the value and is reported as null; a time that parses and is
    /// reachable is committed; anything else is left in the field and reported to
    /// nobody. The three cases are [TimeFormat#parse]'s two answers plus
    /// [TimeFormat#isBlank], which is what tells "cleared" apart from "not yet".
    private void typed(String typed) {
        setState(() -> text = typed);
        if (TimeFormat.isBlank(typed)) {
            commit(null, typed);
            return;
        }
        var time = widget().resolvedFormat().parse(typed);
        if (time == null || !widget().allows(time)) {
            return;
        }
        commit(widget().precision().truncate(time), typed);
    }

    private void commit(@Nullable LocalTime time, String shown) {
        committed = shown;
        var onChange = widget().onChange();
        if (onChange != null) {
            onChange.accept(time);
        }
    }

    /// A wheel turned, or `Enter` was pressed on one.
    ///
    /// It writes text into the field, which is how the two halves stay in step.
    /// The popover stays **open**, unlike a calendar's: a time is three wheels and
    /// closing after the first would make the minutes unreachable. `Enter` and a
    /// press outside are what close it, which is what every wheel picker does.
    private void turned(LocalTime time) {
        var shown = widget().resolvedFormat().format(time);
        if (shown.equals(text)) {
            return;
        }
        setState(() -> text = shown);
        commit(time, shown);
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
                columns(), bounds, Placement.BELOW, NO_MINIMUM_WIDTH, (content, measured, available) -> content);
        if (opened.isEmpty()) {
            LOG.trace("this platform has no popup windows, so a time-picker cannot open its wheels");
            return false;
        }
        setState(() -> wheels = opened.get().lightDismiss(true).takesFocus(false));
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
        setState(() -> text = committed);
        return true;
    }

    @Override
    public void located(LogicalRect self) {
        bounds = self;
    }

    private boolean isOpen() {
        if (wheels != null && !wheels.isOpen()) {
            wheels = null;
        }
        return wheels != null;
    }

    private void close() {
        var open = wheels;
        wheels = null;
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
    String fieldText() {
        return text;
    }
}
