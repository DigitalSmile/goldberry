/// Where an application starts: the program it implements, the call that runs it,
/// and the windows, popups and overlays it puts on screen.
///
/// An application implements [io.github.digitalsmile.goldberry.Application] and
/// hands it to [io.github.digitalsmile.goldberry.Goldberry]'s `launch`, which owns
/// the window, the three trees, the frame loop and the shutdown order in one place
/// instead of in every `main` (ADR-0093). What the running application may ask of
/// all that arrives as a [io.github.digitalsmile.goldberry.Host]: repaints and
/// restyles, shortcuts, overlays, popups, context menus, the clipboard and file
/// dialogs.
///
/// A program that wants less can open a [io.github.digitalsmile.goldberry.Window]
/// itself. The first one opened starts the backend and the event loop on the
/// calling thread, which becomes the UI thread (ADR-0019), so no application names
/// a backend or writes a `switch` over its events.
///
/// Two layers sit above a window's content. `Overlay` pins a widget to a corner
/// inside the window; `Popup` puts a widget tree in a platform window of its own,
/// for the menu or dropdown that has to leave the window (ADR-0102), positioned by
/// `Placement`'s flip-and-shift arithmetic.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry;

import org.jspecify.annotations.NullMarked;
