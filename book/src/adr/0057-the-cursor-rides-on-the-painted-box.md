# ADR-0057: The cursor rides on the painted box

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §7.3, §8; [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md), [ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md), [ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)

## Context

§7.3 wants a standard cursor shape set mapped to native cursors by the backend,
set from CSS (`cursor: pointer`) or from code. The awkward question is not the
mapping — SDL has `SDL_CreateSystemCursor` — but *where the shape lives* between
the stylesheet that declares it and the pointer motion that needs it.

The style that decided the shape does not survive the frame.
[ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md) materializes the render
tree as a `Box` tree per frame and throws it away; `WidgetRenderer` resolves a
`ComputedStyle` per element per frame and keeps none of them. So by the time the
pointer moves, "what cursor does this element want" has no one left to ask —
unless something wrote it down.

## Decision

**The shape is a property of the painted rectangle.** `Box` carries a `Cursor`,
`HitTest.Region` records it while capturing, and the router reads it off the
rectangle under the pointer. That is the same route hit testing already takes and
for the same reason ([ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)):
what the cursor should be is a question about what is on screen, and the box tree
is what is on screen.

**Inheritance is the stack of rectangles, not the element tree.**
`HitTest.cursorAt` scans backwards and takes the first rectangle that asks for
anything other than `DEFAULT`. So a `cursor: pointer` on a button applies to the
label inside it without the label repeating it — which is what CSS's inherited
`cursor` means to a user — arrived at by walking the only structure input has at
that point. A box with no owner still counts: scenery is not clickable but it is
visible, and an overlay that says `cursor: wait` means it.

**`Cursor` lives in the SPI package, and its names are CSS's.** `NOT_ALLOWED`,
`EW_RESIZE`, `GRAB` — so `cursor: ew-resize` resolves through the same
uppercase-and-underscore rule that already maps `space-between` onto Yoga's
`SPACE_BETWEEN`, with no translation table to drift. It sits beside `PixelFormat`
and `DisplayScale` rather than in `input` because both the render tree and the
input router name it, and neither should have to depend on the other to do so.

**`cursor` is a third category in `ComputedStyle`, and the record says so.** §8's
property split has two halves — compiled to Yoga, resolved for paint — and this
is neither: it compiles to no engine and is read by input. Rather than pretend it
is a paint property, the field is documented as the exception it is.

**The router pushes; it does not know what it is pushing to.**
`PointerRouter.onCursorChange(Consumer<Cursor>)` is wired to the backend window by
`Window`. The router still knows nothing about the platform, and a test wires it
to a list.

**Only changes are reported.** This is asked on every pointer motion — every pixel
of a drag — so the notification is edge-triggered, and `SdlCursors` additionally
skips `SDL_SetCursor` when the shape is already showing. The headless backend
counts changes rather than calls, so a regression here is a number that climbs
with the mouse.

**The shape is frozen during a capture.** A drag decides what the pointer looks
like when it starts. A cursor that flickered as the pointer crossed the widgets
underneath would be advertising things the user cannot currently interact with.

**Cursors are process-global, because they are.** `SDL_SetCursor` sets what the
mouse looks like everywhere; X11, Wayland and Win32 all work this way, and the
pointer is over one window at a time. So the cursors are owned by the *backend*,
created on first use and destroyed together, and the window that asks is by
definition the one the pointer is in. The `SDL_Cursor *` never leaves `:natives`:
it would otherwise be a lifetime two modules shared.

**Missing shapes fall back rather than fail.** `grab` and `grabbing` are a CSS
invention with no system cursor in `SDL_SystemCursor`, X11's cursor font, or
Win32's `IDC_*` set; they map to `move`, which says the same thing less
precisely. A shape SDL declines for any other reason — a stripped-down cursor
theme — leaves the pointer as it is, logged at debug. Nobody's window should fail
to open because they wanted a hand instead of an arrow.

**The cursor calls are optional at link time.** Bound lazily, and an
`UnsatisfiedLinkError` disables the feature rather than the backend — the same
argument `SdlVideo.optionalDowncall` makes for the display-mode calls (ADR-0047).
A `libgoldberry` built before these symbols were exported keeps opening windows.

**`SDL_SYSTEM_CURSOR_*` joins the constant registry.** They are ordinals in an
enum upstream has already inserted into the middle of once — `POINTER` is 11 in
SDL3 and did not exist in SDL2 — and a wrong one shows the user the wrong cursor
and reports no error at all.

## Alternatives considered

- **Keep a `Map<Element, Cursor>` from the last render.** Rejected: a second
  structure to keep in step with the box tree, with nothing checking that it is.
  The same argument [ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)
  made against a parallel array of owners.
- **Ask the element for its style at pointer time.** Rejected: it does not have
  one. Re-resolving the cascade on pointer motion would also mean styling against
  a frame that has not been painted, which is the mistake ADR-0054 exists to
  avoid.
- **Put `Cursor` in `input` and have `layout` import it.** Rejected: `input`
  already depends on `layout`, and the reverse edge would make a cycle out of two
  packages that are otherwise cleanly ordered.
- **A per-window cursor in the SPI, hiding SDL's global one.** Rejected: it would
  be a lie that costs bookkeeping. The platform model is one pointer with one
  shape, and the SPI method is on `BackendWindow` only so that a backend which
  genuinely is per-window can implement it that way.
- **Walk the element tree for inheritance.** Rejected: it gives a different answer
  from the one the user sees whenever an element paints outside its parent, and
  the rectangles are already ordered by what is on top.

## Consequences

- **`cursor: pointer` works end to end**, stylesheet to platform, and a golden
  image is not needed to prove it: the shape is a value on a rectangle.
- **The showcase sets a cursor, and that is not decoration.** It is the only thing
  in the repository that makes `SDL_CreateSystemCursor` and `SDL_SetCursor`
  actually run, and CI drives the showcase under Xvfb on all three platforms — so
  the calls are exercised rather than left as bindings nothing has ever called.
- **`Box` and `ComputedStyle` each gained a component**, which touched every
  wither and every branch of `ComputedStyle.with`. That is the cost of records
  with positional construction, and it is paid once per property.
- **`grab` and `grabbing` are not the shapes their names promise** until custom
  image cursors ship (§7.3). Written down here so the fallback is a decision
  rather than a surprise.
- **Hiding the cursor is bound and unused.** `SdlCursors.hide()` exists because a
  text editor hiding the pointer while typing is ordinary; nothing calls it yet.
- **Nothing recomputes the cursor when the tree changes under a still pointer.**
  A widget that becomes disabled without the pointer moving keeps the old shape
  until the next motion. Fixing it means re-running `cursorAt` after each paint
  with the last known position, which is worth doing when something can actually
  change that way.
