package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// What a [CalendarView] holds: which month is shown, which day the arrows are
/// on, and the month that is fading out.
///
/// ## The roving day is state and the selection is not
///
/// §10 gives the grid "a roving day", and a roving highlight is not a selection:
/// a user arrowing across a month has chosen nothing yet, and an application told
/// about every arrow would be told about forty things nobody picked. So the day
/// under the keyboard lives here and only `Enter`, `Space` or a press reports
/// anything — the same split `list` makes between a focused row and a selected
/// one.
///
/// The shown month is state for the same reason and one more: paging with
/// `PgUp` is a *view* change, and an application that had to hold it would be
/// asked to re-render on every page of a control it was only reading from.
/// [CalendarView#month] is therefore an opening position rather than a value the
/// widget follows.
final class CalendarState extends State<CalendarView> implements CalendarBox.CalendarKeys {

    /// Both are replaced by [#initState] before anything builds, and neither may
    /// be null. The epoch rather than today, because this class cannot ask what
    /// day it is — [CalendarView] explains why, and it is the reason the widget
    /// is *told* which month to open on.
    private YearMonth shown = YearMonth.from(LocalDate.EPOCH);

    private LocalDate roving = LocalDate.EPOCH;

    /// The month being faded out, and how far through — §3.1's cross-fade. Both
    /// null when nothing is changing, which is nearly always.
    private @Nullable CalendarMonth outgoing;

    private @Nullable Phase phase;

    private boolean focused;

    @Override
    protected void initState() {
        super.initState();
        var calendar = widget();
        shown = calendar.month();
        // The chosen day when it is on screen, and the first of the month
        // otherwise. A roving highlight that started on a date the grid is not
        // showing would move the grid on the first arrow press, which reads as
        // the calendar having been somewhere else all along.
        var chosen = calendar.selection().first();
        roving = clamped(chosen != null && YearMonth.from(chosen).equals(shown) ? chosen : shown.atDay(1));
    }

    /// A month or a selection arriving from the application moves the grid.
    ///
    /// Both only when they *change*. A calendar that jumped to its widget's month
    /// on every rebuild would undo a user's own paging the moment anything else
    /// in the window changed — which is the same rule `text-input` states about a
    /// `bind=` value, in the same words: the property is an opening position and
    /// an override, not the thing being edited.
    @Override
    protected void didUpdateWidget(CalendarView previous) {
        super.didUpdateWidget(previous);
        if (!widget().month().equals(previous.month())) {
            goTo(widget().month());
        }
        var chosen = widget().selection().first();
        if (chosen == null || chosen.equals(previous.selection().first())) {
            return;
        }
        roving = clamped(chosen);
        var wanted = YearMonth.from(roving);
        if (!wanted.equals(shown)) {
            goTo(wanted);
        }
    }

    @Override
    public Widget build(BuildContext context) {
        var calendar = widget();
        var month = CalendarMonth.of(shown, calendar.locale());
        var fading = outgoing;
        if (phase != null && !phase.isRunning()) {
            // Settled since the last build. Dropped here rather than in `render`,
            // because a widget describes itself and the finished half of a
            // cross-fade is not part of the description any more.
            phase = null;
            outgoing = null;
            fading = null;
        }
        return new CalendarBox(
                new CalendarHeader(
                        CalendarMonth.monthLabel(shown, calendar.locale()),
                        () -> goTo(shown.minusMonths(1)),
                        () -> goTo(shown.plusMonths(1))),
                new CalendarWeekdays(CalendarMonth.weekdayNames(calendar.locale())),
                new CalendarGrid(weeksOf(month), fading == null ? null : weeksOf(fading), phase),
                calendar.disabled(),
                calendar.attributes(),
                this);
    }

    /// Six [CalendarWeek]s of seven [CalendarDay]s, each told everything it draws.
    private List<Widget> weeksOf(CalendarMonth month) {
        var calendar = widget();
        var selection = calendar.selection();
        var today = calendar.today();
        var weeks = new ArrayList<Widget>(CalendarMonth.WEEKS);
        for (var row = 0; row < CalendarMonth.WEEKS; row++) {
            var days = new ArrayList<Widget>(CalendarMonth.DAYS_IN_WEEK);
            for (var date : month.week(row)) {
                days.add(new CalendarDay(
                        date,
                        String.valueOf(date.getDayOfMonth()),
                        month.isInMonth(date),
                        date.equals(today),
                        selection.contains(date),
                        selection.isStart(date),
                        selection.isEnd(date),
                        selection.covers(date),
                        // Only while this has the keyboard: a roving highlight on
                        // a grid nobody is typing into is a second thing claiming
                        // to be the selection.
                        focused && date.equals(roving),
                        calendar.disabled() || !calendar.allows(date),
                        calendar.decoration().apply(date),
                        this::press));
            }
            weeks.add(new CalendarWeek(days));
        }
        return List.copyOf(weeks);
    }

