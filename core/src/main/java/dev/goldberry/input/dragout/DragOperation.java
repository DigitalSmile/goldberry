package dev.goldberry.input.dragout;

/// What a target may do with what is dragged to it, as the platform names it.
///
/// The source offers a set ([DragOffer#operations()]) and the target picks one;
/// the user steers the pick with the platform's modifier keys.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
public enum DragOperation {
    /// The target keeps a copy. The default, and the one every target takes.
    COPY,
    /// The target takes the data and the source is expected to remove its own.
    /// The toolkit never deletes anything: a source that hears it decides.
    MOVE,
    /// The target keeps a reference to the source's data, a shortcut or a
    /// symbolic link for a file.
    LINK
}
