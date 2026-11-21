package dev.goldberry.natives.desktop.dragout;

/// The X11 events an XDND source acts on, read out of an `XEvent`.
sealed interface XdndEvent {

    /// A `ClientMessage` with 32-bit data.
    record ClientMessage(long window, long type, long[] data) implements XdndEvent {}

    /// A `SelectionRequest`: `requestor` wants `selection` as `target`, into
    /// `property`.
    record SelectionRequest(long owner, long requestor, long selection, long target, long property, long time)
            implements XdndEvent {}

    /// A key went down.
    record KeyPress(int keycode) implements XdndEvent {}

    /// Anything else, which is only a reason to look at the pointer again.
    record Other() implements XdndEvent {}
}
