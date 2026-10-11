# The tolerance's stray pixels (ADR-0630)

## `book/src/guide/testing.md`, `### Comparing with a tolerance`, first paragraph

Replace "The tolerance absorbs antialiased edges and nothing else: a colour that changed or a box in the wrong place moves thousands of pixels by tens of levels." with:

> The tolerance absorbs antialiased edges, and one pixel in 100,000 beyond
> them, where another CPU outlines a stroke a hair apart. A colour that changed
> or a box in the wrong place moves thousands of pixels by tens of levels.
