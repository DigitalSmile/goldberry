# 166. A raised thing is told apart by its edge, and a group box holds its title

Date: 2026-08-20

## Status

Accepted. Corrects two decisions in
[ADR-0164](0164-elevation-is-an-edge-and-a-closed-section-is-absent.md) and
finishes three things §5 asks for that it left out.

## Context

Five reports, from looking at the Panels screen:

> 1. The top bar with counter is small now, only at Panels tab
> 2. In black theme I do not see any visual differences between panel and card
> 3. What is the purpose of group box? I thought I should group elements with
>    title and border.
> 4. Make carousel animations
> 5. Make an option to collapse to show open only one at a time. Add animations

Three of those are defects and two are §5 surface that was deferred. The two
defects that matter — 2 and 3 — are both ADR-0164 being wrong rather than
incomplete, and both were only findable by looking at the thing.

## Decision

### `panel` gets the rule §5 always asked for

§5: "**`panel`** — plain surface: `--gb-surface`, border, radius tokens. The
building block; no elevation."

**There was no `panel` rule in `controls.css` at all.** The widget's own javadoc
said it "sets nothing at all, not even a background", which contradicted the
specification and made every document that wanted a surface invent one. The
showcase invented one that looked exactly like a card — which is the whole of
report 2. A building block that draws nothing is not a building block.

### Elevation is an edge, and `--gb-surface-2` was never an elevation

ADR-0164 said "elevation is an edge" and then hedged by *also* stepping the fill
to `--gb-surface-2`. Both halves were wrong.

`--gb-surface-2` over `--gb-surface` is eight levels on the Nord dark ramp, and
eight levels is not an elevation anybody can see — report 2 again. And on the
**light** theme it is a step *down*: `--gb-surface` is `#ffffff` there, so a card
built on `--gb-surface-2` read as **recessed**. The token means "the second
surface", and it never promised to be an elevation.

So two tokens that say what is meant:

| Token | Dark | Light |
|---|---|---|
| `--gb-surface-raised` | `nord2`, a step up | `#ffffff`, because white is the top |
| `--gb-border-strong` | white at 20% | black at 16% |

The **edge** is the load-bearing half and it is an *alpha over whatever is
underneath* — the only way to say "lighter than its own surface" in a subset with
no colour functions — so it lightens on dark, darkens on light, and stays right on
a card sitting on a page, on a panel, or on another card, without either theme
stating it twice.

On light the two themes genuinely differ: there is no room above white, so a card
there is told apart by its edge alone. That is not a shortfall, it is what a light
theme has to offer, and a white card with a defined edge on a near-white page
reads as raised.

### A `group-box`'s frame encloses its title

ADR-0164 put the title **above** the frame, reasoning that a `fieldset`'s legend
*through* the border needs a notch the subset cannot express. The premise is
still true; the conclusion was wrong, and report 3 is the proof: *"what is the
purpose of group box? I thought I should group elements with title and border."*

A heading floating over a bordered box is a heading and a `panel`. Nothing about
it says the two belong together, and an untitled one was indistinguishable from a
card — so the widget had no purpose that two existing widgets did not already
serve.

The border now goes round **both**. The title is a header row inside the frame,
tinted and ruled off from the body. That is a titled group with one look and no
ambiguity, it needs nothing the subset has not got, and it still wraps at a narrow
width where a legend through a border would break the frame.

### `Phase` is shared, and two more things arrive on the frame clock

`TabPhase` was written for `tabs`
([ADR-0109](0109-a-tab-arrives-and-departs-on-the-frame-clock.md)) and had nothing
tab-shaped in it. It is now `widgets.core.Phase`, and `carousel`'s slides and
`collapse`'s body arrive through it.

Both are **arrival only**, and both for reasons that are the widget's own:

- A carousel builds only the current slide, so holding the outgoing one alive for
  the length of a cross-fade would be building a slide that has been moved away
  from — the one thing "only the current slide is built" says it does not do.
