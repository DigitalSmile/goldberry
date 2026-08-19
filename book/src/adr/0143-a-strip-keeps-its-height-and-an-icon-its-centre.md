# 143. A strip keeps its height, and an icon its centre

Date: 2026-08-19

## Status

Accepted. Two drawing defects with one shape, both reported by looking at the
running window rather than at a test.

## Context

**A tab strip took its height from the tallest thing in it.** That is a `tab`
while there are tabs, and the `+` button when the last one is closed — 24 rather
than 32 — so closing every tab shrank the header row and left the `+` sitting at
the top of a row that was no longer as tall as it. The same rule was quietly
wrong with tabs *in* it: the showcase's strip measured 30 where its tabs are 32,
which is a row two pixels shorter than its own contents.

**A menu icon was drawn at the corner of its column.** The leading slot is 16
square, because it has to be one width whether it holds a tick or an icon
([ADR-0113](0113-a-submenu-is-placed-beside-its-menu.md)) — and an `Icon` is
rasterized at whatever size the application built it, which cannot be changed
after the fact ([ADR-0043](0043-icons-are-stroked-paths.md)). The painter drew it
at the box's origin, with a comment arguing that a stylesheet which resized the
box should not make the icon "drift to a centre nobody asked for". The showcase
builds its menu icon at 20, so the glyph hung four pixels above and left of the
tick it lines up with, and that row read as the odd one out.

## Decision

**`tab-list` has a height of its own** — `var(--gb-control-height)` — rather than
taking one from its content. A header row is a row of headers: it is one control
tall by definition, and the number of tabs in it is not what decides that.

**An icon is centred in its box.** In the common case the box *is* the icon,
because `Box.icon` sizes it — so the offset is zero and nothing changes. Where a
stylesheet said otherwise, centring is what a slot means. The old comment had it
backwards: a glyph parked in the corner of a slot is not "staying put", it is the
report "the row with the icon looks wrong".

## Consequences

**The gallery's `controls` goldens moved by two pixels**, and that is the tab
strip being the right height rather than a regression. Everything below the
header row shifted down with it.

**A menu icon larger than its column still overflows it**, symmetrically now
rather than into the label. The toolkit cannot resize an `Icon`, so an
application that wants its menu icons to fit the column builds them at 16 —
which is what §3 sizes a glyph at, and what the showcase should have been doing.
Centring is what makes the wrong size look merely large instead of misaligned.

**Both are pinned by pictures**, because both are facts about where something is
drawn and neither changes a number an assertion can reach.
`menu-icon-oversized.png` is built at 20 on purpose: the other menu images build
16, which is exactly why they never showed this.
