# 447. A spinner has a size, because a ring has a stroke

Date: 2026-09-20

## Status

Accepted.

## Context

`docs/core-widgets.md` §3 calls `spinner` "a small indeterminate activity
indicator", and the toolkit took the adjective literally: one size, 16px, from
`controls.css`, with a 2px stroke written into the widget as a constant.

[ADR-0445] gave it a second job. A `web-view` holds its page out of sight until
it has loaded and draws a spinner in the box the page will occupy — a box that
is a whole tab. A 16px ring in the middle of it reads as a decoration on
something rather than as the subject, and there was no way to ask for a bigger
one.

The obvious answer is a stylesheet rule, and it is not enough. A spinner is a
**ring**: `Box.Mark(ARC, colour, thickness)`, where the thickness is a number
the painter is given. §8's CSS subset has no property for the weight of a mark,
so a 32px spinner styled only by width and height is drawn with a 16px
spinner's 2px stroke — a thin hoop. Nothing in a stylesheet could have fixed it.

## Decision

**`spinner` takes a `size` of `small`, `medium` or `large`**, as a value the
widget reads and not only a class.

That is the line
[ADR-0087](0087-a-semantic-fill-brings-its-own-foreground.md)'s `badge` and `message` draw
between them, and this falls on `message`'s side of it. A variant that is *only
a skin* should be a class rather than a second vocabulary only Java can write,
which is why a badge's variants are classes. A size is not only a skin: it
decides something the stylesheet cannot say.

**The diameter stays the stylesheet's.** The size puts a class on the node,
`controls.css` gives that class a width and a height, and the widget reads the
width the cascade actually resolved:

```java
var diameter = style.width() instanceof Length.Points points && points.value() > 0
        ? points.value()
        : size.diameter();
```

So `#busy { width: 48px }` gets a stroke weighted for 48px, and the two numbers
cannot drift. The diameter on the enum is a **fallback** for a width that is not
a length a ring can be drawn from — `auto`, or a percentage of a parent the
widget knows nothing about.

**One ratio for every size**, and it is exactly `2 / 16`: today's stroke at
today's diameter. A large spinner is a big picture of a small one rather than a
different shape, and `medium` renders *identically* to every spinner drawn
before this existed — which is why no golden image moved.

`web-view` asks for `large`.

## Alternatives considered

**A numeric size** — `spinner size=32`. More flexible, and it invents a scale
the design system does not have: §1.3 works in named tokens, and a control that
accepted 17px would be the only one that did.

**CSS only**, with the thickness derived from the computed width and no widget
change at all. This is half of what was built and is the half that works; what
it does not give is a size a **document** can write, and §9 asks every widget to
have a Java form, a KDL form and a CSS form. A `spinner` that could only be
resized from a stylesheet would be the one control whose size is not in its
markup.

**A `dense`/`compact` pairing**, reusing `Density`. That is a property of a
*screen* rather than of one control, and it already means something else: a
compact density is the whole window tightening up, not one indicator being
asked to stand for a bigger region.

## Consequences

**Nothing that existed changed.** `medium` is the default, its class is the only
one with no rule, and the ratio makes its stroke the same number it always was.
Every `new Spinner(...)` already written still compiles and still draws what it
drew.

**`spinner` gains a row in §3** and two rules in `controls.css`.

**The stroke now depends on the cascade**, which is a new coupling: a stylesheet
that sets a spinner's width to a percentage gets the size's nominal stroke
rather than one matching what it is finally laid out at. Reading the *laid-out*
box instead would mean the mark could not be decided until after layout, and a
render pass that needs its own output is not a thing this toolkit has.

**Three sizes and no fourth.** An application that wants 48px writes the width
and gets a stroke to match, which is the escape hatch — but it does so without a
name, and a fourth named size is a decision for whoever needs it.

[ADR-0445]: 0445-a-page-is-not-shown-before-it-can-be-seen.md
