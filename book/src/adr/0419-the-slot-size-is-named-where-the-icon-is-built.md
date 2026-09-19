# 419. The slot size is named where the icon is built

Date: 2026-09-19

## Status

Accepted. Closes the `TODO` entry left by
[ADR-0143](0143-a-strip-keeps-its-height-and-an-icon-its-centre.md) — "nothing
says so at the door" — and is the first record to apply
[ADR-0394](0394-a-diagnostic-that-fires-on-everything-says-nothing.md)'s rule to a
diagnostic written after it.

## Context

An `Icon` is a path built at a size. `Icon.of` multiplies Lucide's 24×24
coordinates by `size / 24` at parse time and scales the stroke with them, so there
is no transform at draw time and no way to change the answer afterwards
([ADR-0043](0043-icons-are-stroked-paths.md)): "drawing the same symbol at two
sizes is two `Icon`s". ADR-0143 then decided that an icon larger than the box it
is given is **centred** in it, which turned a misalignment into a mere overhang.
Its own consequence is the entry this record closes:

> The toolkit cannot resize an `Icon`, so an application that wants its menu icons
> to fit the column builds them at 16 — which is what §3 sizes a glyph at, and
> what the showcase should have been doing.

The interesting part is not the overflow. It is that **there is exactly one slot
in the catalog this can happen in.** Surveying every widget that places an icon —
button, chip, tab, crumb, menu heading, timeline marker, segmented option, link
marker, image error glyph — every one of them does

```java
content.add(Box.icon(icon, style.color()));
```

with no `.style(style)` on that box. `Box.icon` sizes the box *to* the glyph, so
there is no slot and 20 or 24 is ordinary. Only `ItemLead` applies the style after
the icon:

```java
return Box.icon(icon, style.color()).style(style);
```

and `Box.style` assigns `style.width()` and `style.height()` wholesale, so
`item-lead { width: 16px; height: 16px }` wins and the glyph overhangs a box it
did not size. (The comment on that line claimed the override went the other way.
It did not, and that is a small part of why an oversized glyph looked like
nobody's decision rather than a wrong number.)

So the entry reduces to: the number 16 was written in `controls.css`, and the
person who has to choose it is writing Java and has no reason to open a
stylesheet. `Icons` — the registry an application hands its icons to — knew
nothing about sizes at all; its only diagnostic was for an unregistered *name*.

### Why the obvious diagnostic is the one ADR-0394 forbids

The reflex is a warning when a glyph is bigger than its slot. ADR-0394 spent a
whole record on why that class of thing is worthless, and this case fails its
sharpest test in advance: **the showcase builds its menu icons at 20**, in a 16px
column, on purpose, and `menu-icon-oversized.png` is a golden that pins that
drawing as *correct*. A `WARN` would therefore fire on the toolkit's own demo,
on every row of every menu, once per paint, and claim a defect where ADR-0143
recorded a deliberate decision. "A `WARN` claims something is broken; nothing is."

Nor can the check live at the factory. `Icon.bundled("palette", 20)` is
unimpeachable — it is what a `button` wants — and the factory has no idea which
slot, if any, the icon is headed for.

## Decision

Three things, and the diagnostic is the smallest of them.

**1. `Icons.SLOT` names the number, in the class an application already goes
through.** Sixteen, documented as what the catalog's *fixed* slots are and
explicitly not as a maximum, because a button's icon sizes the button's own box
and 20 and 24 are right there.

**2. `Icons.bind(String)` makes the correct call the shortest one.**
`icons.bind("folder")` registers `Icon.bundled("folder", Icons.SLOT)`. This is the
half that actually closes the entry: a constant nobody finds is a stylesheet with
extra steps, and the only thing that reliably competes with the shortest call is a
shorter correct one. `Icons.resolve`'s unknown-name message points at it too.

**3. `ItemLead` says so at `debug`, once per `(name, size, column)` triple.** The
repo's established dedup shape — a `ConcurrentHashMap.newKeySet()`, a
`REPORT_LIMIT` of 256 past which everything goes through rather than falling
silent, and `reportedOverhang()` / `forgetReportedOverhang()` so a test can assert
*once* rather than merely *at all*, there being no appender on the classpath. The
message names the icon, both numbers, ADR-0043's reason, and the fix.

It fires **only against an explicit `Length.Points` width**. A percentage or an
`auto` column has no number to be bigger than, and inventing one is how a
diagnostic starts reporting arithmetic. There is no tolerance beyond an epsilon
for the `double`: unlike ADR-0394's overflow watch, which was summing insets and
found 92% of its reports inside two pixels, these are two numbers a human typed.

### The count, because ADR-0394 asks for it

Every `Icon` that reaches a menu `Item` in this repository: `MenuGoldenTest` builds
two at 16 and one at 20; `ItemAlignmentTest` builds one at 20; the showcase's
`AppMenu` uses the 20 from `Showcase.ICON_SIZE`. Everything else that calls
`Icon.bundled` at 20, 24 or 48 goes to a button, a tray raster, or a test of the
path scaler, and never reaches a slot.

So across `:widgets:test` this diagnostic produces **one** report —
`palette/20.0/16.0` — reached from two test classes, plus the same one triple in
`:example:test`. Not 688 shading into arithmetic: one, and it is exactly the case
ADR-0143 named and the showcase never fixed. A distribution of one is not a
distribution, which is itself the argument that the check is measuring what it
meant to.

## Consequences

**The showcase still reports.** Deliberately, and this is the test that says the
diagnostic is not tuned to be quiet. ADR-0143 decided the drawing is acceptable
and a golden pins it; the showcase is not changed here, because changing it would
delete the one true positive and leave a check that fires on nothing.

**`debug` means it is off by default, and that is the cost.** Nobody is told
unless they go looking. ADR-0394 took the same trade for the nested-viewport
notice and stated it the same way: the failure mode is mild — a centred, slightly
large glyph — and the false positive would have been constant. What is bought is
that when somebody *does* ask "why is this icon large", the answer is one log line
away instead of a bisect through `controls.css`.

**`Icons.SLOT` and `controls.css` can drift, so a test pins them together.**
`ItemLeadOverhangTest` renders a menu the way `Menus` builds one and asserts the
`item-lead` box is `Length.points(Icons.SLOT)` square. If somebody widens the
column and leaves the constant, the door starts handing out the wrong number and
this is what says so.

**This does nothing for the other nine slots, because there is nothing to do.**
They size to their icon. If one ever gains a fixed width in a stylesheet it will
overflow silently, and the check will have to move somewhere both it and
`item-lead` can see — most likely `Box.icon`, which is where `BoxInk.of` already
computes the overhang for culling and says nothing about it.

**`ItemLead.render` does one comparison per iconed row per paint.** A `Length`
pattern match against a `double`, and the set is touched only when it already
overflowed. The rows that do not overflow — which is the intended world — pay one
branch.

**A stale comment was corrected on the way.** `ItemLead` claimed `Box.icon`'s size
overrode the column's. It is the other way round, it has been since the line was
written, and it only ever looked harmless because both numbers were 16.
