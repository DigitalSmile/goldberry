<!-- Pieces for book/src/guide/styling.md, each with its destination above it.
     Written 2026-10-10 and 2026-10-11 with CSS-00 to CSS-49 (all of
     Phase 3 but the two refusals) of
     docs/css-plan.md
     (the parts done so far) and the support analysis's finding 2. Later CSS
     batches add their pieces to this file. -->

<!-- 1. Destination: `### Strict and lenient sheets`, after the ```text block
     with the WARN line, before "A lenient sheet keeps what it dropped". -->

A rule nested inside another that asks for something outside the subset,
`a { &::before { … } }` say, is dropped by itself with a warning, and the rest of
the rule holding it is read.

A strict sheet also refuses any timing function but the design system's three
curves, `ease-enter`, `ease-exit` and `linear`, so the toolkit's own controls
all move on them. A lenient sheet may write any curve CSS has; see
[Transition and animation](#transition-and-animation).

<!-- 2. Destination: `## Selectors`. In the pseudo-class table, a row after
     `:disabled`, and a row after `:focus-visible`. -->

| `:enabled` | the widget is not disabled | the widget |

| `:focus-within` | the node or something under it has keyboard focus | the router |

<!-- 3. Destination: `## Selectors`. Replaces the structural table, from
     "| Pseudo-class | True when |" to the `:nth-last-child(An+B)` row, with the
     table below. -->

| Pseudo-class | True when |
|---|---|
| `:first-child`, `:last-child` | it is the first, or the last, of its parent's children |
| `:only-child` | it is its parent's only child |
| `:nth-child(An+B)` | it is at position An+B, counting from 1; `odd` and `even` work |
| `:nth-last-child(An+B)` | the same, counting from the last child |
| `:nth-child(An+B of S)`, `:nth-last-child(An+B of S)` | it matches the selector list `S`, and is at position An+B among the siblings that match it |
| `:first-of-type`, `:last-of-type`, `:only-of-type`, `:nth-of-type(An+B)`, `:nth-last-of-type(An+B)` | the same five, counted among the siblings of its own type |

<!-- 4. Destination: `## Selectors`. Replaces the paragraph "Not in the
     subset: `:not()`, `:has()`, `:focus-within`, …" (to "rather than
     matching a selector.") with the text below. -->

`:is()`, `:where()` and `:not()` take a list of selectors, each of which may
have combinators of its own: `:is(button, toggle):hover`,
`list-row:not(.selected)`, `:is(card > header) text`. `:is()` matches when any
of them does and `:not()` when none does; each is as specific as the most
specific selector in its list. `:where()` is `:is()` with no specificity at
all, which makes it the way to write a default any rule can override. A
selector in the list that is outside the subset, `:is(a[href])`, drops the
rule it is in, where a browser would ignore that one entry.

A rule that counts siblings through `of S` restyles every child of a parent
that rebuilds, and the siblings of a child whose state changes, since which of
them match `S` may have moved. A sheet without one pays nothing.

`:focus-within` moves with the keyboard: a move between two controls in one
`field` leaves the field's answer alone, so a rule on it does not flicker.
`Handles.onFocusWithin` tells a widget the same thing as a notification.

Not in the subset: attribute selectors, and pseudo-elements other than
`::placeholder` and `::selection`. A widget's properties stay out of the
cascade, so a state a stylesheet should see is a class or a pseudo-class; a
part is selected by its type name, and generated content is a widget.

A rule may hold rules, as CSS's nesting writes them:

```css
card {
  padding: 12px;
  &:hover { background: var(--gb-surface-hover) }
  > header { font-weight: 600 }
  .compact & { padding: 6px }
  @media (max-width: 600px) { padding: 8px }
}
```

`&` is the rule it is inside. A nested selector without one is relative to it:
`header` means `& header` and `> header` means `& > header`. When the outer
rule has several selectors, `&` matches any of them, and is as specific as the
most specific, as `:is()` is. `@media` may sit inside a rule and holds
declarations for that rule's selector as well as rules of its own. Order is
kept: declarations written after a nested rule come after it in the cascade.
Every nested rule is read into an ordinary one when the sheet is parsed, so
nesting costs nothing once the sheet is loaded. `@media` is the only at-rule a
rule may hold.

Not in the subset: `:has()`,

<!-- 5. Destination: `### Box and layout`. Replaces the paragraph from
     "`flex-direction`, `flex-wrap`, `flex`," to "`display: none`." with the
     text below. -->

