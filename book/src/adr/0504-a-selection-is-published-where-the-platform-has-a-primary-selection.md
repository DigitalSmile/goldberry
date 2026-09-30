# 504. A selection is published where the platform has a primary selection, and a field never asks which platform that is

Date: 2026-09-30

## Status

Accepted. Closes `book/src/TODO.md`'s "No primary selection" under *The
clipboard*. Adds a second text buffer beside
[ADR-0286](0286-a-clipboard-write-is-an-offer.md)'s clipboard and leaves the
clipboard exactly as it was. Bumps libgoldberry's ABI from 16 to 17.

## Context

X11 has two buffers where other desktops have one. `Ctrl+C` fills the
CLIPBOARD selection; *selecting* text fills PRIMARY, and a middle click in any
other application pastes it, with no key pressed in between. Wayland carries the
same thing through `zwp_primary_selection_device_manager_v1`, which GNOME, KDE and
wlroots compositors all advertise. SDL3 wraps both as `SDL_SetPrimarySelectionText`,
`SDL_GetPrimarySelectionText` and `SDL_HasPrimarySelectionText`, and none of the
three was bound.

The entry named why: it is one platform's idea, and the widgets that would fill
it — a text field on X11 — "would have to know they are on X11". That is the
part that needed deciding, and it has a trap under it. SDL answers the three calls
on **every** video driver. On `x11` and `wayland` they talk to the window system;
on everything else — Windows, Cocoa, `offscreen`, `dummy` — SDL keeps the text in
a buffer of its own inside the process and reads it back faithfully. A binding
that trusted the calls would work in every test and offer Windows users a middle
click that pastes something nobody on Windows selected.

## Decision

**Three symbols, bound like the clipboard's text.** `SdlClipboardCalls` gains a
holder for each, `SdlClipboard` gains `hasPrimaryText()`, `primaryText()` and
`primaryText(String)` with the same call-copy-`SDL_free` read, and
`goldberry.symbols` lists them in the clipboard section. `GOLDBERRY_ABI_VERSION`
and `GoldberryShim.SUPPORTED_ABI_VERSION` go to 17. The three descriptors are ones
the clipboard already links, so the native-image metadata gains nothing.

**The capability is optional, and the backend is the only thing that knows.**
`render.clipboard.PrimarySelection` is text only — `hasText()`, `text()`,
`text(String)` — because nothing in the toolkit selects anything but text.
`Backend.primarySelection()` and `Host.primarySelection()` return an `Optional`,
empty by default:

- **`Sdl3Backend`** answers present only when the driver SDL chose is `x11` or
  `wayland` — `hasPrimarySelection(String)`, decided once after `SDL_Init` and
  unit-tested over driver names with no display.
- **`HeadlessBackend`** answers with an in-memory `HeadlessPrimarySelection`,
  **present** by default so the widgets' behaviour is testable, with
  `primarySelection(false)` to model a platform without one, and
  `primaryBuffer()` to prove nothing was written while it was off. It counts
  writes and can refuse them, as `HeadlessClipboard` can.
- **`Launcher`** forwards the backend's answer. A `Host` that says nothing has
  none.

An `Optional` where `clipboard()` is deliberately not one, because absence here
changes what a widget *does* and not only what it reads. A clipboard that always
reads empty is honest; a middle button that moves the caret and pastes nothing is
a gesture from the wrong platform. So without a primary selection, nothing is
published and a middle click is not consumed.

**A finished selection is published, once.** `text-input`, `text-area`, core's
`Editor` and the content views' `SelectableDocument` publish a non-empty selection
when it is finished:

- a pointer selection on the **release** — a drag, a double click, a triple click
  — and not on every move, because every write is an ownership change the whole
  desktop is told about, and a clipboard manager that mirrors PRIMARY would hear
  one per pointer event;
- a keyboard selection when its key lands — `Shift` with a movement, and `Ctrl+A`,
  which publishes even when everything was already selected;
- **not** the select-all a `text-input` does when `Tab` arrives. Arriving is not
  selecting, and a form tabbed through would otherwise overwrite PRIMARY once per
  field.

A collapsed selection publishes nothing and clears nothing: what was last
selected stays pasteable, which is what xterm and every browser do.

**A middle click pastes at the point.** In a field or an `Editor`, a middle
press moves the caret to where it landed and inserts the primary selection's
text there. The move changes no text, so the history records one step and one
`Ctrl+Z` takes the paste back, leaving the caret where the press put it. It is
`Ctrl+V`'s insertion — the same maximum length, a single line's newlines
flattened, a `text-area`'s kept — and a read-only or disabled control, an empty
primary selection, or none at all make it a no-op the event is not consumed by.
A drag after a middle press selects nothing: the fields now track whether the
primary button started the gesture, which also stops a right-button drag from
extending the selection, as it did before.

**A `password` never publishes.** `text-input` is the only masked field in the
catalog (`code-input`'s `mask` has no selection at all). A primary selection is
readable by every client on the desktop with no action from the user, and every
X11 toolkit refuses it for a secure field. Pasting *into* a password by middle
click is allowed, as `Ctrl+V` is: the ban is one-way.

## Consequences

- No widget and no `Editor` names a platform. Each asks its host whether a
  primary selection exists, and the whole of the platform decision is one
  predicate over the driver's name in `Sdl3Backend`.
- `SelectableDocument` publishes and never pastes: a document is not a target.
  Its tests reach a primary selection through a `Host` proxy, because a press
  finds one only through the element that heard it.
- The showcase's canvas sticky wires its `Editor` to the host's primary
  selection, which is the one `Editor` in the tree.
- A Wayland compositor without the primary-selection protocol is not told apart:
  SDL refuses the write and reads empty there, which every caller already handles
  as a refusal.
- The primary selection is not watched, for the reason the clipboard is not
  ([ADR-0286](0286-a-clipboard-write-is-an-offer.md)'s entry, still open): a
  middle click asks when it happens.
- `natives`' `SdlClipboardTest` round-trips the three calls against the real
  library, and proves the two buffers are separate.

## Alternatives considered

- **`PrimarySelection.none()` on a non-`Optional` accessor**, the clipboard's
  shape. Every widget would then need a second question — does this *do*
  anything? — to know whether a middle click is a paste, which is the question
  the `Optional` already answers.
- **Offering it on every driver and letting SDL's in-process buffer stand in.**
  Middle-click paste between two fields of one application on Windows, which no
  Windows user expects, and a `HeadlessBackend` that proved nothing about the
  platforms where it matters.
- **A method on `Clipboard`** — `primaryText()` beside `text()`. A backend with a
  clipboard and no primary selection would have to implement it anyway, and
  every caller of the clipboard would see a buffer that exists on two desktops.
- **Publishing on every selection change**, as GTK does. Correct and chatty: a
  drag across a paragraph becomes dozens of ownership changes, each a round of
  messages to every other client. Qt publishes on release, and so does this.
