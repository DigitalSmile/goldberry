package io.github.digitalsmile.goldberry.render.dialog;

/// Which dialog to put up.
///
/// Three rather than one with flags, because the platforms have three and they
/// are not the same conversation: an open dialog names files that exist, a save
/// dialog names one that may not, and a folder dialog cannot filter. A single
/// "file dialog" with booleans would have to document which combinations are
/// real, and this documents them by not being able to say them.
public enum FileDialogKind {

    /// Pick one or more existing files.
    OPEN_FILE,

    /// Name a file to write, which need not exist yet.
    ///
    /// The platform asks about overwriting, not the application.
    SAVE_FILE,

    /// Pick one or more directories. Filters mean nothing here.
    OPEN_FOLDER;

    /// Whether a type dropdown applies.
    public boolean takesFilters() {
        return this != OPEN_FOLDER;
    }

    /// Whether more than one entry can be chosen.
    public boolean takesMany() {
        return this != SAVE_FILE;
    }
}
