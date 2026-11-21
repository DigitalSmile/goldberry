<!-- 1. Destination: book/src/guide/styling.md, `### Border, outline and shadow`,
     after the `filter` paragraph and its example (piece 34 of
     `guide-styling-css-subset.md`), replacing that piece's pointer sentence
     "`backdrop-filter` is described in `guide-styling-backdrop-filter.md`
     piece 1." -->

`backdrop-filter` takes the same functions and runs them over what is drawn
**under** the box instead, inside its border and its rounded corners. The box
itself, its background, border and children, is drawn over the result as
usual, so a translucent background tints the frost:

```css
.toolbar {
  background: rgb(236 239 244 / 35%);
  border-radius: 12px;
  backdrop-filter: blur(24px) saturate(110%);
}
```

What is under the box is everything drawn before it in the window, or in the
group it is inside: a box with `opacity`, `filter`, `clip-path`,
`mix-blend-mode` or `isolation: isolate` keeps its frost to what is inside the
group, and so does a box with rounded corners that clips its children. The
frost follows the box's `opacity` and `transform`. At the window's edge the
edge pixels are repeated, so a bar along the edge is as frosted there as in
its middle. A blur reads past the box by about three times its radius, so a
change that close to a frosted box repaints the whole box.

A video or a 3D canvas is not drawn into the window's picture, so it cannot be
frosted. Over one, a frosted box shows its own background colour at full
strength, the opaque fallback a frosted surface is designed with. Without a
GPU the video's own fallback picture is what is on screen, and it is frosted.
A frosted box seen through another frosted box shows without its own frost.

Under `@media (prefers-reduced-transparency: reduce)`, give the box an opaque
background and `backdrop-filter: none`.

<!-- 2. Destination: book/src/guide/design-system.md, `**Materials.**`
     paragraph. Replaces "`frost` is not built: there is no `backdrop-filter`
     in the CSS subset and no blur in the rasterizer. A design that wants a
     frosted panel gets an opaque raised one." with the text below. -->

`frost` is `backdrop-filter: blur(24px) saturate(110%)` over the raised
surface at 78% (dark) or 72% (light). It falls back to the opaque raised
surface over a video or a 3D canvas on its own, and under reduced
transparency by a rule of the application's. No component wears it by
default: a popup is a window of its own, with nothing under it to frost.
