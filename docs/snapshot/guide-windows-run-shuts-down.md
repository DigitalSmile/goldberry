# `Goldberry.run()` shuts the runtime down

Held back until the release (ADR-0635). One piece.

## `book/src/guide/windows.md`, `## The low-level path`

Replaces the paragraph that begins "`Window.open` may be called more than once":

`Window.open` may be called more than once, and `Goldberry.run()` returns
when every window has closed or something called `Goldberry.stop()`. Either
way it closes what is still open and releases the windowing layer before it
returns, so nothing is left for the process to leak. A window opened after
`run()` returns starts again from nothing. An application launched through the
launcher opens a second window with widgets in it through `host.openWindow`,
not here.
