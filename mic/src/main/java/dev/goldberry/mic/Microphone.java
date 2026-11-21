package dev.goldberry.mic;

import java.util.Objects;

/// A recording device, or the system's choice of one.
///
/// Two microphones are equal when they are the same device. A device that is
/// unplugged and plugged in again is a new one, as the system sees it.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class Microphone {

    private final int id;
    private final String name;
    private final boolean systemDefault;

    Microphone(int id, String name, boolean systemDefault) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.systemDefault = systemDefault;
    }

    /// The name the system gives it.
    public String name() {
        return name;
    }

    /// Whether this is the system's choice rather than one device: a recording
    /// from it follows the user's setting when it changes.
    public boolean isSystemDefault() {
        return systemDefault;
    }

    int id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Microphone microphone && microphone.id == id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "Microphone[" + name + (systemDefault ? ", the system's choice]" : "]");
    }
}