    // --- moving ---------------------------------------------------------------

    /// Shows `month`, starting a cross-fade unless one is already running.
    ///
    /// The **first** month of a run is the one that fades out. A user holding
    /// `PgDn` pages faster than 100ms a month, and restarting the fade on each
    /// would draw a month that was never looked at over one that never settled;
    /// keeping the original outgoing layer makes a run read as one movement.
    private void goTo(YearMonth month) {
        if (month.equals(shown)) {
            return;
        }
        var leaving = CalendarMonth.of(shown, widget().locale());
        setState(() -> {
            if (outgoing == null) {
                outgoing = leaving;
                phase = new Phase(Phase.Kind.ENTERING, CalendarGrid.CROSS_FADE_MILLIS);
            }
            shown = month;
        });
    }

    /// Puts the roving day on `date`, paging the grid if it is in another month.
    ///
    /// @return whether the day moved
    private boolean rove(LocalDate date) {
        var target = clamped(date);
        if (target.equals(roving)) {
            return false;
        }
        setState(() -> roving = target);
        var wanted = YearMonth.from(target);
        if (!wanted.equals(shown)) {
            goTo(wanted);
        }
        return true;
    }

    /// `date` brought inside `min` and `max`.
    ///
    /// The **bounds only**, not the disabled predicate: a predicate can refuse
    /// arbitrary days, so clamping to it would mean searching for the nearest
    /// acceptable one in a direction the caller did not give — and a `Right` that
    /// skipped four days because a weekend was refused is a grid whose arrows
    /// lie. The roving day may therefore sit on a refused date, which draws as
    /// `:disabled` and cannot be chosen; §10 asks for the days to be unreachable,
    /// and unreachable is what `choose` enforces.
    private LocalDate clamped(LocalDate date) {
        var calendar = widget();
        if (calendar.min() != null && date.isBefore(calendar.min())) {
            return calendar.min();
        }
        if (calendar.max() != null && date.isAfter(calendar.max())) {
            return calendar.max();
        }
        return date;
    }

    // --- CalendarKeys ---------------------------------------------------------

    @Override
    public boolean moveDays(int days) {
        return !widget().disabled() && rove(roving.plusDays(days));
    }

    /// `PgUp`/`PgDn`. `plusMonths` keeps the day of the month where it fits and
    /// clamps to the last day where it does not, which is what `java.time` does
    /// and what a user paging from the 31st means.
    @Override
    public boolean moveMonths(int months) {
        return !widget().disabled() && rove(roving.plusMonths(months));
    }

    @Override
    public boolean moveYears(int years) {
        return !widget().disabled() && rove(roving.plusYears(years));
    }

    /// `Home`/`End` — the ends of the week the roving day is in, in the locale's
    /// terms rather than in `DayOfWeek`'s.
    @Override
    public boolean moveToWeekEdge(boolean start) {
        if (widget().disabled()) {
            return false;
        }
        var month = CalendarMonth.of(shown, widget().locale());
        for (var row = 0; row < CalendarMonth.WEEKS; row++) {
            var week = month.week(row);
            if (week.contains(roving)) {
                return rove(start ? week.getFirst() : week.getLast());
            }
        }
        // The roving day is not in the shown grid, which can only happen between
        // a move and the build that follows it. Moving by six days either way is
        // the same answer the loop would have given.
        return rove(start ? roving.minusDays(roving.getDayOfWeek().getValue() - 1L) : roving);
    }

    @Override
    public boolean choose() {
        return !widget().disabled() && press(roving);
    }

    @Override
    public void focusChanged(boolean gained) {
        if (focused == gained) {
            return;
        }
        setState(() -> focused = gained);
    }

    /// A day was chosen, by the keyboard or by the pointer.
    ///
    /// The roving day follows the press, so arrowing on from a clicked day starts
    /// where the pointer left off rather than wherever the keyboard was last.
    ///
    /// @return whether anything was reported
    private boolean press(LocalDate date) {
        var calendar = widget();
        if (calendar.disabled() || !calendar.allows(date)) {
            return false;
        }
        rove(date);
        var onSelect = calendar.onSelect();
        if (onSelect != null) {
            onSelect.accept(calendar.selection().with(date));
        }
        return true;
    }
}
