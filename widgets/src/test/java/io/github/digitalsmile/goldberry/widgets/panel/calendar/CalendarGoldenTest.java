package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.select.Selector;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// What a `calendar` looks like — §2's row for it, photographed.
///
/// Everything on that row is a geometry no assertion in [CalendarTest] can see:
/// a 32-point square cell, a **grid gap of zero** so a range's shading is one run
/// rather than seven stripes a week, a `caption` header row in
/// `--gb-text-muted`, and a `full` radius on the chosen day and on a range's two
/// ends and nowhere between them.
///
/// **September 2026**, fixed, and `today` supplied: a calendar cannot read the
/// clock (ADR-0274) and an image of one that could would fail every midnight.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class CalendarGoldenTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 6);

    private final TestHost host = new TestHost();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A surface to sit on, and the padding a `date-picker`'s popover gives it —
    /// §2's "popup radius 12, padding 8".
    private static final String SCENE = """
            calendar { padding: 8px; background: var(--gb-surface) }
            """;

    private void paint(String name, Theme theme, CalendarView calendar, boolean focused) {
        var tree = new ElementTree(calendar, host);
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());
        renderer.render(tree);
        if (focused) {
            // Both halves, for `CodeInputGoldenTest`'s reason: the widget is told
            // so a day starts roving, and the element is told so `:focus-visible`
            // matches. A golden has no window to give the keyboard to.
            ((CalendarBox) tree.root().children().getFirst().widget()).onFocusChanged(true, true);
            tree.flush();
            tree.root().children().getFirst().setPseudoClass(Selector.PseudoClass.FOCUS_VISIBLE, true);
        }
        GoldenImage.assertMatches(name, 256, 288, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static CalendarView september(DateSelection selection) {
        return new CalendarView(selection, null, SEPTEMBER).today(TODAY).locale(Locale.UK);
    }

    /// One chosen day, today unchosen, and two rows of borrowed days — the frame
    /// that shows every state a cell has except a range's.
    @Test
    @DisplayName("a month with a day chosen, on dark")
    void singleDark() {
        paint("calendar-dark", Theme.NORD_DARK, september(DateSelection.of(LocalDate.of(2026, 9, 14))), false);
    }

    @Test
    @DisplayName("and the same on the light theme")
    void singleLight() {
        paint("calendar-light", Theme.NORD_LIGHT, september(DateSelection.of(LocalDate.of(2026, 9, 14))), false);
    }

    /// §2's "radius `full` on the selected day, **range ends only**" — the middle
    /// is square and continuous, which is the whole reason the grid has no gap.
    @Test
    @DisplayName("a range is round at both ends and square between them")
    void range() {
        paint(
                "calendar-range",
                Theme.NORD_DARK,
                september(DateSelection.range(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 21))),
                false);
    }

    /// §10's `min`, `max` and disabled predicate, in one picture: nothing before
    /// the 7th, nothing after the 25th, and no Sundays in between.
    @Test
    @DisplayName("what min, max and the predicate refuse")
    void refused() {
        paint(
                "calendar-bounds",
                Theme.NORD_DARK,
                september(DateSelection.NONE)
                        .between(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25))
                        .disabledDates(date -> date.getDayOfWeek() == DayOfWeek.SUNDAY),
                false);
    }

    /// The roving day, which is a ring **inside** one cell rather than around the
    /// grid — §10's "one Tab stop with a roving day".
    @Test
    @DisplayName("the ring is on the roving day, not around the month")
    void focused() {
        paint("calendar-focus", Theme.NORD_DARK, september(DateSelection.of(LocalDate.of(2026, 9, 14))), true);
    }

    /// `FocusGoldenPairTest` requires this rather than the comment: every
    /// `*-focus.png` has a `-light` twin, because a focus ring is the one mark in
    /// the system with no second means of being seen.
    @Test
    @DisplayName("and the same ring on the light theme")
    void focusedOnLight() {
        paint("calendar-focus-light", Theme.NORD_LIGHT, september(DateSelection.of(LocalDate.of(2026, 9, 14))), true);
    }
}
