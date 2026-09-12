/// The platform's own open, save and folder dialogs.
///
/// `docs/gaps.md` G9, and the third thing on [Backend] that belongs to the
/// *session* rather than to a window — after the clipboard and the tray. A
/// request is a [FileDialogSpec], an answer is a sealed [FileChoice], and the
/// whole of it is asynchronous because a person is in the loop (ADR-0287).
///
/// Nothing here draws. A toolkit that painted its own file browser would be
/// redrawing the one piece of the desktop the desktop is certain to have, in a
/// theme that does not match it, with none of the places, the search or the
/// permissions a sandboxed platform routes through its own picker.
package io.github.digitalsmile.goldberry.render.dialog;

import io.github.digitalsmile.goldberry.render.Backend;
