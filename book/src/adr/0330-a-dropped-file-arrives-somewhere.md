# 330. A dropped file arrives somewhere

Date: 2026-09-16

## Status

Accepted. Closes `docs/gaps.md` G35b.

## Context

There was no drop event anywhere in the toolkit. SDL3 has five —
`SDL_EVENT_DROP_BEGIN`, `DROP_POSITION`, `DROP_FILE`, `DROP_TEXT`,
`DROP_COMPLETE` — and none was surfaced, so dragging a PNG from a file manager
onto a window did nothing at all.

Nothing was broken; this is surface that had not been built, and it was found by
needing it rather than by reading the API. The routes that did exist — a file
dialog, and `Ctrl+V` for a clipboard picture — cover the common cases, which is
why it is a gap and not a defect.

## Decision

**One event per gesture, carrying the files and the point they landed on.**

```java
// io.github.digitalsmile.goldberry.Window
public Subscription onFileDrop(Consumer<FileDrop> listener);

// io.github.digitalsmile.goldberry.input.drop
public record FileDrop(List<Path> paths, LogicalPoint at) { … }
```

### The position is half the feature

A board needs to know **where** something was dropped, not merely that it was: the
whole gesture is "put this picture *here*". SDL carries the coordinates already,
so a drop event without them would make the toolkit the reason an application
cannot use one.

They are `LogicalPoint`, window-relative, in the same space every pointer event
and every layout is in — so a drop can be hit-tested against the last painted
frame exactly as a click is.

### The gesture is assembled in the toolkit, not in the backend

The SPI keeps the platform's shape: `BackendEvent.FileDropped` is one file, and
`FileDropCompleted` ends the run. `Window` collects them and raises one
`FileDrop`.

That split is deliberate. Reassembling "a beginning, a moving position, one event
per file and an end" into "these files, there" is arithmetic every application
would otherwise get slightly differently, and it is arithmetic that can be tested
without a desktop — `FileDropTest` drives it through the headless backend and
never touches SDL. What is left in `Sdl3Backend` is one `switch` arm per event
type and a remembered position, and the numbers and offsets in it are checked
against the compiled SDL by the layout probe (ADR-0010).

The backend keeps the last position because **which** events carry one is a
platform's business: `DROP_BEGIN` carries none by SDL's own documentation, and the
others may or may not. The last non-zero answer wins, so a drop always knows where
it was.

### A `Subscription`, not a setter

Every other handler on `Window` is a setter returning `Window`, and those answer a
question about the *window*: how big it is, where it is, whether it may close.
There is one answer to each, so a second caller replacing the first is right.

A drop is aimed at whatever is under the pointer, which in one window is any
number of things. A board and a settings panel in the same window both have a use
for one, and neither should silently overwrite the other. So the listeners are a
list and unsubscribing is closing the `Subscription` — the type `:core`'s `bind`
package already has for exactly this.

### Nothing is read and nothing is checked

The paths are names the platform handed over. Whether they exist, can be opened,
or are what they claim to be are questions for whoever accepted the drop. A name
this file system will not accept at all — a NUL byte — is skipped with a log
rather than taking the whole gesture down: a drop shortened by one bad name is
better than a drop that failed.

The path crosses the SPI as a `String` and becomes a `Path` in `Window`, for the
reason a keycode crosses as an `int`: turning a platform's name into something
Java's file system agrees with is a conversion with a failure mode, and a backend
is not where that should be decided.

### `onTextDrop` is not here

`SDL_EVENT_DROP_TEXT` is the same shape and nothing has asked for it. A second
event with no caller is a second event with no test.

## Consequences

- **The ABI version goes to 11**, with ADR-0329: the `SDL_DropEvent` layout joins
  the probe registry, and four event numbers join the constants it checks. A wrong
  event number does nothing at all — the drop simply never arrives — which is
  precisely the failure that table exists for.
- `BackendEvent` gains two cases. It is sealed, so every exhaustive switch over it
  stopped compiling until it said what it does with them, which is the property
  ADR-0004 chose the shape for.
- A drag that crosses a window and leaves raises nothing: the completion arrives
  with no files behind it and clears the gesture silently.
- The `HeadlessBackend` produces no drops. It has no desktop to drag from; the
  gesture is still fully testable through `Window`, which is where the logic is.
