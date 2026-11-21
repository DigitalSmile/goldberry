package dev.goldberry.natives.desktop.dragout;

/// What a target may do with a drag, in the platform's three words.
public enum DragAction {
    /// The target keeps a copy.
    COPY,
    /// The target takes it, and the source is expected to remove its own.
    MOVE,
    /// The target keeps a reference.
    LINK
}
