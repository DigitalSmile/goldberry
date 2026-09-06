package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.form.parts.PickerPanel;

/// What a `time-picker` looks like, closed and open.
///
/// The **wheels** are the picture worth having, and they are the one thing no
/// assertion can see: five rows a column with the middle one filled and the
/// neighbours quiet is the whole of what makes a fixed column read as a wheel
/// rather than as a list, and `58 59 00 01 02` is what says it wraps.
///
/// Built directly rather than by opening the popover, because a golden has no
/// window and a popup is a platform window (ADR-0140).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class TimePickerGoldenTest {

    private static final TimeFormat HH_MM = TimeFormat.of(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));

    private final TestHost host = new TestHost();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final String SCENE = """
            #scene { padding: 12px; background: var(--gb-bg); align-items: flex-start }
            #scene time-picker { width: 200px }
            """;

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var scene = new Column(List.of(content), new Attributes("scene", Set.of(), "scene"));
        var tree = new ElementTree(scene, host);
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static Widget wheels(LocalTime at, TimePrecision precision) {
        return new PickerPanel(new TimeColumns(at, LocalTime.MIDNIGHT, precision, time -> true, time -> {}));
    }

    @Test
    @DisplayName("a picker with a time in it, on dark")
    void closedDark() {
        paint(
                "time-picker-dark",
                Theme.NORD_DARK,
                224,
                56,
                new TimePicker().format(HH_MM).value("09:30"));
    }

    @Test
    @DisplayName("and the same on the light theme")
    void closedLight() {
        paint(
                "time-picker-light",
                Theme.NORD_LIGHT,
                224,
                56,
                new TimePicker().format(HH_MM).value("09:30"));
    }

    /// Two wheels, mid-range, so both the filled middle row and the quiet
    /// neighbours above and below it are in one frame.
    @Test
    @DisplayName("the wheels, at hours and minutes")
    void minuteWheels() {
        paint("time-picker-wheels", Theme.NORD_DARK, 160, 200, wheels(LocalTime.of(9, 30), TimePrecision.MINUTES));
    }

    /// The wrap, which is what the quiet neighbours are for: `23` sits above
    /// `00`, and `59` above `00`, because that is what comes next.
    @Test
    @DisplayName("and at the end of the day, where they wrap")
    void wrapping() {
        paint("time-picker-wrap", Theme.NORD_DARK, 160, 200, wheels(LocalTime.of(23, 59), TimePrecision.MINUTES));
    }

    /// Three columns, which is the widest this control gets.
    @Test
    @DisplayName("three wheels at second precision")
    void secondWheels() {
        paint("time-picker-seconds", Theme.NORD_DARK, 200, 200, wheels(LocalTime.of(9, 30, 45), TimePrecision.SECONDS));
    }
}