`flex-direction`, `flex-wrap`, `flex`, `flex-grow`, `flex-shrink`,
`flex-basis`, `justify-content`, `align-items`, `align-self`,
`align-content`, `gap`, `row-gap`, `column-gap`,
`padding` and its four sides, `margin` and its four sides, `width`, `height`,
`min-width`, `max-width`, `min-height`, `max-height`, `aspect-ratio`,
`position`, `inset`, `top`, `right`, `bottom`, `left`, `overflow` and
`display`. These compile to Yoga.
`flex` expands as CSS says: `flex: 1` is `1 1 0%`, `flex: auto` is
`1 1 auto`, `flex: none` is `0 0 auto`, and `flex: 120px` is `1 1 120px`.
`margin: auto` centres on the main axis.
`overflow` is `visible`, `hidden`, `scroll` or `auto`, and `auto` reads as
`scroll`: scroll bars are an application setting here, not a keyword.
`aspect-ratio` is `auto`, a number, or `width / height`, and sizes the axis the
box does not fix.

Every box is a flex box, so `display` has three values. `display: none` takes
the node and everything under it out: nothing is laid out, painted, hit or
reachable by the keyboard, and its elements stay mounted with their state, as a
background tab's do. `display: contents` takes the node's own box away and lays
its children out as its parent's; whatever the widget draws itself, its text
or its icon, goes with the box. `display: flex` is what every box is already.
`block`, `inline` and `grid` are dropped with a warning.

`visibility: hidden` leaves the box's own drawing out and keeps its room: it is
not painted, the pointer passes through it, and the keyboard skips it.
`collapse` is read as `hidden`. `pointer-events: none` lets the pointer through
to whatever is under the box, which still draws. Both inherit, and both are
per box: a child that says `visibility: visible` or `pointer-events: auto`
under a parent that said otherwise is drawn, or hit, as usual.
`user-select: none` keeps a press in a `markdown-view` or an `html-view` from
selecting, and `user-select: all` makes any click select the whole document;
`text` and `auto` are the default. `user-select` inherits too, and
`-webkit-user-select` is the same property.

`z-index` is `auto` or a whole number, and orders a box among its siblings: a
higher one is drawn later and found first by the pointer, and equal ones keep
document order. It applies to every box, positioned or not, since every box is
a flex item. It orders siblings and nothing else, so a box cannot rise above
its parent's siblings; something that must cover the whole window is an
overlay.

Lengths are `px`, `%`, `em`, `rem`, `ch`, `ex`, the absolute units `in`,
`cm`, `mm`, `Q`, `pt` and `pc`, at 96 pixels to the inch, and the viewport
units `vw`, `vh`, `vmin` and `vmax`. `ch` is the advance of the digit zero and
`ex` the x-height, both in the face the element is drawn in. A viewport unit is
a percentage of the window; a desktop window has one size, so `svh`, `lvh` and
`dvh` are all `vh`. A sheet that writes a viewport unit is restyled when the
window is resized, and one that writes none is not.

`calc()`, `min()`, `max()`, `clamp()`, `round()`, `mod()`, `rem()`, `abs()`
and `sign()` are read wherever a length, a number, an angle or a time is,
nested as deep as needed, with `e` and `pi` as numbers: `rotate(calc(45deg * 2))`,
`transition-duration: calc(var(--gb-motion-fast) * 2)` and
`translate(calc(-50% + 4px))` all work. Each is worked out when the style is
computed. A colour's components are not read this way, and
`rgb(calc(255 / 2) 0 0)` is dropped. One that
mixes a percentage with a length, like `calc(100% - 8px)`, is dropped with a
warning by `width`, `margin`, `padding`, `gap` and the rest of the layout
properties, because the layout engine sizes a box by a length or by a
percentage and never by both; give the parent padding instead, or a child
`flex: 1`. The same goes for `min()`, `max()` and `clamp()` over a percentage
and a length, which cannot be compared before the box is laid out.

<!-- 6. Destination: `### Typography`. Replaces the paragraph from
     "`font-family`, `font-size`, `font-weight`, `font-style` and
     `line-height`." to "There is no `letter-spacing`." with the text
     below. -->

`font-family`, `font-size`, `font-weight`, `font-style`, `line-height`, and the
`font` shorthand.
A weight is any number from 1 to 1000, or `normal` (400) or `bold` (700), and
it is drawn with the nearest face the family has by CSS's matching rule: over
Inter's 400 and 600, `bold` is the 600 and `500` is the 400. A family an
application ships at more weights gives each its own face.
`font-style: oblique` is refused because nothing shears a glyph.
`font-family: Inter, sans-serif` takes the first name and discards the rest.

