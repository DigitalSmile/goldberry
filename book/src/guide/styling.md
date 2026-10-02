# Styling

<p class="gb-lede">A small CSS: four fixed layers, a closed set of selectors and properties, custom properties for everything a theme decides, and transitions that cannot run layout.</p>

By the end of this chapter you can load an application stylesheet, select a
widget and its parts, know which properties resolve and which are refused,
swap a theme and a density at run time, and animate the properties the toolkit
lets you animate.

```css
#root   { flex-direction: column; background: var(--gb-bg); color: var(--gb-text) }
#bar    { height: 44px; padding: 0 16px; gap: 12px; align-items: center }
.screen { flex-direction: column; gap: 16px; padding-top: 16px }

button.danger:hover { background: var(--gb-button-danger-bg-hover) }
```

```java
private final Stylesheet styles =
        Stylesheet.resource(CascadeLayer.APPLICATION, Hello.class, "app.css");

@Override public List<Stylesheet> stylesheets() {
    var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
    sheets.add(styles);
    return sheets;
}
```

`Stylesheet.resource` reads a file beside the class and parses it at start-up.
A malformed stylesheet is a `CssSyntaxException` with a position, because a
window that opens unthemed and says nothing is worse than one that refuses to
open. `Stylesheet.parse(layer, text)` does the same for a string.

### Strict and lenient sheets

A sheet that asks for something the subset has not got is a different case
from a malformed one, and what happens depends on whose sheet it is.

| Sheet | Parsed | A selector, pseudo-class or at-rule outside the subset |
|---|---|---|
| the toolkit's own (`controls.css`, the themes, the content sheets) | strict | refuses the sheet with a `CssSyntaxException` |
| an application's (`Stylesheet.resource` or `Stylesheet.parse` in any other layer) | lenient | drops **that rule**, with one warning naming the selector, the sheet and the line |

```text
WARN  app.css, line 42: dropping "lane::before": pseudo-elements (::before, ::after) are not in this subset
```

