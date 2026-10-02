/// The platform's own open, save and folder dialogs.
///
/// One of the things on [Backend] that belongs to the *session* rather than to a
/// window, beside the clipboard and the tray. A request is a [FileDialogSpec], an
/// answer is a sealed [FileChoice], and the whole of it is asynchronous because a
/// person is in the loop: the answer arrives later, on the UI thread. Exported to
/// every module.
///
/// Nothing here draws. A toolkit that painted its own file browser would be
/// redrawing the one piece of the desktop the desktop is certain to have, in a
/// theme that does not match it, with none of the places, the search or the
/// permissions a sandboxed platform routes through its own picker.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#the-host).
@NullMarked
package dev.goldberry.render.dialog;

import org.jspecify.annotations.NullMarked;

import dev.goldberry.render.Backend;
