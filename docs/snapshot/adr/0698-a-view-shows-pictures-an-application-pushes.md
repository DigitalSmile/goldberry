# ADR-0698: A view shows pictures an application pushes

- **Status:** Accepted
- **Date:** 2026-11-14
- **Relates to:** GB-019's `PictureRenderer`, and the borrowing rule of
  `MediaPlayer.shownPicture()`

## Context

A call shows each participant's video in a tile, from frames the application
decodes off the network. `VideoView` took a `final` `MediaPlayer`, so a tile
drew its frames on a CPU `canvas` (Tessera's G-07, here T-07).
`PictureRenderer` already drew an app-built `VideoPicture` or `VideoPlanes`
on the GPU from a `Canvas3dRenderer`, which was the answer until now.

The plan left the package open.

## Decision

A **picture source** is an interface in `dev.goldberry.media.picture`, beside
the pictures it hands out:

- `PictureSource.shownPicture()`: what to draw now.
- `untilNextPicture()`: when a source knows its next picture's time, as a
  player does. Empty by default.
- `onPicture(Runnable)`: for a source that knows the moment a picture
  arrives, as a pushed one does. A no-op subscription by default.

`MediaPlayer` implements it with the two methods it already had.
`PushedPictures` is the source an application pushes into, from any thread:
the newest picture is shown, `clear()` shows nothing, and every push tells
the listeners.

`PictureView(source, fit)` shows any source. It is the same `video-view` node
as a player's view, built on the same surface, so it is styled and drawn the
same way: on the GPU as a layer where the frame shows one, and on the CPU
otherwise. It redraws when the source's next picture is due, or when one
arrives, handing one redraw to the UI thread however many pictures arrive
before it runs.

`VideoView` **stays a player's view**. It also tells the player which form
to decode into (planes while the GPU shows them, converted on the CPU), and
`media-player` and `media-controls` are built on the player too. A source has
no form to choose: a `VideoPicture` is drawn everywhere, and `VideoPlanes`
on the GPU only.

**The borrowing rule** is every source's, and is written on
`PictureSource`: a picture handed out keeps its bytes until two more have
been handed out after it. For `PushedPictures` that binds the application:
three buffers used in turn keep it.

## Consequences

- `VideoSurface`, the leaf behind both views, reads a `PictureSource`.
- `PictureView` takes no markup: a source is code.
- Tested by `PictureViewTest`: the newest picture and a clear, listeners on
  the pushing thread until removed, a player as a source, the view's surface
  drawing what the source shows when it renders, the pixels, and a hundred
  pushes making one redraw.
