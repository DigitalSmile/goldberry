package io.github.digitalsmile.goldberry.widgets.form.datepicker;

import java.time.LocalDate;
import java.time.YearMonth;

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
import io.github.digitalsmile.goldberry.widgets.panel.calendar.CalendarView;
import io.github.digitalsmile.goldberry.widgets.panel.calendar.DateSelection;

/// What a [DatePicker] holds: the text in its field, and the popover under it.
///
/// ## The text is the state, and the date is derived
///
/// §4: "the **typed field is the source of truth**, not the popup". So what lives
/// here is a `String`, and a `LocalDate` is what [DateFormat#parse] makes of it
/// when it is asked. The alternative — holding the date and rendering it into the
/// field — has to answer "what does the field say while somebody is halfway
/// through typing", and every answer to that either fights the caret or throws
/// away characters.
///
/// It is also what makes the two halves agree without either watching the other:
/// the grid **writes text** into the field exactly as a user would, so there is
/// one path a value takes and one place it is parsed.
final class DatePickerState extends State<DatePicker> implements PickerField.PickerActions {

    private static final org.slf4j.Logger LOG = Logs.of(DatePickerState.class);

    /// What [#open] asks the popup facility for — see the note there.
    ///
    /// Named rather than a bare zero, because zero is the *answer* to a question
    /// ("how wide must this be at least?") and the interesting thing about it is
    /// that a picker asks a different one from `select`.
    private static final float NO_MINIMUM_WIDTH = 0;

    /// What the field holds. The whole of this widget's state, plus the popover.
    private String text = "";

    /// The last value that parsed — what `Esc` puts back, and the reason a
    /// half-typed date is not a value being lost.
    private String committed = "";

    /// The value the widget last offered, so a *change* to it can be told from a
    /// value that has always been there. `text-input`'s `lastOffered`, unchanged.
    private String lastOffered = "";

    private @Nullable Popup grid;

    private @Nullable Host host;

