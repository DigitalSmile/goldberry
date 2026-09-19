# 424. A tree mounted once, photographed repeatedly

Date: 2026-09-19

## Status

Accepted. Closes `book/src/TODO.md`'s "No animation strip", under *Rendering
without a window*. Answers the last consequence of
[ADR-0284](0284-a-picture-with-no-window-under-it.md): "An animation strip is not
supported. One call, one picture … which is a different object with a lifetime —
and nothing has asked for it."

## Context

`Offscreen.render(Widget)` mounts a tree, advances a virtual clock once, paints,
and unmounts. One call, one picture, and the unmount is not incidental: it is what
makes a render leak nothing and what makes two renders unable to see each other.

The obvious way to get four frames of a transition out of that is to call it four
times with four settle times, and it does not work. It produces four **first**
frames. A `spinner` at 48 ms is not the same picture as a spinner that has been
spinning for 48 ms, and for anything with state the gap is wider than the
animation: a `text-area` has just learnt its own width, a `masonry` has just
finished arranging, a `State`'s `initState` has just run. Every frame would be a
photograph of a tree recovering from having been born.

So the entry is right about the shape — "an object with a lifetime rather than a
builder that renders once" — and the hard part is not the object. It is deciding
what survives between two of its frames, because the answer is "almost
everything", and the one thing that must not survive is the thing a caller would
never think about.

## Decision

**`io.github.digitalsmile.goldberry.offscreen.Filmstrip`**, a closeable object
that mounts a tree once and hands out one picture per call while the caller drives
its clock.

```java
try (var strip = Offscreen.of(400, 120)
        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
        .strip(new Banner("saved"))) {
    var frames = new ArrayList<Image>();
    while (strip.isAnimating() && strip.frames() < 60) {
        frames.add(strip.frame());
        strip.advance(16);
    }
}
```

`Offscreen.strip(Widget)` is a third terminal beside `paint` and `render`, and it
is a terminal on the existing builder rather than a second builder with its own
six setters — the size, the scale, the stylesheets, the fonts and the background
are already knobs, and a second copy of them is the duplication
[ADR-0423](0423-one-frame-sequence-shared-by-the-window-and-the-buffer.md) had
just finished removing.

`advance` moves the clock and draws nothing; `frame()` is what turns a number into
a picture. Two advances with no frame between them are one advance, which is how a
caller skips a boring stretch of a long animation for free.

### What lives between frames, and what does not

This is the decision, and the table is the ADR:

| | kept | why |
|---|---|---|
| the element tree | **yes** | the whole point. State, scroll offsets and a learnt width carry over; `dispose` runs once, at `close()` |
| the render tree | **yes** | retained layout, so Yoga re-lays out only what moved (ADR-0069) |
| the renderer | **yes** | its shaping cache holds every paragraph already shaped, and self-tunes to the frame (ADR-0299) |
| the router | **yes** | a `Measured` widget is told its region *changed* rather than told it again |
| the clock | **yes** | it is the one object the caller is actually driving |
| **the pixel buffer** | **no** | see below |

**The buffer is allocated per frame, and that is the one entry that is not
obvious.** An `Image` handed back is a *view* over the pixels it was rendered into
and never a copy of them — that is `Image.of(PixelBuffer)`, from ADR-0283, and it
is why an offscreen render does not copy a megabyte to say what it drew. A strip
that reused one buffer would therefore hand out ten references to the tenth
picture, and the caller would find it out by encoding all ten and getting the same
PNG. Ten frames of a 400×120 strip is 1.9 MB; the alternative is one buffer and
ten identical images, which is not an optimization but a defect with a smaller
memory profile.

`FilmstripTest.everyFrameGetsItsOwnBuffer` is the assertion, and it is written the
way the bug would actually be met: take a frame, keep its pixels, take another
frame, and check the first picture is still the first picture.

### The mount pass, and the pass it deliberately skips

Opening a strip runs **one** of `Offscreen`'s three passes: a lay-out and a region
capture, with the clock still at zero.

That keeps half of ADR-0284's argument and drops the other half, on purpose:

- **Kept: the region feedback.** A `text-area` learns its own width from the
  rectangles a laid-out frame produced. A strip whose first frame had never fed
  them back would photograph the entire animation of a widget correcting a first
  guess it should never have been showing — an animation the application does not
  have.
