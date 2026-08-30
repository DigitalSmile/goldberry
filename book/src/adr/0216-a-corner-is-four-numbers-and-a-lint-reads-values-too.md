# 216. A corner is four numbers, and the lint reads values too

Date: 2026-08-30

## Status

Accepted. Extends [ADR-0215](0215-a-property-the-engine-drops-is-a-rule-that-does-nothing.md),
which caught the other half of the same fault, and answers the question
[ADR-0097](0097-a-selection-that-travels-needs-a-geometry.md) parked.

## Context

Two lines in the running application's log:

```
WARN ComputedStyle - dropping "border-radius": 7px 7px 0 0 is not a valid value
WARN ComputedStyle - dropping "background": none is not a valid value
```

Both are rules in the toolkit's own `controls.css`, and both had been doing
nothing since the widget that wrote them shipped.

- **`group-box-title { border-radius: 7px 7px 0 0 }`.** §5's frame is 8px round
  with a 1px edge, and the header fills the top of it — so the header's top
  corners are the frame's radius less its border, and its bottom ones are square
  where the body carries on underneath. The engine resolved **one** radius per
  box, so the whole declaration was dropped and the header drew four square
  corners, two of which spilled out of the frame's curve.
- **`select text-input { background: none }`.** §3's autocomplete `select` holds
  a real `text-input`, and [ADR-0183](0183-a-combobox-is-a-select-you-can-type-in.md)
  says that editor is the select's *interior*: no edge, no fill, no radius. The
  line above it, `border: none`, worked — `stroke` has taken `none` since borders
  existed. `background` did not, so the field kept the well colour `text-input`
  gives it.

**This is ADR-0215 one property along.** That record made the toolkit's own
sheets lint-able and closed the case where the *property* does not exist. These
two properties exist; it was the *values* the engine would not take. The
difference in the log is a `warn` instead of a `debug` — which is louder and was
read exactly as often, because a start-up stream nobody is watching is a stream
nobody is watching at either level.

## Decision

**A radius is four numbers.** `Corners(topLeft, topRight, bottomRight,
bottomLeft)` replaces `Decoration`'s single `double radius`, `border-radius`
takes CSS's 1-4 shorthand in CSS's order, and `RoundRect` draws it. This is the
change ADR-0215 declined to make for `border-bottom`, and the reason it is right
here and wrong there is the same reason: a rule under a table header is
**expressible as a node** — `table-rule`, a box one pixel tall — and a corner is
not. There is no arrangement of boxes that rounds two corners of a header and
leaves the other two alone, and nothing in this toolkit clips.

**One drawing, not two.** `RoundRect.addTo` takes `Corners`, and the uniform case
emits exactly the point sequence the single-radius version always did — a square
corner is a `lineTo` into the corner point and no cubic at all. That is what
says the hundred boxes in the catalog that write one number did not move, and
the golden images agree: the only two that changed are the two with a
`group-box` in them, by 31 pixels each.

**Circular corners only.** CSS's full grammar allows an ellipse per corner
(`border-radius: 10px / 20px`); this does not. An elliptical corner is a
different curve rather than a different number, and the declaration is dropped
with the warning that names it — the same answer §8 already gives `50%`, which a
box cannot resolve because it has no size until Yoga has run.

**`background: none` is transparent, and `background-color: none` is not.** CSS
divides them: `none` in the shorthand turns off the image layers, and the
longhand takes a colour. The toolkit has no image layer, so the one thing "no
background" can mean to a painter is what it means here — and it is how a rule
turns a fill off without having to know what colour it is turning off, which is
what `border: none` has always done one property up.

**The lint reads values as well as names.** `SupportedPropertyTest` (ADR-0215)
now fails on `dropping "…": … is not a valid value` as well as on `ignoring
unsupported property`. Both are the same fault — a rule that does nothing — and
splitting them by which half was at fault would be a check that catches the
mistake somebody made last time.

**Which forced the lint to run the real cascade.** Feeding raw declarations
straight to `ComputedStyle` was good enough to check a property *name*; for a
value it is not, because every colour in the toolkit is `var(--gb-something)` and
substitution belongs to `StyleResolver`. The unmodified check reported 164
failures on a healthy tree. So the test now builds a **probe element per
selector** — one node per compound, chained by parent, so `select text-input` is
a `text-input` inside a `select` — and resolves it through the real resolver.
The leftmost probe has no parent, which is what makes it `:root` and is how the
theme's custom properties reach the chain.

**And `ComputedStyle.forgetReportedDrops()` is public.** A drop is reported once
per JVM, so that one typo cannot report itself sixty times a second; a lint in
another module that did not clear it would pass by reading an empty log.

## Alternatives considered

- **Rewriting the stylesheet to `border-radius: 7px`.** Cheapest, and wrong in a
  way a screenshot shows: the header's bottom corners would be notched away from
  the body underneath it, which is a hole where two surfaces meet.
- **Accepting the shorthand only when all four values agree.** It would still
  drop `7px 7px 0 0` — the actual declaration — while claiming to support the
  syntax. A parser that takes a form and then refuses the reason anyone writes it
  is worse than one that refuses the form.
- **Keeping `radius()` on `Decoration` as a derived accessor.** Nine test call
  sites would have kept compiling, and each would have been asserting something
  the name no longer answers: with four corners, "the radius" is a question with
  no correct return value. They now compare a `Corners`.
- **Elliptical corners, since the grammar was being widened anyway.** No caller
  wants one, `RoundRect`'s four cubics are quarter *circles*, and an unused curve
  is an untested one.
- **Raising the value drop from `warn` to `error`, or throwing.** The engine's
  leniency is deliberate and ADR-0215 settled it: an application's bad
  declaration must not stop a window opening. What was missing is a test over the
  toolkit's own sheets, which is what this adds.

## Consequences

- **`Decoration.radius` is now `Decoration.corners`**, a `Corners`.
  `decoration.radius(8)` still exists and sets all four, which is what every
  design-system radius wants; `decoration.corners(…)` is the new one. Nine
  assertions across `:widgets` and `:core` compare a `Corners` instead of a
  `double`.
- **Two golden images changed**, `group-box-dark` and `gallery-panels`, by the
  31 pixels of two corners each. Every other golden is byte-identical, which is
  what says the shared drawing is shared.
- **The `select`'s inner field is transparent now** and no pixel moved, because
  `select` and `text-input` are both `--gb-surface-sunken` wells: the fill that
  was wrong was the same colour as the fill behind it. It stops being invisible
  the first time either token changes.
- **ADR-0097's parked question can be reopened.** `SegmentedTest` says of the
  bar and its inset grid: "the day a per-corner radius exists this is the rule
  that should be revisited". That day is today. Not revisited here — the inset
  works and a control that draws correctly is not a bug — but the note is no
  longer waiting on a mechanism.
- **The lint got stricter and slower**: it runs the cascade per selector rather
  than `ComputedStyle` per rule. It found nothing beyond these two, on either
  half.
- **An application still gets silence**, exactly as before. What warns is a test
  over the toolkit's sheets; an author writing `border-radius: 50%` in their own
  stylesheet gets a warning line and a declaration that does nothing.