    /// Where the last frame drew the control, for anchoring the popover.
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
        // Re-described rather than reopened, so a keystroke that moves the grid's
        // selection does not close and reopen a platform window — `select`'s rule
        // for its narrowing list (ADR-0182).
        var open = grid;
        if (isOpen() && open != null) {
            open.content(calendar());
        }
        return new PickerField(
                "date-picker",
                new TextInput(text, this::typed)
                        .placeholder(picker.placeholder())
                        .disabled(picker.disabled()),
                isOpen(),
                picker.disabled(),
                picker.attributes(),
                this);
    }

    /// Takes a value the application changed and ignores the echo of the user's
    /// own keystroke — `text-input`'s [#follow], for its reason.
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

    /// What the field currently means, or null when it means nothing.
    private @Nullable DateSelection parsed() {
        return widget().format().parse(text, widget().mode());
    }

    /// What the grid should show — the parsed value when there is one, and
    /// nothing when the field is mid-word.
    private DateSelection selection() {
        var value = parsed();
        return value == null ? DateSelection.empty(widget().mode()) : value;
    }

    /// The calendar in the popover, configured from this picker.
    ///
    /// Every gate is handed straight over, which is §4's "gate both the field and
    /// the grid": one predicate, two readers, and no way for them to disagree.
    private Widget calendar() {
        var picker = widget();
        var start = selection().first();
        return new PickerPanel(
                new CalendarView(selection(), this::chose, start == null ? picker.month() : YearMonth.from(start))
                        .today(picker.today())
                        .between(picker.min(), picker.max())
                        .disabledDates(picker.disabledDates())
                        .locale(picker.locale()));
    }

    // --- the field ------------------------------------------------------------

    /// A keystroke in the field.
    ///
    /// The value is committed as soon as it parses **and is reachable**, which is
    /// what makes typing and picking the same act: §4 gives the field no separate
    /// confirmation, and a date that only counted on blur would leave a form
    /// holding an older value than the one on screen.
    ///
    /// A date the bounds refuse is left in the field and **not** committed. §4:
    /// "an unreachable date cannot be typed either" — and the text stays, because
    /// deleting what somebody typed is how a field loses a keystroke they were
    /// halfway through.
    private void typed(String typed) {
        setState(() -> text = typed);
        var value = widget().format().parse(typed, widget().mode());
        if (value == null || !reachable(value)) {
            return;
        }
        commit(value, typed);
    }

    /// Whether every date in `value` is one this picker allows.
    private boolean reachable(DateSelection value) {
        return value.dates().stream().allMatch(widget()::allows);
    }

    private void commit(DateSelection value, String shown) {
        committed = shown;
        var onChange = widget().onChange();
        if (onChange != null) {
            onChange.accept(value);
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
            // A golden image and a layout preview build the same widget with no
            // host behind it, and a control that threw there could not be drawn
            // at all (ADR-0140).
            return false;
        }
        // **Attached**, not a menu: the field is the source of truth and a
        // focusable popup would take the keyboard off it (ADR-0186). The arrows
        // still reach the grid, because while a popup is open the keyboard
        // belongs to it (ADR-0104).
        //
        // **No minimum width, and that is the difference from `select`.** A
        // dropdown asks for at least the width of the control it drops from,
        // because a list narrower than its field reads as a mistake (ADR-0145) —
        // and a list's rows *stretch* to fill whatever it is given. A month grid
        // does not: it is seven cells of `--gb-calendar-day` and cannot be any
        // other width, so a floor produces a panel as wide as the field with the
        // grid stranded at one end of it. The first version passed the field's
        // width here and that is exactly what it looked like.
        //
        // The `Fit` is the identity for the same reason: six rows of seven cells
        // is the same size whatever is in it, so there is nothing to shrink.
        // `menu` and `select` hand over a `Fitted` because a list can be taller
        // than the screen; this cannot.
        var opened = host.attachedPopup(
                calendar(), bounds, Placement.BELOW, NO_MINIMUM_WIDTH, (content, measured, available) -> content);
        if (opened.isEmpty()) {
            LOG.info("this platform has no popup windows, so a date-picker cannot open its calendar");
            return false;
        }
        setState(() -> grid = opened.get().lightDismiss(true).takesFocus(false));
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

    /// §4's `Esc`: the last value that parsed goes back into the field.
    ///
    /// @return whether there was anything to put back, which is what keeps an
    ///         `Escape` on an untouched picker available to the dialog around it
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

    /// A day was chosen in the grid.
    ///
    /// It **writes text into the field**, which is the whole of how the two halves
    /// stay in step: the grid does what a user would do, and the field parses it
    /// like anything else it was given.
    ///
    /// The popover stays open for an incomplete range and closes on a whole
    /// value, because a range needs a second press and closing after the first
    /// would make it unpickable.
    private void chose(DateSelection value) {
        var shown = widget().format().format(value);
        setState(() -> text = shown);
        commit(value, shown);
        if (widget().mode() != DateSelection.Mode.RANGE || value.last() != null) {
            close();
        }
    }

    /// Whether the grid is showing, allowing for a popup that dismissed itself.
    private boolean isOpen() {
        if (grid != null && !grid.isOpen()) {
            grid = null;
        }
        return grid != null;
    }

    private void close() {
        var open = grid;
        grid = null;
        if (open != null && open.isOpen()) {
            open.close();
        }
    }

    @Override
    protected void dispose() {
        // An element that goes away with its calendar showing would leave a
        // platform window parented to nothing — the one leak a widget can cause,
        // because a popup is not a value and is not collected with the tree.
        close();
        super.dispose();
    }

    /// What the field holds, for a test.
    String fieldText() {
        return text;
    }

    /// The date the field currently means, or null.
    @Nullable
    LocalDate date() {
        var value = parsed();
        return value == null ? null : value.first();
    }
}
