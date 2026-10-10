# ADR-0589: A cursor shape may be the application's picture, and a drag follows its holder's cursor

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** the Gwent clone's issue list (GB-033),
  [ADR-0057](0057-the-cursor-rides-on-the-painted-box.md)

## Context

`dev.goldberry.render.Cursor` was a closed enum of CSS's shapes, and
`Sdl3Window.setCursor` mapped each to an `SdlSystemCursor`. `grab` and
`grabbing` fell back to `move` "until custom image cursors ship" (ADR-0057).
Neither the stylesheet nor the API could name a picture, and nothing called
`SDL_CreateColorCursor`.

Also, while the pointer was captured, `PointerRouter.updateCursor` returned
at once, so a drag kept the shape it began with. A card that says `grab`
under the pointer and `grabbing` once it is held could not show the second.

The downstream ships its own cursors: nine shapes, each at 32, 48, 64 and 96
pixels with a hot spot per size. Drawing a picture under the pointer and
hiding the system cursor would put a second cursor in the application.

## Decision

**An application gives shapes its pictures through `Application.cursors()`,
and the shapes stay CSS's.** So `cursor: grab` in a stylesheet, a widget's
own `pointer` and `Window.cursor(Cursor)` all show the application's picture
for the shape they name, and a shape with no picture shows the platform's.

- `dev.goldberry.input.cursor.CursorImage(Cursor shape, Image picture, int
  hotX, int hotY)` is one size of one shape. A shape may have several. The
  smallest is the shape at 100%, and each size has its own hot spot in its
  own pixels. `CursorImage.byShape` gathers them for a backend and refuses
  two sizes of one width.
- The SPI carries them as `dev.goldberry.render.cursor.CursorPictures`, a
  shape and its `CursorPicture`s (straight-alpha `IconImage` and hot spot),
  smallest first, through **`Backend.setCursorPictures(List)`**. It is on
  the backend, not on a window, because SDL's cursor is process-global. The
  default does nothing, and the headless backend records what it is given.
- `Launcher` reads `cursors()` once, after the first window opens and before
  `start`, as it reads `icon()`.

**Which size SDL is given** follows how SDL draws a picture cursor on each
driver, read from SDL 3.4.16's sources:

- Wayland and macOS treat the surface as the cursor at 100% and pick among
  its alternates by the display's scale. Windows does the same when
  `SDL_MOUSE_DPI_SCALE_CURSORS` is on, which `SdlCursors` sets before it
  makes the first picture cursor. On these, every size goes in: the smallest
  as the surface, the rest through `SDL_AddSurfaceAlternateImage`.
- X11 draws the surface pixel for pixel and never reads the alternates. There
  the toolkit picks: `CursorPictures.nearest(scale)` takes the size whose
  width is nearest the base's times the window's scale, the larger of two as
  near, and gives it alone.

The SDL side is `Sdl3Cursors`, a class of its own beside `Sdl3Backend`. It
holds the pictures, the per-driver choice and the platform shapes, which used
to be `Sdl3Window.toSdl`. `SdlCursors` in `:natives` makes a picture cursor
on first use under a key the caller chooses (shape and first width), caches
it, and destroys them all when the pictures are replaced. A picture SDL
cannot make falls back to the platform's shape. When a picture is showing
and the platform cannot make the shape now asked for, the default arrow
replaces the picture, so a picture is never left up for a shape nobody asked
for.

**A drag follows the capturing box's own cursor.** At capture,
`PointerRouter` remembers what the captured element's own box says
(`HitTest.cursorOf`: its topmost region's shape, `default` included). In
every frame painted during the drag, when that changes, the pointer takes
the new shape. Everything else stays frozen as before: the boxes under the
pointer, and an inherited shape. A label held inside a `pointer` button
says `default` throughout, which is no change, so it keeps the hand.

**The native ABI is 22**: `SDL_CreateColorCursor` is exported, and
`GOLDBERRY_ABI_VERSION` and `GoldberryShim.SUPPORTED_ABI_VERSION` move
together. The Linux library is built here, and the macOS and Windows ones
come from CI.

## Not done

- `cursor: url("…") x y, <keyword>` in the stylesheet. The entry called it
  optional, and a shape-wide picture already reaches every `cursor:` the
  stylesheet writes.
- Animated cursors (`SDL_CreateAnimatedCursor`).
- On X11 the size is chosen when the shape changes. A window moved to a
  display of another scale shows the new size at the next shape change.

## Consequences

- `CursorTest`'s *a repaint during a drag does not thaw the frozen shape*
  repainted the **captured** box with a new shape and asserted the freeze.
  That is the case this record changes, so the test now repaints the box
  under the pointer. Three tests are new: the held box's own change, an
  inherited shape kept, and an explicit `capturePointer`.
- New tests: `CursorImageTest`, `CursorPicturesTest`, `CursorOfTest`,
  `ApplicationCursorsTest` (headless, through `Goldberry.launch`) and
  `Sdl3CursorsTest`, which runs the binding under SDL's `dummy` driver. That
  driver makes picture cursors out of any surface and no system cursor at all.
- The guide's cursor section and the limitations row change, parked in
  `docs/snapshot/guide-input-cursors.md` until the release.
