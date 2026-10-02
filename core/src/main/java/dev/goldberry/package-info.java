/// Where an application starts: the program it implements, the call that runs it,
/// and the windows, popups and overlays it puts on screen.
///
/// An application implements [dev.goldberry.Application] and
/// hands it to [dev.goldberry.Goldberry]'s `launch`, which owns
/// the window, the three trees, the frame loop and the shutdown order in one place
/// instead of in every `main`. What the running application may ask of
/// all that arrives as a [dev.goldberry.Host]: repaints and
/// restyles, shortcuts, overlays, popups, context menus, the clipboard and file
/// dialogs.
///
/// A program that wants less can open a [dev.goldberry.Window]
/// itself. The first one opened starts the backend and the event loop on the
/// calling thread, which becomes the UI thread, so no application names
/// a backend or writes a `switch` over its events.
///
/// Two layers sit above a window's content. `Overlay` pins a widget to a corner
/// inside the window; `Popup` puts a widget tree in a platform window of its own,
/// for the menu or dropdown that has to leave the window, positioned by
/// `Placement`'s flip-and-shift arithmetic.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Building an application](https://goldberry.dev/docs/applications.html) and
/// [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html).
@NullMarked
package dev.goldberry;

import org.jspecify.annotations.NullMarked;
