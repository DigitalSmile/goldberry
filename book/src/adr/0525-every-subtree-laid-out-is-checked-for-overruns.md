# ADR-0525: Every subtree laid out is checked for overruns

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** amends [ADR-0375](0375-a-box-that-does-not-fit-says-so.md);
  [ADR-0394](0394-a-diagnostic-that-fires-on-everything-says-nothing.md),
  [ADR-0524](0524-a-test-drives-a-session-with-a-host-under-it.md),
  `docs/goldberry-gaps.md` entry 28

## Context

ADR-0375 asked one question per frame, Yoga's `hadOverflow` on the root, and
walked the tree for overruns only when the answer was yes. The cost argument was
one foreign call on a frame where everything fits.

The gate misses the case that matters most. Yoga sets the flag on the container
whose flex line ran over, and carries it up through flexed children. It does
not carry it out of an absolutely placed box, and every overlay is one. Deploy
Orc's sign-in wizard pushed two steps out of its 600-pixel dialog, and
`OverflowLog.reported()` stayed empty offscreen. In a window the same overrun
was reported only on a frame where something unrelated overflowed the root.
Whether an overrun was heard depended on other layout. A test asserting the log
was empty proved nothing, and `TextScaleAudit` said as much in its own
documentation.

The log is also process-wide and says each shape once, so even a correct walk
does not give a test a deterministic answer.

## Decision

**The walk that settles the layout checks each subtree it enters, and
`RenderTree.overruns()` answers for the whole tree on request.**

- `RenderObject.settle()` already visits exactly the subtrees laid out this
  pass. It skips one whose boxes and rectangle both held. After settling a
  node's children it calls `OverflowWatch.inspect`, which compares each child's
  rectangle with the node's. The check is arithmetic on rectangles that walk
  has just read: no foreign call, and a name string is built only when
  something overran.
- So a subtree is checked on its first layout and whenever it moves or changes.
  A static frame checks nothing. The root `hadOverflow` call is gone, and so is
  the full walk on every frame of a window already too small.
- `RenderTree.measure`, the popup sizing pass, settles the same way and is
  checked too.
- `RenderTree.overruns()` walks the whole current tree, logs nothing, and
  returns every overrun in tree order. `Session.overruns()` is the same call.
  `TextScaleAudit` now reads it after its last layout instead of the log.
- ADR-0394's exemptions are unchanged: non-visible `overflow`, absolute
  children, a container with no size, a child starting outside, and two pixels.

Measured on a 5,251-node tree, 250 rows of 20 cells, on a loaded machine. A
full re-layout (the window width alternating each frame) took a median of
3.4–4.7 ms with the check and 3.6–4.7 ms without. The difference is inside the
noise. The whole-tree walk alone took 0.10 ms. A static frame does no checking
at all.

## Consequences

- An overrun inside a fixed-width or placed box, a dialog or a toast, is
  reported on the frame it first appears. `OverflowWatchTest` holds the placed
  case, which failed under the old gate, and a later-frame case.
- A layout test asserts `session.overruns()` or `renderTree.overruns()` is
  empty. That answer does not depend on earlier tests. The log stays what an
  author reads and an application can show on a HUD.
- `OverflowLog.forget()` followed by an unchanged frame says nothing until the
  subtree is laid out again. Before, a window too small for its content
  re-reported on the next frame. An application that wants the current list
  asks `overruns()`.
- The showcase may report overruns it never reported before, because they sat
  inside boxes that fit. They are real overruns under ADR-0394's rules, not new
  noise.