A `font-size` is a length, a percentage of the parent's size, `smaller` or
`larger` (a fifth either way from the parent's), or one of CSS's keywords from
`xx-small` to `xxx-large`, stepped from the root's size, which is what `medium`
is. `line-height` is a length, a number times the size, or `normal`, which is
1.2 times the size.

`font: [style] [weight] <size>[/<line-height>] <family>` sets all five, and
puts back to their initial values the ones it does not name: the style and
the weight to `normal`, the line height to `normal`.

`letter-spacing` is added after every character and `word-spacing` to every
space, each `normal` or a length, which may be negative. Both inherit as the
pixels the parent computed, so `0.1em` on a heading stays the heading's tenth
in its labels. The text wraps, measures and takes the caret with the spacing it
is drawn with. A percentage is not read for either. `font` leaves both alone.

`text-transform` draws text in `uppercase`, `lowercase` or `capitalize`, and
inherits. It changes what is painted and never the text a widget holds, so a
field keeps its value, copies it as typed, and its caret lands where it
would. Every character is cased into exactly one character, which is what
keeps the caret true: `ß` stays `ß` in capitals.

`text-shadow` is a comma list of shadows under the text, the first on top,
each `<x> <y> [<blur>] [<color>]`; with no colour it is the text's own. It has
no spread and no `inset`, as in CSS, and inherits. A shadow with no blur is the
text drawn again at its offset; a blurred one is drawn once into a picture of
its own and kept, so a label that does not change pays for its blur once.

<!-- 7. Destination: `### Colour`. Replaces "A colour is a hex value, `rgb()`
     or `rgba()`, `transparent`, or a `var()`." with the text below. -->

A colour is a hex value (`#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`); `rgb()` or
`rgba()`, with commas or with spaces and a slash before the alpha
(`rgb(0 0 0 / 50%)`); `hsl()`, `hwb()`, `lab()`, `lch()`, `oklab()`, `oklch()`
or `color()` in `srgb`, `srgb-linear`, `display-p3` or `xyz`; one of the 148
named colours; `transparent`; `currentcolor`; or a `var()`. A colour outside
sRGB is clamped into it. `currentcolor` is the element's own `color`, so
`border: 1px solid currentcolor` draws the border in the text's colour; it is
fixed when the style is computed, so a `color` transition moves the text and
not the border. `opacity` is a number from 0 to 1 or a percentage.

<!-- 8. Destination: `### Border, outline and shadow`. Replaces the paragraph
     from "`border` and `border-top`, `-right`, `-bottom`, `-left`" to
     "`groove`, `ridge`, `inset` and `outset` are drawn solid, with one warning
     per style." with the text below. -->

`border` and `border-top`, `-right`, `-bottom`, `-left`, each
`<width> || <style> || <color>` in any order. `border-width`, `border-style` and
`border-color` take one to four values, and each side has its own
`-width`, `-style` and `-color`. `border-radius` takes one to four corners,
and after a `/` one to four vertical radii, which make each corner a quarter
ellipse; each corner also has its own longhand, `border-top-left-radius` and
the rest, which takes one radius or a horizontal and a vertical one. A radius
is a length, a percentage, or a `calc()` of both: a percentage is of the box's
width across and of its height down, so `border-radius: 50%` makes a circle
of a square box and an ellipse of any other. The inside of a border's corner
is the outside less each side's own width, so a corner between a thick side
and a thin one is an ellipse inside.
`outline`, `outline-width`, `outline-style`, `outline-color` and
`outline-offset`. An outline may be any style a border side may be, `auto`
being `solid`, and `outline-style: none` takes it away. `box-shadow`
is a comma list of shadows, the first on top, each
`[inset] <x> <y> [<blur>] [<spread>] <color>`.

A border takes room outside the padding, as piece 35 says. A shorthand that
names no style is `solid`, and the style `none` or
`hidden` is a side with no width.

- `dashed` is dashes three widths long with gaps the same, stretched so a side
  starts and ends on a dash. A square corner is a dash's end; a rounded corner
  is one dash shared by the two sides.
- `dotted` is round dots as wide as the side, two widths apart, with one on
  every corner.
- `double` is two lines a third of the width each, with the middle third empty.
- `inset` and `outset` are lit from the top left: an `inset` side is a darker
  tone of its colour on the top and the left and a lighter one on the bottom
  and the right, and `outset` the reverse. `groove` and `ridge` are two bands
  of half the width each, lit one way outside and the other way inside.

<!-- 9. Destination: `### Transform`. Replaces "`transform` with `translate`,
     `scale` and `rotate`, and `transform-origin`." with the text below. -->

`transform` with `translate`, `translateX`, `translateY`, `scale`, `scaleX`,
`scaleY`, `rotate`, `skew`, `skewX`, `skewY` and `matrix`, and
`transform-origin`.

<!-- 10. Destination: `### Transition and animation`. Replaces "`transition`,
     `animation` and its seven longhands:" through "and `-fill-mode`." with
     the text below. -->

`transition` and its four longhands, `transition-property`, `-duration`,
`-timing-function` and `-delay`; `animation` and its seven longhands:
`animation-name`, `-duration`, `-timing-function`, `-delay`,
`-iteration-count`, `-direction` and `-fill-mode`. The longhands are comma
lists that pair up by position with `transition-property`, the shorter lists
repeated, as CSS pairs them. `transition: all` names every animatable property,
and a later entry for one of them wins over it. `transition-property` starts
out empty rather than `all`, so a duration on its own animates nothing until a
property is named.

<!-- 11. Destination: `### Transition and animation`. Replaces the text from
     "Easing is one of three curves:" through "and the rest of the declaration
     runs on the default `ease-enter`." with the text below. -->

The design system's curves are `ease-enter`, `ease-exit` and `linear`, and the
toolkit's own sheets use nothing else. An application's sheet may write any
curve CSS has: `ease`, `ease-in`, `ease-out` and `ease-in-out` as CSS defines
them, `cubic-bezier()`, `steps()` with any of its positions, `step-start`,
`step-end` and `linear()` with its stops. A curve may overshoot, and an
animated `opacity` stays between 0 and 1 when it does. A timing function that
cannot be read, `cubic-bezier(2, 0, 0, 1)` or `steps(0)`, drops itself with a
warning naming it, and the rest of the declaration runs on the default
`ease-enter`.

<!-- 12. Destination: `### Cursor`. Replaces the paragraph from "`default`,
     `pointer`, `text`," to "`grab` and `grabbing`. The last two fall back to
     `move`, because no platform has a system cursor for them." with the text
     below. -->

Every keyword CSS has: `default`, `pointer`, `text`, `move`, `wait`,
`progress`, `crosshair`, `not-allowed`, the resizes (`ew-`, `ns-`, `nesw-`,
`nwse-`, `col-`, `row-`, and the eight edges and corners from `n-resize` to
`sw-resize`), `grab`, `grabbing`, `help`, `context-menu`, `cell`,
`vertical-text`, `alias`, `copy`, `no-drop`, `all-scroll`, `zoom-in`,
`zoom-out` and `none`, which hides the pointer over the box. Without a picture
from the application, a shape the platform has no cursor for falls back to the
nearest one it has: `grab` and `grabbing` to `move`, the edges and corners to
the two-headed arrows, `cell` and the zooms to the crosshair, and `help`,
`context-menu`, `alias` and `copy` to the arrow.

<!-- 13. Destination: `## Inheritance`, after its first paragraph. -->

Every property also takes `inherit`, `initial` and `unset`. `inherit` copies the
parent's value, even of a property that does not inherit: `padding: inherit`
copies the parent's four sides. `initial` is the property's initial value,
and `unset` is `inherit` for a property that inherits and `initial` for one
that does not. `all` takes the three and applies them to every property at
once. `revert` rolls a property back to what the toolkit's own sheets give it,
as if the theme and the application had said nothing about it, and
`revert-layer` to what the layers below the declaration's give it. With
nothing to roll back to, either is `unset`.

<!-- 14. Destination: `### Text flow`, after the paragraph that ends "and so is
     `justify`." -->

`line-clamp: <n> | none`, or `-webkit-line-clamp`, keeps the first `n` lines
of a paragraph and draws an ellipsis at the end of the last one when more were
cut. The box is measured at the lines it keeps. Unlike a browser, which also
wants `display: -webkit-box` and `overflow: hidden` beside it, the property
alone does it. It does not inherit.

<!-- 15. Destination: `### Media queries`, at its end. -->

`@supports` holds rules that apply when its condition does: `(property:
value)` asks whether this toolkit reads that property and would apply that
value, `selector(…)` whether it reads that selector, and `not`, `and` and `or`
combine them as CSS says. The answer is about the toolkit itself, so it is
worked out once, when the sheet is loaded: `@supports (display: grid)` is
false here, and a fallback written beside it is what applies. Anything else in
parentheses, `font-tech()` included, is false. `@supports` may hold `@media`
and may sit inside a rule.

<!-- 16. Destination: the top of the page, after the paragraph that starts
     "`Stylesheet.resource` reads a file beside the class", before
     `### Strict and lenient sheets`. -->

A sheet read with `Stylesheet.resource` may start with `@import`, which reads
another sheet beside it, as its `url()`s are: `@import "tokens.css";`, or with
a media list, `@import url(narrow.css) (max-width: 600px);`, whose rules then
apply under that condition. The imported rules come before the importing
sheet's own, so the importer wins a tie. An import that names a sheet that is
not there, or one already being imported, is dropped with a warning. A sheet
parsed from text has nowhere to look beside, so its imports are reported and
skipped. An `@import` after any other rule is ignored, as CSS ignores it.

<!-- 17. Destination: `## The cascade: four layers`. Replaces "There is no
     `@layer`, and the order cannot be argued with by a stylesheet that loads
     late." with the text below. -->

The four layers' order cannot be argued with by a stylesheet that loads late.
Inside `APPLICATION`, an application may order its own rules with `@layer`:
`@layer reset, vendor;` names layers in the order they rank, `@layer vendor {
… }` puts rules in one, and `@import url(vendor.css) layer(vendor);` puts a
whole sheet in one. A rule in no layer ranks above every layer, and a later
layer above an earlier one; `!important` turns that round. A layer ranks the
way the four layers do, after specificity: it decides between rules of equal
specificity, so a vendor's `.btn` loses to your `.btn` whichever loads last,
but a vendor's `.btn.primary` still beats your `.btn`, where CSS would let
yours win. `@layer` in a theme or in the toolkit's sheets ranks nothing.

<!-- 18. Destination: `### Media queries`. Replaces the feature table, from
     "| Feature | Asked of |" to the `prefers-reduced-motion` row, with the
     table and the paragraph below. -->

| Feature | Asked of |
|---|---|
| `width`, `height`, as `min-`/`max-` or a range like `(width >= 600px)` or `(400px <= width <= 800px)` | the window's logical size, in `px`, `em` or `rem` (16px) |
| `aspect-ratio`, as `min-`/`max-` or a range, `16/9` or one number | the window's width over its height |
| `orientation: portrait \| landscape` | the same |
| `resolution`, as `min-`/`max-` or a range, in `dppx`, `x`, `dpi` or `dpcm` | the window's scale |
| `pointer`, `any-pointer: none \| coarse \| fine`; `hover`, `any-hover: hover \| none` | a mouse, until the application says `renderer.input(MediaContext.Input.TOUCH)` |
| `prefers-color-scheme: light \| dark` | the desktop's theme, light where it does not say |
| `prefers-reduced-motion: reduce \| no-preference` | the renderer's reduced-motion switch |
| `prefers-contrast: no-preference \| more \| less \| custom` | the desktop's contrast switch; `custom` while it forces its colours |
| `forced-colors: none \| active` | Windows' high-contrast mode |
| `prefers-reduced-transparency: reduce \| no-preference` | macOS's reduce-transparency switch |
| `color-gamut: srgb \| p3 \| rec2020` | `srgb` holds: everything is drawn to an sRGB surface |

A feature written alone, `(hover)` or `(prefers-contrast)`, holds unless its
value would be the one that means none. The desktop's switches are read once,
when the application starts: the settings portal on Linux, the system
parameters on Windows, the workspace on macOS. One a desktop does not have
reads as off.


<!-- 19. Destination: `### Backgrounds and gradients`. Replaces the list from
     "A layer is `linear-gradient()`" to "Stops are placed as CSS places
     them.", and drops the last sentence of the paragraph after it, from
     "Transition hints" to "drop the declaration." -->

A layer is `linear-gradient()`, `radial-gradient()`, `conic-gradient()` or
their `repeating-` forms:

- A linear gradient runs `to` a side or a corner, or at an angle (`deg`,
  `rad`, `grad`, `turn`, with `0` up and turning clockwise). It runs to the
  bottom when it says nothing.
- A radial gradient is a `circle` or an `ellipse`, sized by
  `closest-side`, `closest-corner`, `farthest-side` or `farthest-corner`
  (the default) or by lengths, and centred `at` a position.
- A conic gradient turns clockwise round a centre, from the top or `from` an
  angle, centred `at` a position. Its stops are angles, or percentages of a
  turn. A repeating one repeats its first period round the turn.
- A stop is a colour with up to two positions, each a length or a
  percentage. Stops are placed as CSS places them. A position alone between
  two stops is a hint: where the two colours are half and half.
- `in` and a colour space, first in the list, blends the stops in that space
  rather than in sRGB: `srgb`, `srgb-linear`, `display-p3`, `lab`, `lch`,
  `oklab`, `oklch`, `xyz`, `xyz-d50`, `xyz-d65`, `hsl` or `hwb`. A space with a
  hue takes `shorter`, `longer`, `increasing` or `decreasing hue`.

```css
.ring  { background: conic-gradient(from -90deg, var(--gb-accent) 0 72%, var(--gb-border) 0) }
.dawn  { background: linear-gradient(in oklch to right, #f59e0b, #3b82f6) }
.glare { background: radial-gradient(white, 20%, transparent) }
```

<!-- 20. Destination: `### Backgrounds and gradients`. Replaces the paragraph
     after the list, from "Every layer is filled over the whole box" to "which
     is how the stripe above marches." (piece 19 drops its last sentence). -->

Each layer is placed in one of the box's rectangles and cut to another:
`background-origin` and `background-clip`, each `border-box`, `padding-box`
or `content-box`. The padding box is the box less its border and the content
box is the padding box less its padding, where its text starts. A layer starts inside the border and is drawn under it too; the
colour is cut to the last layer's clip, so `background-clip: padding-box`
keeps a fill out from under a translucent border. Every rectangle keeps the
box's rounded corners, tightened by the sides it is inside.

`background-position`, or `background-position-x` and `-y` alone, places
each layer with one to four words: lengths, percentages, `left`, `center`,
`right`, `top` and `bottom`, and a side with an offset from it, `right 8px
bottom 4px`. A percentage is of the room the layer leaves, so `100%` puts a
picture's far edge on the box's and moves a gradient, the size of its box,
not at all; only a length moves a gradient. `background-size` and
`background-repeat` size and tile a `url()` picture: `repeat`, `no-repeat`,
`repeat-x`, `repeat-y`, `space` (whole tiles, spread edge to edge) and
`round` (tiles stretched until a whole number fit), one for both axes or one
for each. Every one of these is a comma list matched to the layers, a short
list repeated. The `background` shorthand takes them all, the position and a
size as `<position> / <size>`, and one or two boxes: one is both the origin
and the clip, two are the origin and then the clip. It also takes `scroll`,
which is what a layer does anyway; there is no `background-attachment`
property and no `fixed` or `local`. `background-position` is animatable,
which is how the stripe above marches.

<!-- 21. Destination: `### Colour`, after piece 7's paragraph. -->

Two colours can be made from others. `color-mix()` mixes two in a colour
space, OKLab when it names none, as much of each as its percentage says:

```css
.primary:hover { background: color-mix(in oklch, var(--gb-accent), black 12%) }
.scrim         { background: color-mix(in srgb, var(--gb-surface) 60%, transparent) }
```

A colour with no percentage takes what the other leaves of 100%, and two that
add up to less than 100% leave the mix that much transparent. A space with a
hue, `oklch`, `lch`, `hsl` or `hwb`, may say `shorter`, `longer`, `increasing`
or `decreasing hue`.

A colour function may also start `from` another colour and name its channels:
`r g b` in `rgb()`, `h s l` in `hsl()`, `h w b` in `hwb()`, `l a b` in
`lab()` and `oklab()`, `l c h` in `lch()` and `oklch()`, `r g b` or `x y z` in
`color()`, and `alpha` in all of them. Each is the origin's value as a plain
number, so `calc()` can work on it:

```css
.soft  { background: oklch(from var(--gb-accent) calc(l + 0.15) c h) }
.ghost { border-color: rgb(from var(--gb-accent) r g b / 40%) }
```

Neither reads `currentcolor`.

<!-- 22. Destination: `### Transform`, after piece 9's sentence. -->

`translate`, `rotate` and `scale` are properties of their own as well:
`translate: 0 -2px`, `rotate: 90deg` (or `z 90deg`; no other axis), `scale:
0.96` or `scale: 1 0.5`, each `none` by default. They apply before
`transform`, translate first, then rotate, then scale, about the same origin,
and each is set and animated by itself, so a hover can lift a card that a
keyframe is turning. `transform-box` says what the origin and a percentage
are measured against: `border-box` (and `stroke-box` and `view-box`, a box
being no SVG shape) or `content-box` (and `fill-box`), inside the padding.

<!-- 23. Destination: `### Transition and animation`. Piece 10 says
     "`animation` and its seven longhands"; make that "eight", and add
     "`-fill-mode` and `-play-state`" in place of "and `-fill-mode`". Then
     replace "The animatable properties are a closed whitelist:" through
     "`color` and `transform`." with the text below. -->

The animatable properties are a closed whitelist: `opacity`,
`background-color`, `background-position`, `border-color`, `border-radius`,
`outline-color`, `outline-offset`, `box-shadow`, `color`, `transform`,
`translate`, `rotate` and `scale`. Every one of them is drawn, never laid out,
so a frame of motion never runs layout. `border-radius` moves corner by
corner; a `transform` transition leaves the three individual properties where
they are.

<!-- 24. Destination: `### Transition and animation`, after the paragraph
     that starts "`@starting-style` gives an element". -->

A keyframe may name its own `animation-timing-function`: the curve from that
keyframe to the next, in place of the animation's. A `@keyframes` block may
sit inside `@media`; the last block of a name whose condition holds is the one
that runs.
`animation-play-state: paused` stops an animation's clock where it is, and
`running` starts it again from there; a paused animation asks for no frames.

<!-- 25. Destination: `### Box and layout`, after piece 5's paragraph. -->

The logical properties are read as the physical ones they are with one
writing mode, horizontal and left to right: the inline axis runs across from
the left and the block axis down from the top. `margin-inline`,
`margin-block`, `padding-inline`, `padding-block`, `inset-inline` and
`inset-block` take a start and an end, one value being both;
`margin-inline-start`, `padding-block-end`, `inset-inline-start` and the
rest are one side each; `inline-size`, `block-size` and their `min-` and
`max-` forms are the width and the height.

`place-content` is `align-content` then `justify-content`, `place-items` is
`align-items` then `justify-items`, and `place-self` is `align-self` then
`justify-self`, one value being both. `justify-items` and `justify-self` are
read and change nothing, as CSS says for a flex item, which every box is; a
value CSS would refuse is still dropped. `justify-content: stretch` is the
start, as it is on any flex container.

`overflow` takes one value for both axes or two, across then down, and
`overflow-x` and `overflow-y` set one each: `visible`, `clip`, `hidden`,
`scroll` and `auto`. `clip` cuts the content off like `hidden` and nothing
can scroll it. A box is clipped on both axes when either axis is anything but
`visible`; CSS clips one axis only for `visible` beside `clip`.

```css
.chip    { padding-inline: 8px; margin-block: 2px 4px }
.centred { place-items: center }
.strip   { overflow: hidden auto }
```

<!-- 26. Destination: `### Strict and lenient sheets`. In the paragraph that
     starts "A lenient sheet keeps what it dropped", replace "such as
     `letter-spacing`" with "such as `float`": `letter-spacing` is read now.
     The Styling screen's probe card was changed to match. -->

<!-- 27. Destination: `## Selectors`. In the first ```css block, after the
     `row > button` line, two lines; then a paragraph after the one that
     starts "The siblings are the children of the node's parent element"
     (CSS-43). -->

```css
label + text-input        { }       /* the sibling just after */
heading ~ paragraph       { }       /* any sibling after */
field:has(> :invalid)     { }       /* what is below it */
```

`+` and `~` read the siblings before a node, the same children `>` and the
structural pseudo-classes count. `:has()` reads the other way: `field:has(>
:invalid)` is a field whose own child is invalid, `card:has(:checked)` a card
with a checked box anywhere inside it, and `heading:has(+ paragraph)` a
heading a paragraph follows. Its selectors may start with `>`, `+` or `~`,
and start below the node when they do not. A `:has()` inside another is
refused, as in CSS.

These are the selectors that make one node's style depend on another node.
Under a sheet that writes `+` or `~`, a change to a node restyles its
siblings; under one that writes `:has()`, a change restyles the nodes above
it (and, for `:has(+ …)` and `:has(~ …)`, the siblings before them) whose
type a `:has()` sits on. A state a widget owns, `:checked` say, is read before
the frame is styled, so `card:has(:checked)` changes in the frame the box is
checked. Sheets that write neither, which are the toolkit's own, pay nothing
for them.

<!-- 28. Destination: `### Parts`, after its first paragraph (CSS-44). -->

Two parts also answer to the pseudo-element a browser's sheet would write for
them. A field's value is `::placeholder` while it shows the placeholder, and
so is a `select`'s while nothing is chosen; a field's `text-selection` and a
document view's `selection-layer` are `::selection`:

```css
text-input::placeholder         { color: var(--gb-text-muted) }
text-input:focus::placeholder   { color: transparent }
::selection                     { background: var(--gb-accent-bg-hover) }
```

A state written before the `::` is the control's. `text-input::placeholder`
matches the part below the `text-input`, which is the same node
`text-input text-value.placeholder` selects; a pseudo-element ends its
selector, and one inside `:is()` or `:has()` is refused, as in CSS.
`::selection` takes `background`: the highlight is drawn behind the text, and
the text keeps its colour so it stays readable.

<!-- 29. Destination: `### Strict and lenient sheets`, the ```text block with
     the WARN line (CSS-44): the message now reads -->

```text
WARN  app.css, line 42: dropping "lane::before": pseudo-elements other than ::placeholder and ::selection (::before, ::after) are not in this subset
```

<!-- 30. Destination: `### Box and layout`, after piece 25 (CSS-48). -->

`order` places a box among its siblings: lower first, document order among
equals, 0 unless a rule says otherwise. Layout, painting and the pointer
follow it; the keyboard's Tab order stays the order the children are written
in.

`direction: rtl` runs a box right to left, and its children with it, since it
inherits. A row starts at the right, `margin-inline-start` and the other
logical properties name the right side, and `text-align: start` places a line
at the right; `left` and `right` stay the sides they name. It is also the base
direction of the box's text: which side a line starts at, and where the
punctuation and spaces between a right-to-left word and a left-to-right one
go. `writing-mode` is not in the subset.

```css
.toolbar.rtl { direction: rtl }
.toolbar .primary { order: -1 }   /* first, whatever the markup says */
```

<!-- 31. Destination: `### Text flow`, replacing what it says about `justify`
     being refused (CSS-49). -->

`text-align: justify` sets every line of a paragraph but its last flush with
both edges, sharing the room left on the line among its word gaps. A line a
newline ends is a paragraph's last too, and so is one with no gap; those sit
at the start. `text-justify: none` turns it off, and `auto` and `inter-word`
both mean the word gaps. A field being edited is never respaced, so its caret
stays on the glyphs: a `text-area` with `justify` keeps its lines at the
start.

<!-- 32. Destination: `### Typography`, after the paragraph on `font-weight`
     (CSS-49). -->

Upright Inter and JetBrains Mono are variable faces, and any weight a
stylesheet writes is drawn at that weight: `font-weight: 500` is a 500, and
`bold` a 700. The italics are separate files at 400 and 600 and draw the
nearest of the two. `font-stretch` (a keyword such as `condensed`, or a
percentage from 50% to 200%) sets the width of a face that has a width axis,
and `font-variation-settings` sets any axis a face has by its tag:

```css
.display { font-variation-settings: "opsz" 32 }
.tight   { font-stretch: 87.5% }
```

An axis a face does not have is ignored, and a value past an axis's range is
brought back inside it. Both inherit, and `font` resets them.

<!-- 33. Destination: `### Border, outline and shadow`, at its end (CSS-46). -->

`clip-path` keeps part of a box and drops the rest, its children included:
`inset()` with optional rounded corners, `circle()`, `ellipse()`,
`polygon()` and `path()`, in the border box or in the box named after the
shape (`padding-box`, `content-box`). A box name alone clips to that box. The
pointer finds the box only inside the shape.

```css
.avatar { clip-path: circle(50%) }
.ticket { clip-path: inset(0 12px round 6px) }
.flag   { clip-path: polygon(0 0, 100% 0, 85% 50%, 100% 100%, 0 100%) }
```

`mix-blend-mode` combines a box with what is drawn under it: `multiply`,
`screen`, `overlay`, `darken`, `lighten`, `color-dodge`, `color-burn`,
`hard-light`, `soft-light`, `difference`, `exclusion` and `plus-lighter`.
`isolation: isolate` keeps what blends inside a box from blending with what
is outside it. A clipped, blended or isolated box is drawn as a group, like a
box with `opacity`; `mask-image` and the `hue` family of blend modes are not
in the subset.

<!-- 34. Destination: `### Border, outline and shadow`, after piece 33 (CSS-40). -->

`filter` changes how a box and everything in it look, as one picture: `blur()`,
`brightness()`, `contrast()`, `grayscale()`, `hue-rotate()`, `invert()`,
`opacity()`, `saturate()`, `sepia()` and `drop-shadow()`, applied in the order
written. A `drop-shadow()` follows the shape of what is drawn, its rounded
corners and its transparent parts, where `box-shadow` follows the box. A
blurred or shadowed box draws past its edges by about three times the radius.
`backdrop-filter` is not in the subset.

```css
.disabled-card { filter: grayscale(1) opacity(0.6) }
.sticker       { filter: drop-shadow(0 2px 3px rgb(0 0 0 / 35%)) }
```

<!-- 35. Destination: `### Box and layout`, replacing what it says about a
     border being drawn over the padding and taking no room (CSS-47). -->

A border takes room, outside the padding, as in CSS: a box's content starts
inside its border and then its padding. `box-sizing` says what `width` and
`height` measure. Its initial value here is `border-box`, so a box given a
size keeps that size whatever border it has and its content gives way;
`content-box` adds the padding and the border to the size instead. A box
sized by its content grows by its border either way.

<!-- 36. Destination: `### Media queries`, at its end (CSS-41). -->

A box can be a container, which its descendants' rules can ask about the way
an `@media` rule asks about the window: `container-type: inline-size` answers
for its width, `size` for its width and height, and `container-name` gives it
names an `@container` can ask for. The size asked about is the container's
content box, inside its border and padding.

```css
.card { container-type: inline-size; container-name: card }
@container card (min-width: 360px) {
  .card-body { flex-direction: row }
}
.card-title { font-size: clamp(14px, 5cqw, 22px) }
```

A query takes the features and range forms a media query has for the
window's size: `width`, `height`, `inline-size`, `block-size`, `aspect-ratio`
and `orientation`. It asks the nearest container that answers for the axes it
reads, and does not hold before that container has been laid out. `cqw`,
`cqh`, `cqi`, `cqb`, `cqmin` and `cqmax` are a hundredth of the nearest
container's size, and of the window's where there is none. A frame whose
containers changed size is laid out a second time, never a third, so a
container whose size depends on its own queries settles over the next frame.

<!-- 37. Destination: `### Text flow`, at its end (CSS-50). -->

Text in a right-to-left script reads from the right wherever it is: a Hebrew
word in an English sentence, a number in an Arabic one, a whole field of
either. Each line is placed in reading order, and the caret, a click and a
selection follow what is on screen; a selection across a change of direction
is highlighted in a piece on each side.

A box's text takes its base direction from `direction`. `unicode-bidi` says
otherwise:

| Value | The base direction of the box's text |
|---|---|
| `normal` | The box's `direction`. The initial value; `embed` and `isolate` read the same, since a box's text is always a paragraph of its own |
| `plaintext` | Each paragraph's first letter with a direction, and the box's `direction` for a paragraph with none. `text-align: start` and `end` then follow each paragraph |
| `bidi-override` | The box's `direction`, for every character: right-to-left script is drawn left to right under `ltr`. `isolate-override` reads the same |

```css
.message { unicode-bidi: plaintext }   /* each message reads its own way */
.code    { unicode-bidi: bidi-override; direction: ltr }
```

`unicode-bidi` does not inherit. In a `text-input` or a `text-area` the
arrow keys move through the text in reading order: `Left` moves on in a
right-to-left paragraph and back in a left-to-right one.
