# A canvas names its painter from markup, and is cached by its stylesheet

Held back until the release (ADR-0645, ADR-0646). Three pieces; each says where
it goes.

## 1. `book/src/components/drawing.md`, `## `canvas``, the paragraph after the samples

Replace "Markup names no painter. A `canvas` node inflates to a styled, sized
surface that draws nothing, because a painter is code and a document names things
rather than building them. The painter is Java." with:

The painter is Java, so markup names one rather than writing it. `painter=` and
`input=` name objects the application bound in its `Named` registry, as
`canvas3d renderer=` does:

```java
var named = Named.strict().bind("app.revenue", (Painter) chart::paint);
```

A `canvas` that names neither is a styled, sized surface that draws nothing.

## 2. The same section, the attribute table

Add two rows after `class`:

| `painter` | name | none | A `Painter` or `StyledPainter` the application bound |
| `input` | name | none | An `Input` the application bound |

## 3. The same section, `### Styling`, after "A canvas has no size of its own, so one in a `row` with nothing to size it is zero wide."

`isolation: isolate` keeps what the painter drew: the canvas becomes a group with
a raster of its own, and while nothing about it changes later frames copy the
raster instead of calling the painter. An animating canvas is drawn on every
frame either way.