- **Dropped: the settle.** `Offscreen` advances past the transition duration
  because a still picture of four banners at zero opacity is useless. A strip is a
  request to photograph exactly that. Frame zero is frame zero.

So `settle(int)` does not apply to a strip and is ignored, which the javadoc says
at `strip` and `FilmstripTest.ignoresTheSettleTime` pins.

After the mount pass, each frame is lay-out → paint → capture, which is the order
a window runs (ADR-0423). Feedback therefore arrives on the *following* frame,
exactly as it does in a window — a strip and a window settle a self-arranging
widget over the same number of frames, rather than the strip settling it faster
because it was being helpful.

## Consequences

- **A strip must be closed, and the teardown order is ADR-0284's.** No frame is
  live by the time `close()` runs, because `frame()` ends its own; what remains is
  the render tree before the element tree, because a `State` that owns a `Font`
  closes it in `dispose` and Blend2D's workers are still holding it until the join.
  Getting that wrong was a SIGSEGV in a worker thread, once, and it is written down
  in two places now.
- **A constructor that fails releases what it built.** A strip is mounted inside
  its constructor, so a widget that throws on its first build would otherwise leave
  a mounted tree, a native Yoga tree and possibly a font book with no reference
  anywhere to close them — and the caller has no strip to close. The fault that got
  the strip torn down is the one thrown; anything raised while releasing is
  suppressed onto it.
- **Pictures outlive the strip.** No two frames share a buffer, so closing
  invalidates none of them. `framesSurviveClosing` asserts it, because the opposite
  is exactly the sort of thing a caller discovers after writing the file.
- **`isAnimating()` is answerable from the moment the strip is open**, because the
  mount pass is a pass — a caller can loop on it without taking a throwaway frame
  first. It is **not** a promise the picture has settled: a widget that moves
  without CSS, a chart streaming points, animates and this says false for it. A
  caller with a bound on frames wants both, which is what the example above does.
- **A strip cannot take the `font(Font)` form and says so.** A single font ignores
  `font-family`, `font-size` and `font-weight` entirely, and a transition whose
  `font-size` moves is one of the things a strip exists to photograph. Refused at
  `strip()` rather than producing a picture that is quietly not of the animation.
- **A strip from a `Studio` shares the book and not the renderer**
  ([ADR-0441](0441-what-a-render-may-keep-and-the-thread-it-may-keep-it-on.md)). A
  renderer holds exactly one clock, so a strip on a studio's renderer would move
  the clock under every still picture taken beside it. The book is the expensive
  part and is shared; the cascade index is rebuilt, and that is what a strip costs.
- **Nothing here is a frame loop.** No refresh, no request for the next frame, no
  system clock anywhere in it. A strip advances when told and by exactly as much,
  which is what makes the tenth frame of a transition the same picture on every
  machine — the same property `Offscreen` has and for the same reason.
- **The name.** It is the strip, and the caller develops it one frame at a time;
  `image.anim` already calls a multi-frame picture what it is (ADR-0382), so the
  vocabulary was there. `AnimationStrip` says the same thing and one word longer,
  and a name like `Recorder` or `Session` describes the machinery rather than what
  comes out.

## Alternatives considered

- **`Offscreen.render(Widget, int[] times)` returning a list of images.** One call,
  no lifetime, no `close()`, and it decides for the caller when to stop. A caller
  who wants "frames until it stops moving" cannot say so, and a caller who wants
  the tenth frame has to pay for nine. The clock is the input; a list of times is a
  guess about which inputs matter.
- **Reuse the buffer and copy on the way out.** Symmetrical with the decision
  above, and worse: it pays a memcpy per frame to save an allocation per frame, and
  it makes `Image.of(PixelBuffer)`'s borrowed-buffer doctrine — which the whole
  offscreen path is built on — a special case here.
- **Advance the clock inside `frame()`, by a fixed interval.** A strip would then
  be a 60 Hz camera and nothing else. `advance` and `frame()` being separate is
  what lets a caller take two pictures of one instant, or jump 400 ms in one step.
- **Make `Offscreen` itself stateful, with `render` callable repeatedly.** The
  builder would then have two modes and a caller would have to know which one they
  were in; and `Offscreen`'s current promise — the tree is unmounted before it
  returns, so nothing survives the picture — is worth more than the method name.
- **A settle on the strip as well, for symmetry.** It is the one knob a strip
  cannot want: a caller who wants to start 200 ms in calls `advance(200)` before
  the first frame, which is the same thing said in the object's own vocabulary.
