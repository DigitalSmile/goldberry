package io.github.digitalsmile.goldberry.widgets.form.datepicker;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.panel.calendar.CalendarView;
import io.github.digitalsmile.goldberry.widgets.panel.calendar.DateSelection;

/// What a `date-picker` looks like, closed and open.
///
/// The **closed** control is the one thing no other file photographs: §2 gives
/// this row "field = `text-input`", and the question a picture answers is whether
/// the affordance beside it reads as part of the same control or as a second one
/// bolted on.
///
/// The **open** one is built by hand rather than by opening the popover, because
/// a golden has no window and a popup is a platform window (ADR-0140). What it
/// photographs is the thing that would otherwise go unwatched: `date-picker-panel`
/// is §2's "popup radius 12, padding 8", and the calendar inside draws no surface
/// of its own — so if the panel ever stopped drawing one, the grid would float on
/// the desktop and every test in this module would still pass.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class DatePickerGoldenTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 6);
    private static final DateFormat ISO = DateFormat.of(DateTimeFormatter.ISO_LOCAL_DATE);

    private final TestHost host = new TestHost();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final String SCENE = """
            #scene { padding: 12px; background: var(--gb-bg); align-items: flex-start }
            #scene date-picker { width: 200px }
            """;

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var scene = new io.github.digitalsmile.goldberry.widgets.core.Column(
                List.of(content),
                new io.github.digitalsmile.goldberry.widget.attr.Attributes("scene", java.util.Set.of(), "scene"));
        var tree = new ElementTree(scene, host);
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static DatePicker picker(String value) {
        return new DatePicker(
                        value,
                        null,
                        null,
                        "yyyy-mm-dd",
                        false,
                        SEPTEMBER,
                        TODAY,
                        null,
                        null,
                        CalendarView.ALL_ALLOWED,
                        ISO,
                        Locale.UK,
                        false,
                        null)
                .locale(Locale.UK);
    }

    @Test
    @DisplayName("a picker with a date in it, on dark")
    void closedDark() {
        paint("date-picker-dark", Theme.NORD_DARK, 224, 56, picker("2026-09-14"));
    }

    @Test
    @DisplayName("and the same on the light theme")
    void closedLight() {
        paint("date-picker-light", Theme.NORD_LIGHT, 224, 56, picker("2026-09-14"));
    }

    /// The empty state, which is the one that shows the placeholder and the
    /// affordance with nothing competing with it.
    @Test
    @DisplayName("an empty picker shows its placeholder")
    void empty() {
        paint("date-picker-empty", Theme.NORD_DARK, 224, 56, picker(""));
    }

    /// The popover's surface, with §10's grid on it. Built directly, for the
    /// reason in the class note.
    @Test
    @DisplayName("the panel the grid opens on")
    void panel() {
        paint(
                "date-picker-panel",
                Theme.NORD_DARK,
                288,
                320,
                new DatePickerPanel(new CalendarView(DateSelection.of(LocalDate.of(2026, 9, 14)), null, SEPTEMBER)
                        .today(TODAY)
                        .locale(Locale.UK)));
    }
}
