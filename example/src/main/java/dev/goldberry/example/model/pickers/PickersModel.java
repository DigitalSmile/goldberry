package dev.goldberry.example.model.pickers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;
import dev.goldberry.css.value.CssColor;

/// What the Forms screen's pickers hold — the departure's date and time, and a
/// colour — each beside the line that says what the picker made of it.
///
/// Typed values, all three: a picker reports a parsed value and a model that has
/// parsed its own is what a picker formats from.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image. The reader is generated.
@SuppressWarnings("UnusedVariable")
public final class PickersModel {

    /// The departure date, and what the picker made of it.
    ///
    /// A **`LocalDate`**, which is what the date picker reports and what a model
    /// that has parsed its values holds. The picker formats it into the field
    /// rather than stringifying it, so the screen shows the application's own
    /// spelling of a date it owns.
    @Bind("trip.date")
    @SuppressWarnings("unused")
    private @Nullable LocalDate tripDate = LocalDate.of(2026, 9, 14);

    @Bind("trip.status")
    @SuppressWarnings("unused")
    private String tripStatus = "Type a date, or press the chevron";

    /// When the departure leaves, and what the wheels made of it.
    ///
    /// A **`LocalTime`**, for `trip.date`'s reason: the control reports a typed
    /// value and a model that has parsed its own is what a picker formats from.
    @Bind("trip.time")
    @SuppressWarnings("unused")
    private @Nullable LocalTime tripTime = LocalTime.of(9, 30);

    @Bind("trip.time-status")
    @SuppressWarnings("unused")
    private String tripTimeStatus = "Type it, or turn the wheels";

    /// A colour, held as one — `color-picker` reports a `CssColor` value, which
    /// is an `0xAARRGGBB` int, and a model that keeps colours as colours is what
    /// the picker formats its hex from.
    @Bind("paint.colour")
    @SuppressWarnings("unused")
    private Integer paintColour = 0xFF88C0D0;

    @Bind("paint.status")
    @SuppressWarnings("unused")
    private String paintStatus = "Nord frost";

    /// Everything that *happens* to the pickers' values.
    ///
    /// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
    @dev.goldberry.bind.runtime.Actions
    public record Actions(PickersModel values) {

        /// What the picker committed — either from the grid or from the field,
        /// which is the point: the typed field is the source of truth and
        /// the grid writes into it, so there is one handler and not two.
        ///
        /// A **`String`**, and it has to be: valued actions cross as text,
        /// so a document is handed the *formatted* date and Java is handed the
        /// `DateSelection`. The toolkit invents no date syntax, so the
        /// application that chose the format is the one that reads it back.
        @Action("trip.set-date")
        @SuppressWarnings("unused")
        void setTripDate(String value) {
            if (value.isBlank()) {
                values.tripDate = null;
                values.tripStatus = "Nothing chosen";
                return;
            }
            var date = LocalDate.parse(value, DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT));
            values.tripDate = date;
            values.tripStatus = "Leaving on " + date;
        }

        /// What the wheels or the field committed. A `String` for
        /// `trip.set-date`'s reason: valued actions cross as text, so the
        /// application that chose the format is the one that reads it back.
        @Action("trip.set-time")
        @SuppressWarnings("unused")
        void setTripTime(String value) {
            if (value.isBlank()) {
                values.tripTime = null;
                values.tripTimeStatus = "No time chosen";
                return;
            }
            var time = LocalTime.parse(value, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
            values.tripTime = time;
            values.tripTimeStatus = "Boarding at " + time;
        }

        /// What the plane, the ramps, a preset or the hex field committed — one
        /// handler, because the hex field is the source of truth and every
        /// other part writes into it.
        ///
        /// A `String` for `trip.set-date`'s reason: valued actions cross as
        /// text. Here the text is the value's own spelling rather than a
        /// formatting choice, so parsing it back is exact.
        @Action("paint.set-colour")
        @SuppressWarnings("unused")
        void setPaintColour(String hex) {
            var argb = CssColor.parse(hex);
            if (argb == null) {
                return;
            }
            values.paintColour = argb;
            values.paintStatus = hex;
        }
    }
}
