# 223. A tap is a gesture, and a shortcut is a value

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0163](0163-a-menu-bar-owns-its-menus.md) and left open by
[ADR-0220](0220-an-accelerator-is-given-back-by-whoever-took-it.md), whose last
consequence was "a bare `Alt` tap does not activate the bar (`F10` does), which
is a key-release rule and not a map".

## Context

`docs/core-widgets.md` §8 asks a `menubar` for "`Alt`-style keyboard
activation". ADR-0163 shipped `F10` instead and wrote down why:

> A bare `Alt` is a **modifier released with nothing in between**, and a
> `Shortcut` here is a key plus modifiers — `Key` has no `ALT` to name, because a
> shortcut on a modifier alone can never fire.

That diagnosis is right and it is the whole difficulty. Three facts collide:

- **`Shortcut` is a value.** `(Key, Modifiers)`, hashable, a map key. It is
  looked up on a **press** — one event, no history.
- **`Key` deliberately names no modifier**, and should not start: an accelerator
  on `Key.ALT` would be an entry nothing could ever match, and publishing the
  constant would invite exactly that.
- **A tap is neither.** It is *two* events with a rule about the gap between
  them. `Alt` down then `Alt` up is a tap; `Alt` down, `F` down, `F` up, `Alt` up
  is `Alt+F`, and the second one ends with the same release as the first.

So the missing feature is not a fifth `Key` constant or a wider `Modifiers`. It
is a **detector with state**, and the question is where the state lives and what
counts as "nothing in between".

There is a fourth fact that decides the location. `Key.fromSdl` answers
`Key.UNKNOWN` for every modifier keycode — and for every letter that arrives as
text, which is most of the keyboard. By the time input reaches `PointerRouter`,
`Alt` and `é` are the same value. **The platform keycode is the only place the
distinction survives**, and the last component that holds one is `Window`.

## Decision

**A tap is its own concept, in its own package** —
`io.github.digitalsmile.goldberry.input.tap`, beside `input.key` rather than
inside it. `input.key` is a vocabulary of values; this is a gesture recogniser.
Two types:

- **`ModifierKey`** — the four modifiers considered as *keys that can be tapped*,
  each carrying its `Mod` and its two SDL keycodes. Left and right fold to one,
  the same fold `Modifiers.fromSdl` does. Four constants and no way to write a
  fifth.
- **`ModifierTaps`** — the detector and the owner-keyed registry, one per window.

**The rule is stated as what spoils it**, because that is the part that has to be
exhaustive:

| What happens between down and up | Result |
|---|---|
| nothing | **fires** |
| another key goes down | disarmed — it was `Alt+F` |
| the same key auto-repeats | disarmed — it is being *held* |
| a second modifier goes down | disarmed — `Alt+Shift` is a layout switch |
| a pointer button or a wheel | disarmed — it was a modified click or scroll |
| the window loses focus | disarmed — the window switcher took it |
| pointer **motion** | **still fires** — moving the mouse interrupts nothing |

**It lives on `Window`**, fed raw keycodes from `handleKeyPressed` and
`handleKeyReleased` *before* the `InputWatcher` and the router see them, and told
`interrupted()` from `handlePointerPressed`, `handlePointerWheel` and
`handleFocusChanged`. Before the watcher because a key a popup swallows still has
to disarm a tap: `Alt` down, arrow into a menu, `Alt` up is not a tap of `Alt`.

**The release is dispatched either way.** A completed tap does not swallow the
key-up. A widget tracking a held modifier — a slider that snaps while `Shift` is
down — has to see the release whether or not the gesture was also a tap.

**`Host` gains `modifierTap(ModifierKey, Runnable, Object)` and
`removeModifierTap(ModifierKey, Object)`**, with ADR-0220's ownership rule and
nothing else: there is no unowned overload, because the only reason to bind a tap
is a widget that will have to give it back.

**`menubar` binds `ModifierKey.ALT` beside `F10`, and both toggle.** Opening on
the first press and closing on the second is what every desktop bar does with
these keys, and it is what makes a modifier safe to bind at all: a user who
tapped `Alt` by accident taps it again rather than hunting for `Escape`.

**`F10` stays.** Not as a stand-in any more but as the companion binding it
always was on the platforms that have both — and as the one that still works
under a compositor that swallows `Alt` for its own window switcher.

## Alternatives considered

- **Adding `Key.ALT` and letting `Shortcut` hold a modifier alone.** The smallest
  diff and the worst outcome: it makes `Mod.CTRL.and(Key.ALT)` spellable and
  meaningless, and it puts a two-event gesture into a map looked up on one event
  — so it would fire on the press, which is the one thing a tap must not do.
- **Detecting the tap in `PointerRouter`,** from `Key.UNKNOWN` plus a modifier
  mask that went from empty to `{ALT}`. It needs no new plumbing and it is
  guesswork: the mask is the platform's account of what is held *now*, and
  inferring which key produced the change is exactly the ambiguity the raw
  keycode does not have. It also breaks the moment a driver reports the mask
  before the key rather than after.
- **A general "key sequence" or chord recogniser.** One caller, one gesture, and
  a state machine with an alphabet is a great deal of surface to get wrong for
  it. The four-modifier vocabulary can grow into one later; nothing about this
  shape blocks that.
- **Leaving `F10` alone and closing the entry as "won't do".** Defensible for one
  more release and not past it: `Alt` is how a keyboard user reaches a menu bar
  on Windows and on most Linux desktops, and a toolkit whose bar does not answer
  it is a toolkit whose bar they do not find.

## Consequences

- **A new exported package**, `io.github.digitalsmile.goldberry.input.tap`, with
  two public types. It is the first package in `input` that holds a *recogniser*
  rather than a value or a dispatcher, which is why it is not in `input.key`.
- **`Window` grows five call sites** — two feeds and three interruptions — and
  each is one line that nothing else would notice going missing. That is what
  `ModifierTapWindowTest` is for: it drives the real launcher and asserts each
  interruption separately, because a detector that is correct and unwired looks
  exactly like one that is absent.
- **Every `Host` implementation gains two methods.** There are three: `Launcher`,
  `TestHost` and `TourTestHost`. `TestHost` mirrors the ownership rule and gains
  a `tap(ModifierKey)` beside its `press(String)`, so a widget test can fire one
  without a window.
- **A `menubar` now holds two kinds of registration** and has to give back both.
  `MenuBarState` keeps a separate `tapped` flag rather than pretending a tap is a
  `Shortcut`, and `MenuBarTest` asserts that unmounting returns it — the leak is
  the same one ADR-0220 was about, in a second map.
- **`F10` and `Alt` now close an open bar.** A behaviour change to `F10`, and the
  right one; the existing test asserted only that one press opens.
- **What this does not do:** there is no way to bind a tap of a *non*-modifier
  key, no chord, and no "tap then type a mnemonic" — §8's mnemonic underlines are
  a separate feature and are still unbuilt.
- **Unverified on Windows and macOS**, like everything else that reads a keycode.
  The CI legs run the detector against fabricated events under SDL's `dummy`
  driver on all three platforms, which proves the arithmetic and not the
  platform's `Alt`.
