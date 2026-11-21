package dev.goldberry.mic.backend;

import java.util.Objects;

/// A recording device as a backend lists it.
///
/// @param id   the backend's id, which lasts while the device stays plugged in
///             and the backend stays open
/// @param name the name the system gives it
public record CaptureDevice(int id, String name) {

    public CaptureDevice {
        Objects.requireNonNull(name, "name");
    }
}
