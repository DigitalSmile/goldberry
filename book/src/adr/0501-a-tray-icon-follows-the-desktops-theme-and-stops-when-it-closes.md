# 501. A tray icon follows the desktop's theme, and stops when it closes

Date: 2026-09-30

## Status

Accepted. Builds the "theme-aware light/dark variants" in `docs/core-widgets.md`
§9's `tray-icon` line, which
[ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md) left as a mechanism
without a user. Changes what
[ADR-0322](0322-the-desktop-says-light-or-dark-or-says-nothing.md)'s
`Host.onSystemThemeChanged` returns.

## Context

The TODO entry said nothing paints a tray icon for you and nothing swaps it on a
theme switch. The parts were there: `TrayIcon.icon(pixels)`, `BackendTray.icon`,
and `SDL_SetTrayIcon`, bound with a comment saying it is what a theme switch
calls. Nothing called it. An application that wanted §9's variants had to hold
two `PixelBuffer`s, listen for the setting and rebuild the tray itself.

Writing the swap exposed three facts the entry did not mention:

- **The listener could not be released.** `Host.onSystemThemeChanged` returned
  nothing, and its documentation said a listener lives as long as the window.
  That holds for an application's own listener. It does not hold for a tray. A
  tray menu cannot change while the icon is up, so an application that changes
  its menu closes the tray and shows it again. Each showing that listened would
  leave one listener behind, holding two pictures and a closed tray. It is the
  same leak ADR-0498 found in the launcher's model subscriptions, at a smaller
  scale. `onFullscreenChanged` already returns a `Subscription`, and for the same
  reason: whatever listens is gone long before the window.
- **The setting is not always the panel's shade.** SDL reports the desktop's
  *application* theme. On Windows that is `AppsUseLightTheme`. The taskbar has
  its own `SystemUsesLightTheme`, which SDL does not read. On Linux it is the
  portal's `color-scheme`, and GNOME's top bar is dark whichever way that is
  set. The toolkit can pick a variant only by the signal it has.
- **A desktop may say nothing.** `Host.systemTheme()` is empty on a session
  with no setting, and a pair has to show one of its two pictures there too.

## Decision

**`TrayIcon` holds a `Picture`, sealed, and it is one of two things.** A
`Single` is one buffer, shown whatever the desktop says. A `ThemePair` is two:
`forLightShell` and `forDarkShell`, set by
`TrayIcon.icons(forLightShell, forDarkShell)`. **Each is named for the
background it sits on, not for its ink.** "The light icon" can mean dark ink for
a light panel or a light mark for a dark one, and the two readings are opposite.
`icon(pixels)` still makes a `Single`. A tray with no picture asks for the
platform's default, as before.

**Where the desktop says nothing, a pair shows `forLightShell`.** That is how
CSS reads "no preference": Media Queries 5 removed `prefers-color-scheme:
no-preference`, and a user agent with no setting matches `light`.

**`Trays.show` keeps a pair matched to the setting.** It starts on the picture
for `host.systemTheme()`, and on each change it calls `BackendTray.icon` with the
other one. The menu is not touched, so the rule that a menu cannot change while
the icon is up still holds. The swap is skipped when the picture is already the
one shown, compared by identity, because `PixelBuffer` is a record and on Linux
every `SDL_SetTrayIcon` writes a PNG for AppIndicator to read back.

**The handle `Trays.show` returns owns the listening.** For a pair it is a
`ThemedTray` wrapped around the backend's tray. Three things stop it:

- closing the handle;
- setting an icon through it, because an application that sets a picture of its
  own (a badge, a busy state) has taken the icon over, and a swap at dusk would
  undo it;
- the backend closing the tray underneath it, as `HeadlessBackend.close` does.
  The next change finds the tray closed and closes the subscription.

A `Single` tray, or one with no picture, gets the backend's handle unwrapped and
listens to nothing.

**`Host.onSystemThemeChanged` returns a `Subscription`.** The launcher wraps
each registration in an object of its own and removes it by identity. Closing
one of two registrations of the same listener leaves the other, and closing it
twice is harmless. A lambda would not do, because the JLS does not promise a
fresh instance for one.

**The toolkit ships no default mark.** A tray icon names the application.
Goldberry's own mark on every application that forgot to supply one would
misname all of them, which is worse than the desktop's generic picture. With no
icon the platform shows its own: `IDI_APPLICATION` on Windows, a blank indicator
on Linux, an empty status item on macOS. An application that wants a picture
has one line to write, and it is the line that decides what the picture is.

## Consequences

- An application gets §9's variants by writing
  `TrayIcon.of(tooltip, menu).icons(darkInk, lightMark)`. No listener or rebuild
  is needed.
- `Host.onSystemThemeChanged` changed its return type from `void` to
  `Subscription`. Callers that ignore the result compile unchanged. Anything
  that implements `Host` must return one: the launcher, `TestHost` and
  `TourTestHost` here.
- `TrayIcon`'s first component is now `picture`, a `Picture`, where it was
  `icon`, a `PixelBuffer`. `TrayIcon.spec()` still describes the tray for a
  desktop that says nothing, and `spec(Optional<SystemTheme>)` describes it for
  a given setting.
- The pair follows the application theme, not the panel. On GNOME, whose top bar
  is dark in both settings, a light setting shows `forLightShell` on a dark bar.
  On Windows it follows the app mode, not the taskbar mode. Neither can be
  fixed from here without platform code SDL does not have. An application that
  knows its panel better sets one picture and follows nothing.
- Tested headlessly in `TraysTest`: a pair starts on the desktop's setting and
  on `forLightShell` when there is none; a change swaps the icon on the same
  tray and leaves its rows alone; closing the tray removes its listener;
  rebuilding the tray five times leaves one listener; an explicit icon ends the
  following; a tray closed underneath lets go; a single-icon tray is shown as
  described and registers nothing. `SystemThemeTest` checks through the real
  launcher that a closed subscription is not told and that closing one of two
  registrations of a listener leaves the other.
- The showcase's tray has no icon, so it does not use the pair. Giving it one
  would mean inventing an asset.
