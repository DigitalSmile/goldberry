# ADR-0576: An accelerator says whether a held key repeats it

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-022)

## Context

`PointerRouter.keyPressed(key, modifiers, repeat)` ran the bound
accelerator for every press the platform reported, auto-repeats included,
and the `Runnable` it ran could not tell. A widget's `onKey` sees
`KeyEvent.isRepeat()`, but only on the focused chain, and a window
accelerator is exactly what an application binds when no widget holds the
focus. The downstream binds `Escape` to open and close its match menu and
`G` to open and close a graveyard browser: holding either flipped the panel
at the platform's repeat rate, about thirty times a second on GNOME.

## Decision

**A binding carries a `dev.goldberry.input.key.Repeat`**, `FIRE` or
`IGNORE`. `PointerRouter.shortcut(Shortcut, Runnable, Object, Repeat)` takes
it, and `Host` gains `shortcut(Shortcut, Runnable, Repeat)` and
`shortcut(Shortcut, Runnable, Object, Repeat)`, implemented by the window and
by the offscreen session's host.

**The default stays `FIRE`.** Every existing form binds `FIRE`, so nothing
that is bound today changes. `Ctrl+Z` held down stepping back through an
undo history is an accelerator that should repeat, and changing every
application's accelerators at once to suit toggles would have broken those
silently.

**A repeat an `IGNORE` binding declines is consumed.** The key is that
accelerator's; passing the second half of a long press on to focus
navigation or to a widget would make it do something the first half did
not.

`Session.hold(Key, Modifiers, int repeats)` presses a key, repeats it, and
releases it, so an application's offscreen tests can show a toggle does not
flip.

## Alternatives considered

- **Ignore repeats for every accelerator**, as some toolkits do for menu
  accelerators. It is the right default for toggles and the wrong one for
  editing accelerators, and it changes behaviour nobody asked to change.
- **A `Consumer<KeyEvent>` action**, so the binding decides. Every binding
  that wants the common answer would then write the same `if`, and the
  question "does this binding repeat" could not be asked of the router. A
  policy on the binding is one word where it is bound.

## Consequences

- `Host` has two more abstract methods. Its implementations are the
  toolkit's own (`HostedWindow`, `SessionHost`) and the test hosts, all
  updated.
- `TestHost` in `:widgets`' tests records each binding's policy for
  assertions.
