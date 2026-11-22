package dev.goldberry.camera.backend;

/// A camera came or went, or its permission was answered.
public sealed interface DeviceEvent {

    /// The camera's id.
    int device();

    /// It appeared.
    record Added(int device) implements DeviceEvent {}

    /// It went away.
    record Removed(int device) implements DeviceEvent {}

    /// The user let it be used.
    record Approved(int device) implements DeviceEvent {}

    /// The user refused it.
    record Denied(int device) implements DeviceEvent {}
}