- A `collapse` unmounts its body when it closes, and holding a subtree alive for
  160 ms after it has been asked to go away is exactly what §5 says it does not
  do. Closing is instant and opening is not: **asymmetric on purpose**, because
  the thing worth animating is content appearing where there was none.

Opacity and a small translation, and nothing else — §1.7's whitelist is the
compositor-cheap set, and neither widget animates its **height**, which §5
forbids and always will.

### `accordion=#true` inflates to a widget

§5 puts the flag on the containing `column`, and is right to: "one open at a time"
is a rule about *siblings*, which no section can enforce about the others.

But `column` is the most-used container in the toolkit and it is a plain record.
Making it stateful so one flag can be honoured would give every column in every
document a `State` object it never uses, and statefulness is a property of the
type rather than of the instance — it cannot be conditional.

So `column accordion=#true` **inflates to an `Accordion`**, which reports `column`
as its own CSS type and adds an `accordion` class. A document writes what §5 says,
a stylesheet still sees a column, and an ordinary column pays nothing.

The sections become **controlled** — each re-issued with the `open` the accordion
decides and an `onToggle` that reports back — which is `radio-group`'s
arrangement exactly ([ADR-0073](0073-a-composite-is-one-tab-stop.md)). A section
the *application* already controls is left alone: two things deciding one boolean
is a bug, and the application asked first.

## Alternatives considered

**Add `box-shadow` to the subset**, again. It settles cards, popovers and dialogs
together and it is a second rasterization pass per shadowed box on a CPU
rasterizer. ADR-0164 refused it; nothing here changes that arithmetic.

**Keep the group box's title above the frame and make it look attached** — a
tighter gap, a matching background. It is the same two boxes with less space
between them, and the question "why is this not just a heading and a panel?" still
has no answer.

**Make `Column` stateful.** One flag, paid for by every column ever built.

**A separate `accordion` markup node.** Cleaner internally and a deviation from §5
for no gain to the author: the flag on the container is the right *syntax*, and
what it inflates to is an implementation detail that the CSS type keeps invisible.

**Cross-fade the carousel's slides.** Needs both slides alive at once, which
contradicts §5's "only the current slide is built" — and the reason for that rule
(a slide nobody can see should not hold subscriptions) does not stop applying for
160 ms.

**Animate a `collapse`'s closing.** Means keeping the body mounted after it has
been closed, which is the thing `collapse` exists not to do.

## Consequences

**Chrome does not shrink.** Report 1 was a title bar half its height on one
screen: Yoga's children shrink by default, so a window whose content asks for more
height than there is takes it out of whatever will give, and a title bar with a
definite height is the most willing thing in the tree. `#bar` is `flex-shrink: 0`
now. If the content does not fit, the content is what scrolls.

**Nineteen goldens moved**, and every one of them was reviewed by eye rather than
accepted. Most are the new `panel` rule showing up as a backdrop in tests that
had been asserting against a transparent one — `badge-on-surface`,
`knob-on-surface`, `segmented-on-surface`, both `overlay-corner`s and the
Scrolling screen, all of which now show the surface the specification always said
a panel had.

**Two new tokens**, and the reason to accept them is that the alternative was a
widget silently choosing a colour. `--gb-surface-raised` and `--gb-border-strong`
are both semantic — "the surface of something raised", "the edge of something
raised" — and both are answered differently by the two themes because the themes
genuinely differ about how much room there is above a surface.

**`carousel` and `collapse` keep the frame loop awake while something arrives**,
and only then. A carousel sitting on one slide and a section that has been open a
while both ask for nothing, so a window with either in it is as idle as a window
without.

**A section that started open does not animate.** It was there on the first frame;
fading it up would be animating the window opening.

**`accordion` is the fourth composite to wire its children.** `radio-group`,
`tabs`, `menubar` and now this. The shape is stable enough to be a pattern: the
container holds one number, and each child is handed its own half of it — which is
also why there is no second piece of state that could disagree with the first.
