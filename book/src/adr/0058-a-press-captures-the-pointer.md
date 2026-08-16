# ADR-0058: A press captures the pointer, and a key falls through to the window

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §7.1, §7.2; [ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md), [ADR-0055](0055-sdl-owns-keyboard-translation.md)

## Context

Two questions about *who gets an event*, both left open by
[ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md).

**The pointer.** Dispatch targeted whatever was under the pointer at that
instant. A press on a slider's thumb followed by a drag off the track sent the
motion somewhere else, and the release went to whatever the pointer had landed
on. The thumb stopped following, and — worse — it never learned the press had
ended, so `:active` stayed on it. ADR-0054 named this and deferred it.

**The keyboard.** §7.2 asks for a per-window accelerator map. Tab traversal was
already handled in the router, because traversal is a property of the tree rather
than of any node in it; an accelerator has the same character and no home.

## Decision

**A press takes the pointer implicitly, and the release gives it back.** From
press to release, every pointer event — motion, wheel, the release itself — goes
to the element that was pressed, wherever the pointer is. This is what browsers
do for a button-down drag and it is what makes a slider work.

**An explicit `capturePointer(element)` outlives the release.** A gesture that
continues past the button coming up — a drag that ends on Escape, a
click-move-click ruler — needs a capture only its owner can end. So capture
records *how* it was taken: implicit capture is released by the matching release,
explicit capture by `releasePointer()`.

**`:hover` still follows the pointer during a capture.** Capture decides who is
*told*, not what is *highlighted*. What the user can see is still what the
pointer is over, and freezing hover would leave a highlight stuck under a moving
cursor. The cursor *shape*, by contrast, does freeze — see
[ADR-0057](0057-the-cursor-rides-on-the-painted-box.md) — because it describes
what the drag is doing rather than what is underneath it.

**Capture survives the pointer leaving the window.** A drag that overshoots an
edge and comes back is one gesture, and the platform keeps sending the motion.
Releasing on exit would drop the second half of it.

**Accelerators fire after the focused chain declines the key.** The order is:
capture down the focus chain, bubble back up, then the window's accelerator map,
then Tab traversal. A text field keeping `Ctrl+A` for "select all" is the case
this is for — it consumes, and the window's binding does not steal it. The
alternative order would make every focusable widget's key handling conditional on
what the window happened to bind.

**Modifiers must match exactly.** `Ctrl+S` does not fire on `Ctrl+Shift+S`,
because that is a different shortcut and applications bind both.

**A held shortcut repeats.** Holding `Ctrl+Z` repeats the undo, which is what the
platform's own key repeat is for. A shortcut that must not repeat checks
`KeyEvent.repeat()`.

**`Shortcut.of("Ctrl+S")` parses the string a menu prints.** That text is going to
appear beside the menu item anyway, and two spellings of one shortcut is one more
thing to keep in step. It is refused at construction if it names no key — a
shortcut that silently never fires produces a bug report of "the menu item does
nothing" with no error anywhere.

**`Cmd` is not quietly remapped to `Ctrl` on macOS.** A toolkit that did would
make `Ctrl+C` mean two different things depending on where it ran. An application
that wants the platform convention is better served asking for it than having it
guessed.

**Letters and digits joined `Key`, and accelerators are the reason.**
[ADR-0055](0055-sdl-owns-keyboard-translation.md) put the letter a user *typed*
in the text event, and `Key` deliberately named only the keys that do something
rather than type something. `Ctrl+S` is the counterexample: a modified letter
produces no text event on any platform, so the letter has to come from the key
event or the one shortcut every application has could not be expressed. They are
the *layout's* letters, not the keyboard's positions — SDL's default
`latin_letters` translation means the key where `A` sits on a Cyrillic or Thai
keyboard still arrives as `A`, while on AZERTY a shortcut stays where the user's
own layout puts it. An uppercase keycode folds to the lowercase one, because SDL
documents platforms that report only modified keycodes.

## Alternatives considered

- **Capture only when a widget asks.** Rejected: every clickable widget would have
  to ask, and the ones that forgot would be subtly wrong in a way that only shows
  up when the user drags — which is exactly when they are least likely to report
  it precisely.
- **Freeze `:hover` during a capture too.** Rejected above: hover describes what
  is under the pointer, and the pointer is still moving.
- **Accelerators before dispatch.** Rejected: it makes the window's bindings
  override every widget's, so a text field cannot keep `Ctrl+A`.
- **An accelerator map on `Window` rather than the router.** Rejected: the router
  already owns focus and key dispatch, and splitting the two would mean the
  ordering above spanned two objects.
- **Match modifiers loosely, ignoring extra ones.** Rejected: `Ctrl+Shift+Z` would
  then fire `Ctrl+Z`'s undo as well as redo.
- **Name shortcuts with an enum-only API (`new Shortcut(Key.S, ...)`).** Kept —
  the record's canonical constructor is public — but `of(String)` is the one that
  matches how shortcuts are written down everywhere else.

## Consequences

- **A slider is now buildable**, and so is any drag gesture: capture is the piece
  M3's `split-pane` and `scroll` thumb were both waiting on.
- **`:active` cannot get stuck.** The release reaches the captor even when the
  pointer is elsewhere, and clearing `:active` is what it does with it.
- **Accelerators are per window**, which is the scope a user means: `Ctrl+W`
  closes *this* window.
- **Menu accelerators are not registered automatically yet.** §7.2 says a menu
  item declaring an accelerator should register itself; there are no menus (M3).
  The map they will register into exists.
- **Arrow-key group navigation is still missing.** §7.2 also asks for it inside
  composites — radio groups, menus, lists — and it needs those composites before
  it means anything.
- **Only one pointer.** There is no pointer id anywhere in the SPI, so capture is
  a single slot. Multi-touch would make it a map; nothing needs that yet.
