package io.github.digitalsmile.goldberry.natives.sdl.dialog;

import java.util.List;

/// What a file dialog answers with, once.
///
/// ```c
/// typedef void (SDLCALL *SDL_DialogFileCallback)(void *userdata, const char *const *filelist, int filter);
/// ```
///
/// The C callback packs three outcomes into one pointer — NULL is an error, a
/// pointer to NULL is a cancel, anything else is a choice — and reading that
/// convention is the native layer's job, not its caller's. So this has three
/// methods and each one means what it says.
///
/// **It may arrive on another thread.** SDL's header is explicit: "the callback
/// may be invoked from the same thread or from a different one, depending on the
/// OS's constraints", and on Linux it comes back from the DBus thread that talks
/// to the XDG portal. Whatever is on the other side of this interface is
/// responsible for getting onto the thread it wants to be on.
public interface SdlFileDialogCallback {

    /// The user picked something. Never empty.
    ///
    /// @param paths       absolute paths, in the order the platform gave them
    /// @param filterIndex which filter was selected, or -1 when the platform
    ///                    does not report one — which several do not
    void chosen(List<String> paths, int filterIndex);

    /// The user closed the dialog without picking anything.
    void cancelled();

    /// The platform could not show the dialog, or failed while it was up.
    ///
    /// @param error `SDL_GetError()`, read before anything else could overwrite it
    void failed(String error);
}
