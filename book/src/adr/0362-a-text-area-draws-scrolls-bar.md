# 362. A text area draws `scroll`'s bar

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A `text-area` has no visible scrollbar",
and amends ADR-0117: `ScrollBar` is public.

## Context

§4 asks `text-area` for "optional auto-grow between min/max rows, scrollbar
beyond". The control scrolled with the wheel and to keep the caret in view, and
drew no bar. The TODO entry named two choices and liked neither: put the text in
a `scroll`, which would fight auto-grow because both want to decide the height,
or write a second bar.

Neither is needed. `ScrollBar` is three numbers and two callbacks: a viewport
length, a content length, an offset, where to report a new offset, and when a
drag starts and ends. The thumb floor, track paging and dragging all live there.
A text area knows all three numbers.

## Decision

**`ScrollBar` is public, and `text-area` builds one over its own offset while
its text is taller than what it shows.**

- `TextAreaState.scrollbar()`: the viewport is the content box's height (the
  rows for an ordinary area, the measured height less padding for one that
  fills), the content is the line count times the line height, and the offset is
  the state's. Null while the text fits, so an area that fits has no part.
- The bar reports through `scrollTo`, clamped to the maximum, and `dragBar`
  holds `.dragging` for the thumb, exactly as `ScrollState` does.
- `TextAreaBox` puts it last among the control's children, beside the clipped
  content layer rather than inside it, positioned down the content box's height
  and against the inside of the right border (a negative padding inset, as the
  gutter strip already does). Its part over the padding is drawn and can be
  pressed.
- **A render that moves the text asks for one rebuild.** The bar is built before
  render, and `laidOut` can move the offset to follow the caret. When it does,
  the state marks itself dirty; the next build puts the thumb where the text is,
  and the render after that lays out the same offset and asks for nothing.
- `text-area:hover` widens the thumb and shows the track, as `scroll:hover` does.

## Consequences

- The bar overlays the last few pixels of the text's width, as `scroll`'s
  overlay bar overlays its content. The reserved gutter is a separate item.
- `gallery-html` and `gallery-markdown` are re-blessed: their source editors
  overflow and now show a thumb.
- `TextAreaGutterTest` finds the content layer by its clip rather than by being
  last.
