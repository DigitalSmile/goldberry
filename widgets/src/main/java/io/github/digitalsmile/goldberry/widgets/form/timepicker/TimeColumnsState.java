package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Locale;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// What the wheels hold: the time they are showing, and which column the keyboard
/// is on.
///
/// ## The wheels report on every turn, and that is the difference from a calendar
///
/// A calendar's roving day is not a selection — a user arrowing across a month
/// has chosen nothing, and only `Enter` or a press reports anything. A time
/// picker's wheels are the opposite and have to be: there is no "not yet" state
/// for an hour, because the columns always show *some* time and a user turning
/// one is changing the value they can see. So every step reports, which is also
/// what makes the field update as the wheel turns — §4 puts the field in charge,
/// and a field that only caught up on `Enter` would be showing a stale time next
/// to a wheel showing the real one.
///
/// `Enter` therefore commits what is already committed, which is not a no-op: it
/// is what closes the popover, and a control that offered no way to say "done"
/// from the keyboard would be one a keyboard cannot finish with.
final class TimeColumnsState extends State<TimeColumns> implements TimeColumnsBox.TimeKeys {

    /// How many values each column has, in column order.
    private static final int[] LIMITS = {24, 60, 60};

    private LocalTime shown = LocalTime.MIDNIGHT;

    /// Which column the arrows are on. Zero is the hours.
    private int column;

    @Override
    protected void initState() {
        super.initState();
        shown = widget().shown();
    }

    /// A value arriving from the picker moves the wheels.
    ///
    /// Only when it differs from what is shown, so a rebuild caused by this
    /// state's own report does not fight it.
    @Override
    protected void didUpdateWidget(TimeColumns previous) {
        super.didUpdateWidget(previous);
        var wanted = widget().shown();
        if (!wanted.equals(shown)) {
            shown = wanted;
        }
    }

    @Override
    public Widget build(BuildContext context) {
        var columns = new ArrayList<Widget>(widget().precision().columns());
        for (var index = 0; index < widget().precision().columns(); index++) {
            columns.add(wheel(index));
        }
        return new TimeColumnsBox(columns, false, this);
    }

    /// One wheel: [TimeColumns#VISIBLE_ROWS] rows centred on the column's value,
    /// wrapping at both ends.
    private Widget wheel(int index) {
        var limit = LIMITS[index];
        var current = valueOf(index);
        var half = TimeColumns.VISIBLE_ROWS / 2;
        var cells = new ArrayList<Widget>(TimeColumns.VISIBLE_ROWS);
        for (var offset = -half; offset <= half; offset++) {
            var value = Math.floorMod(current + offset, limit);
            cells.add(new TimeCell(
                    // Two digits always, because a column whose rows were "9" and
                    // "10" would jump about as it turned.
                    //
                    // **In [Locale#ROOT]**, and for `Slider#text()`'s reason rather
                    // than for tidiness: `%02d` under the *default* locale writes
                    // the digits of that locale's numbering system, so a machine set
                    // to `ar-EG-u-nu-arab` or `hi-IN-u-nu-deva` draws a wheel of
                    // Arabic-Indic or Devanagari digits and the golden taken on it
                    // is a pixel diff nobody can reproduce elsewhere. A wheel is
                    // a *clock face* — the one place a number is the same glyph in
                    // every locale — and a picker that wants its own numerals asks
                    // for them through the format, which is the application's.
                    String.format(Locale.ROOT, "%02d", value),
                    value,
                    offset == 0,
                    offset == 0 && column == index,
                    chosen -> set(index, chosen)));
        }
        return new TimeColumn(cells);
    }

    private int valueOf(int index) {
        return switch (index) {
            case 0 -> shown.getHour();
            case 1 -> shown.getMinute();
            default -> shown.getSecond();
        };
    }

    /// Puts `value` in column `index` and reports the time it makes.
    ///
    /// A time the picker refuses is **not shown and not reported**: §4 asks the
    /// gates to hold for the popover as well as the field, and a wheel that
    /// turned onto an hour the picker would then refuse would be a control
    /// arguing with itself.
    private void set(int index, int value) {
        var next = withColumn(index, value);
        if (!widget().allows().test(next)) {
            return;
        }
        setState(() -> {
            shown = next;
            column = index;
        });
        widget().onCommit().accept(next);
    }

    private LocalTime withColumn(int index, int value) {
        return switch (index) {
            case 0 -> shown.withHour(value);
            case 1 -> shown.withMinute(value);
            default -> shown.withSecond(value);
        };
    }

    // --- TimeKeys -------------------------------------------------------------

    @Override
    public boolean step(int delta) {
        var limit = LIMITS[column];
        set(column, Math.floorMod(valueOf(column) + delta, limit));
        // Consumed either way: an `Up` that ran into a refused time is still the
        // popover's key, and letting it through would scroll the window behind.
        return true;
    }

    @Override
    public boolean moveColumn(int delta) {
        var next = Math.clamp((long) column + delta, 0, widget().precision().columns() - 1L);
        if (next == column) {
            return true;
        }
        setState(() -> column = (int) next);
        return true;
    }

    @Override
    public boolean toColumnEdge(boolean first) {
        set(column, first ? 0 : LIMITS[column] - 1);
        return true;
    }

    @Override
    public boolean commit() {
        widget().onCommit().accept(shown);
        return true;
    }
}
