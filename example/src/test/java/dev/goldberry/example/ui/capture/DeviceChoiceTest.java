package dev.goldberry.example.ui.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.widgets.controls.option.Option;

@DisplayName("a capture card's picker")
class DeviceChoiceTest {

    private static final DeviceChoice<String> EMPTY =
            new DeviceChoice<>("tone", "Test tone", List.of(), Function.identity());

    @Test
    @DisplayName("offers the test signal first, then each device by its place")
    void options() {
        var choice = EMPTY.listing(List.of("Headset", "Webcam"));

        assertEquals(
                List.of("tone", "device-0", "device-1"),
                choice.options().stream().map(Option::value).toList());
        assertEquals(
                List.of("Test tone", "Headset", "Webcam"),
                choice.options().stream().map(Option::label).toList());
    }

    @Test
    @DisplayName("keys a device by where it is now, and the test signal when it is gone")
    void keys() {
        var before = EMPTY.listing(List.of("Headset", "Webcam"));
        var after = before.listing(List.of("Dock", "Webcam"));

        assertEquals("device-1", before.key(Optional.of("Webcam")));
        assertEquals("device-1", after.key(Optional.of("Webcam")));
        assertEquals("tone", after.key(Optional.of("Headset")));
        assertEquals("tone", after.key(Optional.empty()));
    }

    @Test
    @DisplayName("reads back the device a key names, and nothing for any other key")
    void devices() {
        var choice = EMPTY.listing(List.of("Headset"));

        assertEquals(Optional.of("Headset"), choice.device("device-0"));
        assertEquals(Optional.empty(), choice.device("tone"));
        assertEquals(Optional.empty(), choice.device("device-1"));
        assertEquals(Optional.empty(), choice.device("device--1"));
        assertEquals(Optional.empty(), choice.device("device-x"));
    }

    @Test
    @DisplayName("refuses a test signal whose key reads as a device's")
    void clash() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeviceChoice<>("device-0", "Test", List.<String>of(), Function.identity()));
    }
}
