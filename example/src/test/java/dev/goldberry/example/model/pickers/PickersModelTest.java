package dev.goldberry.example.model.pickers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.runtime.Models;

/// The date, the time and the colour: each parsed back from the text its
/// picker committed, and each beside the line that says what it made of it.
@DisplayName("the pickers slice")
class PickersModelTest {

    private final PickersModel model = new PickersModel();
    private final PickersModel.Actions actions = new PickersModel.Actions(model);

    @Test
    @DisplayName("a committed date is parsed in the format the application chose, and a blank one clears it")
    void theDate() {
        var date = LocalDate.of(2026, 10, 25);

        actions.setTripDate(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(date));
        assertEquals(date, Models.observable(model, "trip.date").get());
        assertEquals(
                "Leaving on 2026-10-25", Models.observable(model, "trip.status").get());

        actions.setTripDate(" ");
        assertNull(Models.observable(model, "trip.date").get());
        assertEquals("Nothing chosen", Models.observable(model, "trip.status").get());
    }

    @Test
    @DisplayName("and the time the same way")
    void theTime() {
        var time = LocalTime.of(18, 45);

        actions.setTripTime(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time));
        assertEquals(time, Models.observable(model, "trip.time").get());
        assertEquals(
                "Boarding at 18:45",
                Models.observable(model, "trip.time-status").get());

        actions.setTripTime("");
        assertNull(Models.observable(model, "trip.time").get());
        assertEquals(
                "No time chosen", Models.observable(model, "trip.time-status").get());
    }

    @Test
    @DisplayName("a colour is held as its argb, and a hex that is not one changes nothing")
    void theColour() {
        actions.setPaintColour("#bf616a");
        assertEquals(0xFFBF616A, Models.observable(model, "paint.colour").get());
        assertEquals("#bf616a", Models.observable(model, "paint.status").get());

        actions.setPaintColour("not a colour");
        assertEquals(0xFFBF616A, Models.observable(model, "paint.colour").get());
        assertEquals("#bf616a", Models.observable(model, "paint.status").get());
    }
}
