package io.github.digitalsmile.goldberry.natives.sdl.dialog;

/// Which of SDL's three dialog functions to call.
///
/// Not an `SDL_FileDialogType`: that enum belongs to
/// `SDL_ShowFileDialogWithProperties`, which is not bound. This is Java's own
/// way of choosing between three separate C functions, and it exists so the
/// wrapper has one entry point instead of three near-identical ones.
public enum SdlDialogKind {

    /// `SDL_ShowOpenFileDialog` — existing files.
    OPEN_FILE,

    /// `SDL_ShowSaveFileDialog` — one path, which need not exist yet.
    SAVE_FILE,

    /// `SDL_ShowOpenFolderDialog` — directories, and no filters.
    OPEN_FOLDER;

    /// Whether SDL takes a filter list for this kind.
    public boolean takesFilters() {
        return this != OPEN_FOLDER;
    }

    /// Whether SDL takes `allow_many` for this kind.
    public boolean takesAllowMany() {
        return this != SAVE_FILE;
    }
}
