# 328. A dot's colour is data

Date: 2026-09-16

## Status

Accepted. Closes `docs/gaps.md` G36.

## Context

`chip` draws `docs/ux-design.md` §10's pill: a rounded label with a pressed state
and, with `withDot(true)`, a leading 6-point dot. The dot takes its colour from
`background` on the `chip-dot` part, so `chip.danger chip-dot { background: … }`
is how a status hue is set — and that is the right mechanism for a *status*, which
is a closed set somebody can write rules for.

It is the wrong mechanism for a **Project**. A Project has a colour the way it has
a name and a due date: it is a row in a database, chosen by whoever made the
Project, and six of them in a menu are told apart by hue long before they are
read. A stylesheet cannot have a rule per Project, because the Projects do not
exist when the stylesheet is written.

So an application could store a colour per Project and not draw it. The pill said
*which* Project by name, the menu's rows were distinguished by name, and the hue
was a column with no pixel.

## Decision

**`withDot(int argb)` beside `withDot(boolean)`, and a `dotColor` component
carrying it.**

```java
public Chip withDot(boolean value);   // as before: the stylesheet's colour
public Chip withDot(int argb);        // this colour, and the dot shown
```

```kdl
chip dot-colour="#bf616a" "Goldberry"
```

### A colour, not a class name

Because the value is data. This is the same line ADR-0195 drew for chart series
colours and ADR-0251 for component metrics: the cascade is for things a rule can
name, and a widget that draws something the property set has no declaration for
still has to be able to take the answer from somewhere.

`int argb` and not an `Rgba`: `0xAARRGGBB` is the toolkit's own currency at the
paint boundary — `Box.background`, `frame.fillRect`, `ComputedStyle.color` — so
this keeps a colour *type* out of the widget API entirely.

`0` means "the stylesheet decides", which is the sentinel `Wiring.colour` already
uses for every other document-supplied colour in the catalog.

### The colour turns the dot on

`withDot(0xFFBF616A)` shows a dot. A chip carrying a dot colour and no dot is a
value saying two contradictory things, so the constructor refuses the pair rather
than picking one — the same refusal, and the same reasoning, as the existing "a
dot or an icon, not both".

`withDot(false)` clears the colour with it. Turning something off and leaving its
colour behind is state waiting to reappear.

### Markup takes both spellings

`Wiring.colour(node)` already accepted `colour=` and `color=`, because CSS spells
it one way and this repository's prose spells it the other. A widget with more
than one colour needs more than one *name*, so that method gains an overload
taking the pair of names, and `chip` uses `dot-colour`/`dot-color`. A colour alone
turns the dot on, which is what keeps the markup and the Java form building the
same value from the same number of attributes.

## Consequences

- `Chip` grows from nine components to ten. The canonical constructor has no
  callers outside the class, so the change is contained; every wither carries the
  new component and `ChipDotColourTest` asserts that they do.
- `ChipDot` gains a component and paints the colour over whatever the cascade
  resolved for its `background`. The stylesheet keeps everything else about the
  dot — its size, radius, margin and border — which is what makes this an override
  of one property rather than an opt-out of theming.
- The same argument will apply to a `chip` used for a Ticket's status dot, which
  is the second caller and is why this is a parameter rather than a widget in an
  application.
- A colour an application supplies is **not** contrast-checked. `ThemeAudit`
  reasons about tokens, and a hue from a database is not one. A 6-point dot beside
  a label is not carrying the meaning on its own — the label is — so this is a
  decoration rather than an accessibility hole.