A lenient sheet keeps what it dropped in `Stylesheet.dropped()`, and
`StyleLint` reports each one as a `DROPPED_RULE` finding. A property the
engine has not got at all, such as `letter-spacing`, is warned about once per
property per sheet when the sheet is read, with the first line it appears on.
A malformed sheet (an unclosed block, a declaration with no value) is refused
in both modes, which is what lets [hot reload](markup.md#hot-reload) keep the
last good sheet while you type. The mode can be chosen explicitly with
`Stylesheet.parse(layer, text, ParseMode.STRICT)`
([ADR-0529](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0529-an-applications-stylesheet-is-lenient-and-loud.md)).
Run with `-Dgoldberry.css.lint=true` while developing and the launcher lints
your sheets each time it reads them ([Logging](logging.md#properties-an-application-can-set)).

## The cascade: four layers

```java
public enum CascadeLayer { TOOLKIT_BASE, THEME, APPLICATION, INLINE }
```

| Layer | Holds | Who writes it |
|---|---|---|
| `TOOLKIT_BASE` | `controls.css`: every control's metrics and structure, reading colours through `var(--gb-*)` | the toolkit, `Controls.baseStylesheet()` |
| `THEME` | `nord-light.css` or `nord-dark.css`: the custom properties, plus the density and scrollbar sheets | the toolkit, `Theme.NORD_DARK.load()` |
| `APPLICATION` | your stylesheets | you |
| `INLINE` | what only a widget can compute, through `Styled.restyle` | a widget author |

At equal specificity a later layer wins, so an application rule beats a theme
rule beats a base rule. Specificity is CSS's: an id beats a class beats a
type. There is no `@layer`, and the order cannot be argued with by a
stylesheet that loads late.

`INLINE` has no `style=` attribute behind it. It is the layer a widget writes
into for a value no selector can express, such as the position of the third
of five segments
([ADR-0099](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0099-an-indicator-travels-on-a-grid.md)).

## Selectors

```css
button                    { }       /* type */
.primary                  { }       /* class */
#save                     { }       /* id */
panel text                { }       /* descendant */
row > button              { }       /* child */
checkbox:hover check-indicator { }  /* a part, under a state */
:root                     { --gb-accent: #b48ead }
list-row:nth-child(even)  { }       /* where it sits among its siblings */
.steps > :last-child      { }
```

The pseudo-classes are a closed set. A typo such as `:hovered` is refused in
a strict sheet and dropped with a warning in a lenient one, rather than being
a rule that never matches.

| Pseudo-class | True when | Set by |
|---|---|---|
| `:hover` | the pointer is over the node or a descendant | the router |
| `:active` | a press is held on the node or a descendant | the router |
| `:focus` | the node has keyboard focus, however it got it | the router |
| `:focus-visible` | the node has focus from the keyboard | the router |
| `:disabled` | the widget says it is disabled | the widget |
| `:checked` | the widget says it is on | the widget |
| `:indeterminate` | a tri-state control is mixed | the widget |
| `:invalid` | a field or its control failed validation | the widget |
| `:affixed` | an `affix` has pinned itself | the widget |
| `:root` | the root element, where a theme hangs its properties | structure |

The structural pseudo-classes say where a node sits among its parent's
children:

| Pseudo-class | True when |
|---|---|
| `:first-child`, `:last-child` | it is the first, or the last, of its parent's children |
| `:only-child` | it is its parent's only child |
| `:nth-child(An+B)` | it is at position An+B, counting from 1; `odd` and `even` work |
| `:nth-last-child(An+B)` | the same, counting from the last child |

The siblings are the children of the node's parent element, which is also
what `>` reads. A composition widget is an element with one child, like an
unstyled `<div>` around one node, so a card a stateless wrapper builds is the
only child of that wrapper. When a parent's children are added, removed or
reordered, only the children whose structural answer changed are restyled,
and a sheet with no structural pseudo-class restyles nothing
([ADR-0528](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0528-a-node-knows-its-place-among-its-siblings.md)).

`:hover` and `:active` reach the whole ancestor chain, which is what lets
`checkbox:active check-indicator` light the glyph when the label is pressed.
The router refuses to set `:hover` or `:active` on a disabled widget, which
is how a disabled control stays dull without `:not(:disabled):hover`.

Not in the subset: `:not()`, `:has()`, `:focus-within`, attribute selectors,
sibling combinators, and `::before` and `::after`. A part is selected by its
type name instead, and generated content is a widget. A widget that wants to
react to focus inside its subtree implements `Handles.onFocusWithin` rather
than matching a selector.

### Parts

A control with two surfaces a theme must style differently is two cascade
nodes. The inner one is a **part**: it has a type name, matches pseudo-classes
and takes properties, and it cannot be written in a document
([ADR-0065](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).

```css
checkbox                         { gap: 8px }
checkbox check-indicator         { border-radius: 4px }
checkbox:checked check-indicator { background: var(--gb-checkbox-bg-checked) }
toggle-track:checked toggle-thumb { transform: translate(16px) }
scroll:hover scroll-thumb.vertical { width: 10px }
```

`check-indicator`, `toggle-track`, `toggle-thumb`, `slider-value`,
`scroll-thumb`, `field-message` and `tab-panel` are parts. Each widget's
chapter lists its own under *Styling*.

## Properties

The subset is closed. A property the engine does not know is ignored, and the
sheet naming it warns once per property when it is read. A known property
with a value it cannot read is dropped with a warning that quotes the text.

### Box and layout

```css
.toolbar {
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: flex-start;
  align-items: center;
  gap: 8px;                 /* or gap: 4px 8px, row then column */
  padding: 8px 12px;
  margin: 0 auto;
  width: 100%;
  min-height: 32px;
  overflow: hidden;
}
.toolbar > button { flex: 1 0 120px; align-self: flex-end }
.badge { position: absolute; top: 4px; left: 4px }
```

`flex-direction`, `flex-wrap`, `flex`, `flex-grow`, `flex-shrink`,
`flex-basis`, `justify-content`, `align-items`, `align-self`,
`align-content`, `gap`, `row-gap`, `column-gap`,
`padding` and its four sides, `margin` and its four sides, `width`, `height`,
`min-width`, `max-width`, `min-height`, `max-height`, `position`, `inset`,
`top`, `right`, `bottom`, `left`, `overflow`. These compile to Yoga.
`flex` expands as CSS says: `flex: 1` is `1 1 0%`, `flex: auto` is
`1 1 auto`, `flex: none` is `0 0 auto`, and `flex: 120px` is `1 1 120px`.
`margin: auto` centres on the main axis
([ADR-0311](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0311-margin-is-room-outside-and-auto-is-the-half-that-mattered.md)).
Lengths are `px`, `%`, `em` and `rem`. There is no `calc()` and no
`display: none`.

### Text flow

```css
.cell    { white-space: nowrap; text-overflow: ellipsis; overflow: hidden }
.readout { text-align: end }
link     { text-decoration: underline }
.log     { white-space: pre-wrap; overflow-wrap: anywhere }
```

`white-space: normal | nowrap | pre | pre-wrap | pre-line`. A paragraph
never collapses spaces or newlines, so the five keywords are two behaviours:
`pre-wrap` and `pre-line` wrap as `normal` does, and `pre` stays on one line
as `nowrap` does. `pre-line` keeps runs of spaces CSS would fold into one.
`overflow-wrap: normal | anywhere | break-word` cuts a word wider than the
whole line between grapheme clusters; `word-break: normal | break-all` may cut
any word at the edge of the line. Both inherit
([ADR-0530](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0530-five-white-space-keywords-are-two-behaviours-and-a-long-word-may-be-cut.md)).
`text-overflow: clip | ellipsis`,
`text-align: start | center | end`, and `text-decoration` or
`text-decoration-line: none | underline | line-through`. `left` and `right`
are refused for `text-align`, and so is `justify`
([ADR-0256](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0256-a-line-is-placed-by-the-paint-not-by-the-box.md)).

### Typography

```css
.heading { font-family: Inter; font-size: 15px; font-weight: 600; line-height: 20px }
.mono    { font-family: "JetBrains Mono" }
.note    { font-style: italic }
```

`font-family`, `font-size`, `font-weight`, `font-style` and `line-height`.
A weight is a face, so `font-weight: bold` resolves to the nearer face that
exists, which is 600 ([ADR-0066](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0066-a-weight-is-a-face-and-color-inherits.md)).
`font-style: oblique` is refused because nothing shears a glyph
([ADR-0323](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0323-an-italic-is-a-face-and-the-matrix-closes.md)).
`font-family: Inter, sans-serif` takes the first name and discards the rest.
There is no `letter-spacing`.

### Colour

```css
panel  { background: var(--gb-surface); color: var(--gb-text) }
.faded { opacity: 0.5 }
.wash  { background-color: rgba(136, 192, 208, 0.2) }
```

`background`, `background-color`, `color` and `opacity`. A colour is a hex
value, `rgb()` or `rgba()`, `transparent`, or a `var()`. `opacity` on a node
with children renders the subtree into a layer and composites it once, so two
overlapping children do not show through each other
([ADR-0071](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0071-a-layer-is-a-subtrees-raster.md)). A translucent leaf
keeps the cheap path.

### Border, outline and shadow

```css
card   { border: 1px solid var(--gb-border); border-radius: 8px; box-shadow: var(--gb-elevation-1) }
.tab   { border: 1px solid var(--gb-border); border-bottom: none }
button:focus-visible { outline: 2px solid var(--gb-focus); outline-offset: 2px }
```

`border` and `border-top`, `-right`, `-bottom`, `-left`, each
`<width> || <style> || <color>` in any order. `border-width` and
`border-color` take one to four values. `border-{side}-width` and
`border-{side}-color`. `border-radius` takes one to four corners. `outline`,
`outline-width`, `outline-color` and `outline-offset`. `box-shadow` is one
shadow, not a list.

A border takes no layout room: it is drawn inside the box's edge, over the
padding ([ADR-0505](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0505-a-border-has-four-sides-and-takes-no-room.md)).
Every style keyword is drawn solid, and there are no `-style` longhands. An
outline is drawn outside the box and takes no room either, so a focus ring
cannot move a control by appearing.

### Transform

```css
panel.turned   { transform: rotate(20deg) }
panel.grown    { transform: scale(1.4); transform-origin: left top }
toast.entering { transform: translate(0, 16px) }
```

`transform` with `translate`, `scale` and `rotate`, and `transform-origin`.
Layout runs first and the matrix moves the result, so a transform costs no
layout pass. Hit testing maps the pointer back through the same matrix, so a
scaled button responds where it is drawn
([ADR-0068](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0068-the-transform-stack-is-java-side.md)).

### Transition and animation

```css
button        { transition: background-color var(--gb-motion-fast) ease-enter }
button:active { transition: background-color 0ms }

@starting-style { toast { opacity: 0; transform: translate(0, 16px) } }

@keyframes pulse { from { opacity: 0.4 } to { opacity: 1 } }
skeleton { animation: pulse 1.2s linear infinite }
```

`transition`, `animation` and its seven longhands: `animation-name`,
`-duration`, `-timing-function`, `-delay`, `-iteration-count`, `-direction`
and `-fill-mode`.

The animatable properties are a closed whitelist: `opacity`,
`background-color`, `border-color`, `color` and `transform`.
`transition: width 200ms` is a dropped declaration with a warning naming it,
because animating a width would run layout on every frame
([ADR-0067](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0067-motion-is-an-overlay-on-a-frame-clock.md)).

The timing that applies is the one on the style being moved **to**, so the
two `button` rules above make a press snap and a release fade. Easing is one
of three curves: `ease-enter`, `ease-exit` and `linear`. CSS's own keywords
are read onto them, with one info line naming each mapping:

| Written | Runs as |
|---|---|
| `ease`, `ease-out`, `ease-in-out` | `ease-enter` |
| `ease-in` | `ease-exit` |

A timing function outside that list (`cubic-bezier(…)`, `steps(…)`,
`step-start`) in `transition` or `animation` drops itself with a warning
naming it, and the rest of the declaration runs on the default `ease-enter`
([ADR-0527](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0527-css-easing-keywords-run-on-the-system-curves.md)).
Colours interpolate in OKLCH. Animated values
live in an overlay applied at paint and are never written back into the
computed style, so a transition retargeted halfway starts from where it is.
`transition` does not inherit.

`@starting-style` gives an element the style it transitions **from** on its
first frame ([ADR-0352](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0352-an-element-enters-from-its-starting-style.md)).
`@keyframes` names a sequence `animation` runs under the same whitelist
([ADR-0353](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0353-a-stylesheet-may-name-keyframes.md)). Under reduced
motion every transition collapses to zero and keyframe animations do not run.
The desktop's setting is read at start-up, and
`-Dgoldberry.motion.reduced=reduce` or `=full` overrides it.

A theme is still chosen by swapping a stylesheet; `@media` below is for a
sheet that adjusts itself to the window or the desktop.

### Media queries

```css
@media (max-width: 900px) { .sidebar { width: 200px } }
@media (prefers-color-scheme: dark) { :root { --brand: #88c0d0 } }
@media (prefers-reduced-motion: reduce) { .pulse { animation: none } }
@media screen and (min-width: 600px) and (max-width: 1200px) { }
```

| Feature | Asked of |
|---|---|
| `width`, `height`, as `min-`/`max-` or a range like `(width >= 600px)` | the window's logical size, in `px`, `em` or `rem` (16px) |
| `orientation: portrait \| landscape` | the same |
| `prefers-color-scheme: light \| dark` | the desktop's theme, light where it does not say |
| `prefers-reduced-motion: reduce \| no-preference` | the renderer's reduced-motion switch |

They combine with `and`, `or`, `not`, `only` and comma lists, and nest. The
media types `all` and `screen` hold and `print` does not. A query naming
anything else never holds and warns once; in a strict sheet it is refused.
`@keyframes` inside `@media` is not in the subset.

The cascade re-runs when an answer changes: on a resize that crosses a
breakpoint, when the desktop's theme changes, and when reduced motion is
switched. A resize that crosses none keeps every resolved style
([ADR-0526](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0526-a-media-query-is-asked-of-the-window.md)).

### Cursor

```css
button    { cursor: pointer }
.splitter { cursor: ew-resize }
canvas    { cursor: crosshair }
```

`default`, `pointer`, `text`, `move`, `wait`, `progress`, `crosshair`,
`not-allowed`, `ew-resize`, `ns-resize`, `nesw-resize`, `nwse-resize`,
`grab` and `grabbing`. The last two fall back to `move`, because no platform
has a system cursor for them. The cursor rides on the painted box and inherits
down the stack of rectangles under the pointer, not the element tree
([ADR-0057](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0057-the-cursor-rides-on-the-painted-box.md)).

## Custom properties and `var()`

```css
:root        { --gb-accent: #b48ead; --gb-scroll-line: 40px }
#revenue     { --gb-chart-1: #b48ead }
.hint        { color: var(--gb-text-placeholder, #4c566a) }
```

A custom property is declared on any node and inherits. `var(--name)` reads
it, with an optional fallback after a comma. Every colour a control draws
comes through a `--gb-*` token, so an application rule that redefines one on
`:root` restyles every control that reads it, and one that redefines it on
`#revenue` restyles one chart. The tokens that exist are listed in
[The design system](design-system.md).

## Inheritance

`color`, the font properties, `line-height`, `white-space`, `text-align`,
`text-decoration`, `overflow-wrap` and `word-break` pass down the element
tree. The layout properties,
`background`, `opacity`, `transform`, `transition` and the border do not
([ADR-0066](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0066-a-weight-is-a-face-and-color-inherits.md)).

`ComputedStyle.INITIAL` has black text on purpose, so a window with no
stylesheet looks like one. Set `color` on your root, as the showcase does with
`#root { color: var(--gb-text) }`. A control that says nothing still reads
right because `controls.css` sets `color` on it.

## Restyle versus repaint

A repaint redraws what changed. A restyle throws every resolved style away,
re-reads `Application.stylesheets()` and rebuilds the renderer. The second is
much more expensive, and almost nothing needs it: a theme and a density, and
very little else.

```java
@Bind(value = "app.theme", restyle = true) private String themeName = "dark";
```

A field declared `restyle = true` asks for a restyle when it is assigned, and
the toolkit calls `Host.restyle()` for you
([ADR-0133](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0133-a-restyle-is-declared.md)). Everything else that
changes a value asks for a repaint by itself
([ADR-0128](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0128-a-change-is-its-own-frame-request.md)). An
application outside the model system calls `host.restyle()` directly.

A restyle is not a full recompute of every node. Each element caches its
resolved style and re-resolves only when the resolver changed, when the style
it inherits changed, or when a pseudo-class or a rebuild invalidated its
subtree ([ADR-0070](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0070-the-cascade-resolves-invalidated-nodes.md)).

## Themes, density and scrollbars

```java
@Override public List<Stylesheet> stylesheets() {
    var sheets = new ArrayList<>(Controls.stylesheets(
            settings.theme(),            // Theme.NORD_LIGHT or Theme.NORD_DARK
            settings.density(),          // Density.REGULAR or Density.COMPACT
            settings.scrollbars()
    ));     // Scrollbars.OVERLAY or Scrollbars.ALWAYS
    sheets.add(styles);
    return sheets;
}
```

`Controls.stylesheets(theme)`, `(theme, density)` and
`(theme, density, scrollbars)` return the base sheet, the theme, and whatever
the other two add, in cascade order. `Theme.NORD_LIGHT` and `Theme.NORD_DARK`
are the two themes that ship, and a theme is a custom-property layer: the
base rules never name a colour.

`Density.COMPACT` is a handful of tokens on `:root`, and every control is 28 tall
instead of 32 with nothing in your tree mentioning a height.
`Density.REGULAR` ships no stylesheet at all, because regular is what the
toolkit already is
([ADR-0074](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)).
`Scrollbars.ALWAYS` reserves a 12 px gutter beside scrolling content
([ADR-0364](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0364-always-shown-scroll-bars-are-a-token-sheet.md)).

### Following the desktop

```java
@Override public void start(Host host) {
    host.systemTheme().ifPresent(this::follow);
    host.onSystemThemeChanged(this::follow);
}

private void follow(SystemTheme theme) {
    actions.pickTheme(theme == SystemTheme.DARK ? "dark" : "light");   // a restyle = true field
}
```

`host.systemTheme()` answers `LIGHT`, `DARK`, or empty where the desktop has
no such setting, and empty is a different answer from light
([ADR-0322](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0322-the-desktop-says-light-or-dark-or-says-nothing.md)).
The toolkit chooses nothing with the answer. A Linux build without the D-Bus
headers cannot ask at all, and says so through
`Goldberry.capabilities().contains(Capability.SYSTEM_THEME)`
([ADR-0325](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0325-a-build-says-what-it-can-ask-the-desktop.md)).

### Text scale

The renderer scales text between 90% and 150% without scaling the boxes, which
is the condition every control is built to survive
([ADR-0267](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0267-a-text-scale-scales-the-text-and-not-the-layout.md)).
The switch is `WidgetRenderer.textScale(double)`, on the renderer an
application builds itself. The launcher does not expose it yet.

## Read more

- [ADR-0066](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0066-a-weight-is-a-face-and-color-inherits.md): inheritance, and a weight is a face
- [ADR-0067](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0067-motion-is-an-overlay-on-a-frame-clock.md): transitions
- [ADR-0068](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0068-the-transform-stack-is-java-side.md): transforms
- [ADR-0071](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0071-a-layer-is-a-subtrees-raster.md): opacity and layers
- [ADR-0074](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md): density
- [ADR-0133](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0133-a-restyle-is-declared.md): a restyle is declared
- [ADR-0310](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0310-a-shadow-is-a-stack-of-rectangles.md): shadows
- [ADR-0505](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0505-a-border-has-four-sides-and-takes-no-room.md): borders
- [Sizing with CSS](../layout/sizing.md): the layout half, in the Layout part
