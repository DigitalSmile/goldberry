package dev.goldberry.mic.backend;

/// A recording device came or went.
public sealed interface DeviceEvent {

    /// The device's id.
    int device();

    /// It appeared.
    record Added(int device) implements DeviceEvent {}

    /// It went away.
    record Removed(int device) implements DeviceEvent {}
}
