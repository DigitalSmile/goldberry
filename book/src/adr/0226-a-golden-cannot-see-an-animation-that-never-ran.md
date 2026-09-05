# 226. A golden cannot see an animation that never ran

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0176](0176-a-dialog-is-a-widget-and-showing-one-is-not.md).

## Context

`dialog` shipped with a closing animation that did not animate. Every golden
image passed, and the entry that recorded it said exactly why:

> A golden drives `render` by hand and never asks whether the frame loop would
> have, so a widget that answered `isAnimating` with `false` while it was fading
> produced perfect pictures of an animation that never ran. […] the corpus cannot
> catch this class of bug **by construction** — an assertion on `isAnimating` is
> the only thing that can.

Both halves are true, and the entry stopped one step short. "The corpus cannot
catch it" is a fact about goldens; "an assertion on `isAnimating` is the only
thing that can" is a *specification for a test that was never written*. Left as
prose in a TODO list, it is a note that a future author has to read at the moment
they are least likely to — while adding the twelfth animating widget, after
watching their goldens pass.

The defect is also asymmetric in a way that matters. Answering `false` while
moving freezes the animation mid-way; answering `true` while still keeps a window
awake for ever. Both are invisible to a picture, and only the second is
noticeable by anyone not looking for it.

## Decision

**`AnimationSweepTest` — two rules, neither about pixels.**

**Rule one, structural: a widget that holds a `Phase` declares `isAnimating`.** A
`Phase` is this toolkit's word for "arriving or leaving on the frame clock", so
holding one and never asking the loop for the next frame *is* the defect, spelled
in a form reflection can read. It costs nothing and it fires on the day a new
overlay is written rather than the day somebody runs it.

**Scoped to widgets — things that implement `Paints`.** A `State` and a value
record may both hold a phase — a dialog's `closing`, a toast's `Reflow` — and
neither is asked for the next frame: the loop walks the *element* tree and asks
the widgets in it. Holding a phase somewhere unpainted is how a phase reaches a
widget, not a fault. This distinction is the whole of what makes the rule usable;
without it the sweep names six false positives and gets deleted.

**Rule two, coverage: every declaration of `isAnimating` in the catalog has a
test beside it that names the method.** Not every animation is a `Phase` — a
tab's transition is a number, a scrollbar's fade is an idle clock — so this
catches what the structural rule cannot. Beside it, in the same package, because
a widget whose only mention of `isAnimating` is in some distant integration test
is a widget whose author did not think about it.

Rule two is deliberately weak about *what* is asserted. An arch test cannot tell
a good assertion from a bad one. It can tell that there is one, which is the
difference between this class of defect being found late by a human and not at
all.

**It found one gap immediately**, which is the argument for it: `ScrollViewport`
and `ScrollFade` had no `isAnimating` assertion anywhere. `ScrollFadeTest` now
covers the fade in both directions — that it keeps asking through the idle period
and the fade, that it *stops* once the bars are gone, that held bars are still
rather than moving — and `ScrollTest` covers the viewport that delegates to it.

## Alternatives considered

- **Leaving the lesson in `TODO.md`.** It is a note, and notes are read by people
  who already know. The entry itself argued for an assertion.
- **Making the golden harness drive the frame loop**, so a picture of a
  non-animation could not be produced. It is the version that would catch
  everything, and it changes what a golden *is*: the corpus's value is that it
  rasterizes one frame deterministically at a chosen instant, and a corpus that
  ran a loop would be slower, flakier, and would still need somebody to decide
  how many frames "enough" is.
- **A behavioural sweep that constructs every animating widget mid-animation and
  asserts `isAnimating()`.** The rule with real teeth, and it needs a plausible
  constructor call for each of a dozen package-private records — which is a
  fixture the next author has to extend before their widget compiles, at which
  point they have thought about the question anyway. Rule two gets most of the
  value for none of the fixture, and the per-widget tests it demands are better
  assertions than a generic one could be.
- **Requiring the assertion in a named test class per widget.** Stricter, and it
  would have failed on `MessageBox`, whose `isAnimating` is asserted in
  `MessageGoldenTest` — a golden that also checks the loop, which is exactly the
  right thing and would have been forbidden by a naming rule.

## Consequences

- **A new arch test with two rules**, joining `SemanticsSweepTest` as the second
  sweep that enforces something no picture can show.
- **A gap closed on the way in.** `ScrollFadeTest` is eleven assertions that
  nothing had: §2.4's fade curve, and both ends of the frame contract.
- **Rule two is package-granular**, so a package with two animating widgets and
  one test satisfies it. Stated rather than hidden: the rule is a floor, and the
  per-widget assertions are where the real checking happens.
- **A false negative remains, deliberately.** A widget that answers `isAnimating`
  from something other than a `Phase`, and whose package already has an unrelated
  `isAnimating` mention, passes both rules while being wrong. Closing that needs
  the behavioural sweep above and its fixture.
- **The `TODO.md` entry can close.** What it asked for now exists, and the lesson
  lives in a class that fails rather than a paragraph that is read.
