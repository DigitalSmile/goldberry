# 378. The desktop's own modifier has a name

Date: 2026-09-17

## Status

Accepted. Closes `docs/ARCHITECTURE.md` §17.1's "The platform primary modifier",
which had been recorded as a disagreement between the design system and the code
since the shortcut table was built.

## Context

`docs/design-system.md` §2.3 asks for accelerators expressed against a platform
primary modifier — `Cmd` on macOS, `Ctrl` elsewhere — "via one `Shortcut`
abstraction". `Shortcut` refused to do it, and wrote down why: a toolkit that
silently turns `Ctrl` into `Cmd` on macOS makes `Ctrl+C` mean two different
things depending on where it runs, and a terminal emulator or an editor with
Emacs bindings is broken by exactly that translation.

§17.1 recorded both as defensible and incompatible, with the design system
winning by default. They are not incompatible. The design system wants a way to
*say* "whatever this desktop uses"; the code was refusing to *guess* it. The
disagreement was that the toolkit had no word for the thing.

## Decision

**`Primary` is a modifier you can name, and nothing is translated.**

- `PrimaryModifier.current()` is `Mod.META` on macOS and `Mod.CTRL` everywhere
  else, read once from `os.name` and overridable with
  `-Dgoldberry.input.primary=ctrl|meta`. `resolve(osName, override)` is the
  testable half, which is `NativePlatform`'s rule for the same problem.
- `Shortcut.of("Primary+S")` and `Shortcut.primary(Key.S)` build the accelerator
  this desktop would have written. `Mod` and `CmdOrCtrl` parse as the same
  thing, because those are the names the same idea goes by elsewhere.
- `Ctrl` is still `Ctrl` and `Cmd` is still `Cmd`. An application that means the
  control key gets the control key, on every platform.
- **The toolkit's own editing accelerators move to it**: select-all, copy, cut,
  paste, undo and redo are on the primary modifier in `EditKeys`, so a
  `text-input` on macOS answers `Cmd+C` rather than `Ctrl+C`. On Linux and
  Windows nothing changes, because the primary modifier is `Ctrl` there.
- `Shortcut.toString()` prints `Cmd+` where the desktop calls it that, so a menu
  row reads as that platform's menus read.

## Consequences

- Word-wise movement stays on `Ctrl`, and `Home`/`End` stay where they are. The
  rest of macOS's editing conventions — `Alt+Left` for a word, `Cmd+Left` for
  the line, `Ctrl+A` for its start — are a second key map and are their own
  entry on the TODO; this is the accelerator modifier, not the whole platform.
- `design-system.md` §2.3 is satisfied by the abstraction it asked for, and the
  counter-argument it was up against is preserved rather than overruled.
