package dev.goldberry.example.ui.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import dev.goldberry.widgets.controls.option.Option;

/// What a capture card's picker offers: a test signal first, then the devices
/// the system listed last, keyed by their place in that list.
///
/// A device is chosen as an object, so a hot-plug that moves it down the list
/// moves its key with it, and one that takes it away leaves nothing chosen.
///
/// @param <D>       the device: a microphone, a camera
/// @param testKey   the test signal's key
/// @param testLabel what the picker calls the test signal
/// @param devices   the devices listed, in the system's order
/// @param label     what the picker calls a device
record DeviceChoice<D>(String testKey, String testLabel, List<D> devices, Function<? super D, String> label) {

    /// What a device's key starts with.
    static final String DEVICE = "device-";

    DeviceChoice {
        Objects.requireNonNull(testKey, "testKey");
        Objects.requireNonNull(testLabel, "testLabel");
        devices = List.copyOf(devices);
        Objects.requireNonNull(label, "label");
        if (testKey.startsWith(DEVICE)) {
            throw new IllegalArgumentException("the test signal's key " + testKey + " reads as a device's");
        }
    }

    /// The same choice over `listed`.
    DeviceChoice<D> listing(List<D> listed) {
        return new DeviceChoice<>(testKey, testLabel, listed, label);
    }

    /// The picker's options, the test signal first.
    List<Option> options() {
        var options = new ArrayList<Option>(devices.size() + 1);
        options.add(new Option(testKey, testLabel));
        for (var at = 0; at < devices.size(); at++) {
            options.add(new Option(DEVICE + at, label.apply(devices.get(at))));
        }
        return List.copyOf(options);
    }

    /// The key `device` has in this list, or the test signal's when it is not
    /// listed.
    String key(Optional<D> device) {
        return device.map(devices::indexOf)
                .filter(at -> at >= 0)
                .map(at -> DEVICE + at)
                .orElse(testKey);
    }

    /// The device `key` names, or empty for the test signal and a key that
    /// names no listed device.
    Optional<D> device(String key) {
        if (!key.startsWith(DEVICE)) {
            return Optional.empty();
        }
        try {
            var at = Integer.parseInt(key.substring(DEVICE.length()));
            return at >= 0 && at < devices.size() ? Optional.of(devices.get(at)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
