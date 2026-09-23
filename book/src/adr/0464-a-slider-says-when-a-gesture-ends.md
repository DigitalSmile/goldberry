# 464. A slider says when a gesture ends

Date: 2026-09-23

## Status

Accepted. Adds one hook to `slider` in `:widgets` (`docs/core-widgets.md` §3),
asked for by `goldberry-media`'s seek bar (`docs/goldberry-media.md` §3,
"Seeking").

## Context

`slider` reports every step of a drag through `onChange`, snapped and clamped,
which is what a thumb that follows the finger needs. A media seek bar needs a
second fact: when the user **lets go**. §3 scrubs to keyframes while the bar is
dragged, because an exact seek per step would decode from the keyframe every
time, and asks for one exact seek on release. With only `onChange`, every drag
step was an exact seek (the phase 2 `audio-player` did exactly that, and the
Engine coalesced them), and there was no way to resume a player paused for the
drag at the right moment.

## Decision

`Slider` gains `onCommit`, a twelfth component and a wither
(`slider.onCommit(value -> …)`), and `commit=` in markup. It is told the same
snapped, clamped value `onChange` would be:

- once when a press or a drag on the slider is **released**, from the release's
  position, which is where the last move put it;
- after **every key step**, since a key press is a whole gesture.

The eleven-component constructor stays, so no existing slider changes.

## Alternatives considered

- **`onChangeStart` and `onChangeEnd`**, as Flutter has. The start is the first
  `onChange` of a gesture, which a caller can tell for itself; one hook is
  enough.
- **Commit only on pointer release.** Then a keyboard user would never commit,
  and a seek bar driven by arrows would scrub for ever.
- **Let the media widget watch the pointer itself.** It would duplicate the
  slider's own capture and travel arithmetic, and get the release position
  slightly differently wrong.

## Consequences

- `media-controls`, `audio-player` and `media-player` scrub while dragging and
  seek exactly on release (§7, S2).
- A slider in a form can save on commit rather than on every step.
- The record's equality now includes one more lambda, which a markup-built and
  a Java-built slider only share when both have none: §11's parity comparison
  still holds for every slider that does not set it.
