# CSS support: what goldberry has, and what it is missing

- **Date:** 2026-10-10
- **Against:** `master` at `a32882c9`, plus the GB-024 to GB-026 work already in
  the code (`url()` layers, `background-size`, `background-repeat`,
  `border-image`), whose guide text is parked in `docs/snapshot/styling-images.md`
- **Status:** analysis only. Nothing here is scheduled, and no code changed.
  The tracking table at the end is where an item moves when it is.

## How this was measured

The guide's *Styling* chapter is not the source of truth here. The code is, and
every claim below was checked against it in one of three ways:

1. **The property registry.** `ComputedStyle.with` is one `switch` over property
   names, and `ComputedStyle.isProperty` answers "known" for every arm in it. A
   name that reaches `default` is warned about once per sheet and does nothing.
2. **The parser's refusals.** `CssParser` names everything it refuses: at-rules,
   pseudo-elements, attribute selectors, sibling combinators, functional
   pseudo-classes and unknown pseudo-classes. `MediaQueries` names the media
   features it has.
3. **A probe.** About 150 declarations and sheets were parsed with
   `Stylesheet.parse(APPLICATION, …, LENIENT)` and `STRICT`, and each
   declaration was applied with `ComputedStyle.of` against a parent whose
   colour, size, background and padding all differ from the initial style. A
   declaration whose result equals the parent's plain inherited style was
   dropped. Values that would equal the initial style anyway (`box-shadow:
   none`, `justify-content: start`, `font-family: Inter`) were re-probed with
   values that differ.

Where this document says **refused**, a strict sheet throws and a lenient one
drops the rule with a warning. **Dropped** means the declaration is ignored
with a warning, and the rest of the rule applies. **Malformed** means the whole
sheet is refused in both modes.

## What there is

For orientation, so the gaps read against something. Everything here was
confirmed by the probe or by the source.

| Area | Supported |
|---|---|
| Cascade | Four fixed layers (`TOOLKIT_BASE`, `THEME`, `APPLICATION`, `INLINE`), specificity, `!important`, source order |
| Custom properties | `--name` on any node, inherited; `var(--name, fallback)`, nested |
| Selectors | type, `.class`, `#id`, `*`, descendant, `>`, comma lists |
| Pseudo-classes | `:hover` `:active` `:focus` `:focus-visible` `:disabled` `:checked` `:indeterminate` `:invalid` `:affixed` `:root`; `:first-child` `:last-child` `:only-child` `:nth-child(An+B)` `:nth-last-child(An+B)` |
| At-rules | `@media`, `@starting-style`, `@keyframes` |
| Media features | `width`, `height` (`min-`/`max-`/range syntax), `orientation`, `prefers-color-scheme`, `prefers-reduced-motion`; types `all`, `screen`, `print`; `and`/`or`/`not`/`only`, comma lists, nesting |
| Lengths | `px`, `%`, `em`, `rem`; `auto` where Yoga takes it |
| Angles, times | `deg` `rad` `grad` `turn`; `ms` `s` |
| Colours | `#rgb` `#rgba` `#rrggbb` `#rrggbbaa`; `rgb()`/`rgba()` in the comma and the space form (`rgb(0 0 0 / 50%)`); `transparent`; the 16 CSS 1 named colours. Interpolated in OKLCH |
| Layout (Yoga) | `flex-direction` `flex-wrap` `flex` `flex-grow` `flex-shrink` `flex-basis` `justify-content` `align-items` `align-self` `align-content` `gap` `row-gap` `column-gap` `padding(-*)` `margin(-*)` `width` `height` `min-/max-width/height` `position` (`static` `relative` `absolute`) `inset` `top` `right` `bottom` `left` `overflow` (`visible` `hidden` `scroll` `auto`) |
| Text flow | `white-space` (`normal` `nowrap` `pre` `pre-wrap` `pre-line`), `overflow-wrap`/`word-wrap`, `word-break` (`normal` `break-all`), `text-overflow` (`clip` `ellipsis`), `text-align` (`start` `center` `end`), `text-decoration(-line)` (`none` `underline` `line-through`) |
| Typography | `font-family` (first name of a list), `font-size` (lengths), `font-weight` (1–1000, `normal`, `bold`), `font-style` (`normal` `italic`), `line-height` (length or number) |
| Paint | `color`, `opacity` (number or %), `background` (layers of `linear-`/`radial-gradient`, their `repeating-` forms and quoted `url()`), `background-color`, `background-image`, `background-position` (lengths), `background-size`, `background-repeat` |
| Border | `border`, `border-{side}`, `border-width`, `border-color`, `border-{side}-width/-color`, `border-radius` (1–4 lengths); styles `solid` `dashed` `dotted` `double` `none` |
| Border image | `border-image` and its five longhands |
| Outline, shadow | `outline`, `outline-width`, `outline-color`, `outline-offset`; `box-shadow` (list, `inset`, blur, spread) |
| Transform | `transform` with `translate(X/Y)`, `scale(X/Y)`, `rotate`, `skew(X/Y)`, `matrix`; `transform-origin` |
| Motion | `transition`; `animation` and its seven longhands; easings `ease-enter`, `ease-exit`, `linear`, with `ease`/`ease-in`/`ease-out`/`ease-in-out` mapped onto them |
| Animatable | `opacity`, `color`, `background-color`, `background-position`, `border-color`, `box-shadow`, `transform` |
| Cursor | 14 keywords (`default` `pointer` `text` `move` `wait` `progress` `crosshair` `not-allowed` four resizes `grab` `grabbing`) |

## What is missing

Each table says what happens today and how big the job looks. **Cost** is a
rough reading of the code: *wiring* means the engine underneath can already do
it and only the CSS side is absent, *moderate* means new code in one subsystem,
*large* means a new subsystem or a rendering capability the toolkit does not
have. *By design* marks a gap the guide or an ADR already argues for keeping.

### 1. Cascade, values and syntax

| Missing | Today | Cost | Note |
|---|---|---|---|
| `inherit`, `initial`, `unset`, `revert`, `revert-layer` | dropped | wiring | The commonest way to undo a theme rule. `padding: inherit` and `background-color: inherit` drop. `initial` works only inside `flex` |
| `all` shorthand | unknown property | moderate | Needs the global keywords first |
| `calc()`, `min()`, `max()`, `clamp()` and the other math functions | dropped | moderate to large | `TODO.md` already cites missing `calc()` twice (the slider thumb radius and `slider-ticks`' inset). A px/em/rem-only fold is cheap; mixing in `%` needs resolving after layout, and Yoga takes no expressions |
| `currentcolor` | dropped | wiring | `border-color: currentcolor` is how CSS ties a border to the text colour |
| `env()`, `attr()` | dropped as an unknown value | small | `env(safe-area-inset-*)` means little on a desktop; low value |
| `@property` (typed, animatable custom properties) | refused | moderate | Also the only route to animating a custom property |
| `@layer` | refused | moderate | The four layers are fixed by design (the guide: "the order cannot be argued with"). An application cannot sub-layer its own sheets |
| `@import` | refused | small | An application lists sheets in `stylesheets()` instead |
| `@scope` | refused | moderate | |
| `@namespace` | refused | by design | No namespaces in the element tree |
| **CSS nesting** (`a { &:hover {…} }`, `a { b {…} }`) | **malformed: the whole sheet is refused, even lenient** | moderate | See *Findings* below. Nesting has been in every major browser since 2023, so authors will write it |
| Inline `style=` in KDL markup | not read | by design | `INLINE` is a widget's own layer, written through `Styled.restyle` |

### 2. Selectors

| Missing | Today | Cost | Note |
|---|---|---|---|
| `:not()` | refused | moderate | The guide gives the router's refusal to set `:hover` on a disabled widget as the reason `:not(:disabled):hover` is not needed. It is still the general-purpose negation |
| `:is()`, `:where()` | refused | moderate | `:where()` is how a library writes zero-specificity defaults |
| `:has()` | refused | large | Upward invalidation; the cascade's invalidation is all downward today |
| `:focus-within` | refused | moderate | `Handles.onFocusWithin` is the Java alternative. The router already knows the focus chain, so this is close to `:hover`'s ancestor walk |
| `:enabled`, `:read-only`, `:read-write`, `:required`, `:optional`, `:placeholder-shown`, `:valid`, `:default`, `:empty`, `:target`, `:lang()`, `:dir()` | refused | small each | `:enabled` is the commonest; the rest need a widget to report the state |
| `:first-of-type`, `:last-of-type`, `:only-of-type`, `:nth-of-type()`, `:nth-last-of-type()` | refused | small | `Structural` already counts siblings; counting by type is the same walk |
| `:nth-child(An+B of S)` | **malformed** | small | Should at least be *refused*, so a lenient sheet keeps its other rules |
| Attribute selectors `[attr]`, `[attr=v]` and the rest | refused | moderate | The cascade has no attributes to match. A widget's attributes are KDL properties and are not exposed to it |
| Sibling combinators `+`, `~` | refused | moderate | Structural invalidation already restyles siblings whose position changed; a sibling's *state* changing is new |
| Pseudo-elements `::before`, `::after`, `::placeholder`, `::selection`, `::marker`, `::first-line`, `::first-letter`, `::backdrop` | refused | by design (mostly) | A part type is used instead (`check-indicator`, `scroll-thumb`). Generated content is a widget. `::placeholder` and `::selection` are the ones authors will look for first; both are colours a part could carry |

### 3. Units and colours

| Missing | Today | Cost | Note |
|---|---|---|---|
| Viewport units `vw` `vh` `vmin` `vmax` (and `svh`/`lvh`/`dvh`) | dropped | small | The window's logical size is already in `MediaContext` |
| Container units `cqw` `cqh` … | dropped | large | Need container queries |
| `ch`, `ex`, `cap`, `ic`, `lh`, `rlh` | dropped | small to moderate | `ch` is the useful one for a field's width; needs the font's metrics |
| Absolute units `pt` `pc` `in` `cm` `mm` `q` | dropped | trivial | |
| `%` font size, `font-size` keywords (`small`, `larger` …) | dropped | small | `1.5em` works and `150%` does not |
| `%` border radius, elliptical radii (`10px / 5px`) | dropped | moderate | `border-radius: 50%` is the usual circle; the toolkit writes `9999px` |
| `hsl()`/`hsla()`, `hwb()` | dropped | trivial | |
| `lab()`, `lch()`, `oklab()`, `oklch()`, `color()` | dropped | small | `Oklch` exists for mixing; `CssColor`'s doc refuses `oklch()` as input on purpose, which is worth revisiting |
| `color-mix()`, relative colour syntax (`rgb(from …)`) | dropped | moderate | `color-mix()` is how a theme derives hover shades without a token each |
| Named colours beyond the 16 (`orange`, `rebeccapurple`, the 148 of CSS Color 4) | dropped | trivial | |
| System colours (`Canvas`, `CanvasText`, `AccentColor` …) | dropped | moderate | Would pair with `forced-colors` |

### 4. Layout

| Missing | Today | Cost | Note |
|---|---|---|---|
| `display` (`none`, `contents`, `flex`) | unknown property | **wiring** | `YGNodeStyleSetDisplay` is already bound and exported from `libgoldberry`. `display: none` is the biggest single surprise for a CSS author |
| `display: grid` and the `grid-*` properties | unknown property | large | Yoga 3.2.1 has no grid. `table` and `calendar` lay out their own cells |
| `display: block`/`inline`/`inline-block`, flow layout, `float`, `clear` | unknown property | by design | Everything is a flex box |
| `aspect-ratio` | unknown property | **wiring** | `YGNodeStyleSetAspectRatio` is bound |
| `box-sizing` | unknown property | small | Yoga 3.2.1 has `YGNodeStyleSetBoxSizing`, but `libgoldberry` does not export it, so this is a native ABI bump. A border takes no layout room here at all, so the current model is neither of CSS's two |
| `order` | unknown property | moderate | Yoga has no `order`; the element list would be reordered before layout |
| `z-index` | unknown property | moderate | `Box.elevated` is one bit ("draw me last"), with no stacking context and no order among elevated siblings |
| `visibility` (`hidden`, `collapse`) | unknown property | small | Paint and hit-test skip, layout keeps the room |
| `position: fixed`, `position: sticky` | dropped | moderate / by design | `affix` is the sticky widget, and `:affixed` its state |
| `overflow-x`, `overflow-y`, `overflow: clip` | unknown / dropped | moderate | A `scroll` scrolls one axis by construction |
| `justify-content: stretch`/`left`/`right`/`normal`; `place-content`, `place-items`, `place-self`, `justify-items`, `justify-self` | dropped / unknown | small | `start`/`end` are aliased (ADR-0247); `left`/`right` are kept out on purpose |
| `width: fit-content`/`min-content`/`max-content` | dropped | moderate | Yoga 3.2.1's style API has no intrinsic-size keywords |
| Logical properties (`margin-inline`, `padding-block`, `inset-inline-start` …) | unknown property | small | One writing mode, so they map 1:1 onto the physical ones; RTL is where it starts to matter |
| `direction`, `writing-mode`, `unicode-bidi` | unknown property | large | `YGNodeStyleSetDirection` is bound; text is LTR-first |
| `vertical-align` | unknown property | moderate | `html-view` draws `sub` and `sup` as a size and not a baseline shift for this reason |
| Multi-column (`columns`, `column-count` …), `table-layout`, `list-style*`, counters, `content` | unknown property | by design | Widgets, not CSS |

### 5. Text and fonts

| Missing | Today | Cost | Note |
|---|---|---|---|
| `letter-spacing`, `word-spacing` | unknown property | moderate | `TODO.md` lists `letter-spacing` as left of §8's unimplemented list. Needs the shaper to take tracking |
| `text-transform` | unknown property | small | Uppercase labels are a common design-system rule |
| `text-shadow` | unknown property | moderate | `box-shadow`'s ramp could be reused over glyph runs |
| `text-indent`, `tab-size`, `hyphens`, `text-wrap` (`balance`, `pretty`), `line-clamp`/`-webkit-line-clamp` | unknown property | moderate each | A paragraph already knows its lines, so `line-clamp` is close to `text-overflow: ellipsis` |
| `line-height: normal` | dropped | trivial | Only a length or a number is read |
| `text-decoration` colour, style, thickness; `text-decoration-color`/`-style`/`-thickness`, `text-underline-offset` | dropped / unknown | moderate | `underline red` drops the whole declaration on purpose rather than half-applying it |
| `white-space: break-spaces`; `white-space-collapse`, `text-wrap-mode` | dropped / unknown | small | |
| `font` shorthand | unknown property | small | |
| `font-variant*`, `font-feature-settings`, `font-variation-settings`, `font-stretch`, `font-optical-sizing`, `font-synthesis` | unknown property | moderate to large | Variable weights are *not scheduled* in the limitations page: a weight is a face |
| `font-style: oblique` | dropped | by design | Nothing shears a glyph |
| `text-align: left`/`right`/`justify` | dropped | by design / large | `left`/`right` because they differ from `start`/`end` under RTL; `justify` because a line is not a shaped run in every case |
| `@font-face` | refused | moderate | Fonts are registered from Java (`Host.fonts()`); a sheet cannot ship its own face |
| Font fallback lists | the first family is used, the rest discarded | moderate | Per-glyph fallback down the list is what CSS does |

### 6. Backgrounds and borders

| Missing | Today | Cost | Note |
|---|---|---|---|
| `border-style` and `border-{side}-style` | unknown property | small | The style can only be set inside a shorthand |
| `groove`, `ridge`, `inset`, `outset` | drawn solid, warned once | small | |
| `outline-style` and a non-solid outline | unknown / always solid | small | By design for focus rings, but `outline` is used for other things too |
| Per-corner longhands (`border-top-left-radius` …) | unknown property | trivial | The four-value shorthand works |
| `background-clip`, `background-origin`, `background-attachment`, `background-blend-mode` | unknown property | moderate | `background-clip: text` is a popular effect |
| `background-position` with `%` or keywords | parsed, moves nothing | small | Right for a gradient the size of its box; wrong for an untiled `url()` layer smaller than the box, which sits at the top left |
| `background-position-x`/`-y` | unknown property | trivial | |
| `background-repeat: space`, `round` | dropped | small | |
| `conic-gradient()`, `repeating-conic-gradient()` | dropped | moderate | Pie and colour-wheel fills |
| Gradient transition hints, colour interpolation methods (`in oklch`) | dropped | small | Interpolation is already OKLCH-capable |
| `image-set()`, `cross-fade()`, `element()`, unquoted `url(x.png)` | dropped | small | `@2x` is picked by file name instead |

### 7. Effects and transforms

| Missing | Today | Cost | Note |
|---|---|---|---|
| `filter` (`blur`, `brightness`, `drop-shadow` …) | unknown property | large | Needs offscreen layers with a filter pass. `opacity` already renders a subtree into a layer |
| `backdrop-filter` | unknown property | large | Named in `TODO.md` as still unimplemented. Needs what is behind the box |
| `mix-blend-mode`, `isolation` | unknown property | large | |
| `clip-path`, `mask*` | unknown property | large | `overflow: hidden` clips to the rounded box, and that is the only clip |
| 3D transforms (`translate3d`, `rotateX/Y/Z`, `perspective()`, `matrix3d`), `perspective`, `transform-style`, `backface-visibility` | dropped / unknown | large | `Affine` is 2D |
| Individual `translate`, `rotate`, `scale` properties | unknown property | small | Easier to animate independently than one `transform` list |
| `transform-box` | unknown property | small | |
| `will-change`, `contain`, `content-visibility` | unknown property | small / by design | Performance hints; the renderer decides layers itself |

### 8. Motion

| Missing | Today | Cost | Note |
|---|---|---|---|
| `cubic-bezier()` | skipped with a warning, the entry runs on `ease-enter` | **trivial** | `Easing`'s three curves are already stored as cubic-bezier control points (`EASE_ENTER(0.2, 0, 0, 1)`) |
| `steps()`, `step-start`, `step-end`, `linear()` | skipped, as above | small | |
| CSS's own curves, exactly | `ease`, `ease-out` and `ease-in-out` all run as `ease-enter`; `ease-in` as `ease-exit` | trivial | An info line names each mapping |
| `transition: all` | **dropped** | small | Only the seven animatable names are read, so `all` fails the whole declaration |
| `transition-property`, `-duration`, `-timing-function`, `-delay`, `-behavior` | unknown property | small | `animation` has its longhands; `transition` has none |
| `animation-play-state`, `animation-composition`, `animation-timeline`, `view-timeline`, `scroll-timeline` | unknown property | small to large | `ScrollTimelineTest` exists in `:widgets`, but a stylesheet cannot name a timeline |
| Animating anything else (`border-radius`, `outline-*`, `width`, `font-size`, custom properties …) | the declaration is dropped | by design / moderate | Layout properties are excluded so a frame never runs layout. `border-radius`, `outline-color` and `outline-offset` are paint-only and could join |
| `@keyframes` inside `@media` | refused | small | |
| Per-keyframe `animation-timing-function` | not read | small | |
| `offset-path` and motion paths | unknown property | large | |

### 9. Media and container queries

| Missing | Today | Cost | Note |
|---|---|---|---|
| `hover`, `any-hover`, `pointer`, `any-pointer` | refused | small | A desktop answers `hover` and `fine`; a touch screen is where it matters |
| `resolution`, `min-resolution`, `-webkit-device-pixel-ratio` | refused | small | `Host` knows the scale; `@2x` pictures are chosen without it |
| `aspect-ratio`, `min-aspect-ratio` | refused | trivial | |
| `prefers-contrast`, `forced-colors`, `prefers-reduced-transparency`, `inverted-colors` | refused | moderate | Each needs the desktop's setting, as `prefers-color-scheme` reads it |
| `color-gamut`, `dynamic-range`, `display-mode`, `scripting`, `update` | refused | small / n/a | |
| Media types other than `all`, `screen`, `print` | never match, warned once | by design | |
| `@container` and container units | refused | large | The case a widget library has more than a page: a card styled by its own width |
| `@supports` | refused | small | Would answer from `ComputedStyle.isProperty`, which already exists |

### 10. Interaction and UI

| Missing | Today | Cost | Note |
|---|---|---|---|
| `pointer-events` | unknown property | small | Hit testing already walks the boxes |
| `user-select` | unknown property | small | Selection is per widget today |
| `caret-color`, `accent-color` | unknown property | small | Each control reads its own `--gb-*` token instead |
| `scrollbar-color`, `scrollbar-width`, `scrollbar-gutter` | unknown property | small | `Scrollbars.ALWAYS` and the `scroll-thumb` part cover this from the toolkit's side |
| `scroll-behavior`, `scroll-snap-*`, `overscroll-behavior`, `scroll-margin`/`-padding` | unknown property | moderate | |
| `touch-action`, `resize`, `appearance` | unknown property | small / by design | |
| Cursor keywords `help`, `none`, `context-menu`, `cell`, `vertical-text`, `alias`, `copy`, `no-drop`, `all-scroll`, `col-resize`, `row-resize`, the eight `n`/`e`/`s`/`w` resizes, `zoom-in`, `zoom-out` | dropped | small | SDL has system cursors for most of the resize family. `cursor: none` is common in games |
| `cursor: url(…)` | dropped | small | Since GB-033 an application gives a *shape* its picture through `Application.cursors()`; a sheet still cannot name a file |

### 11. The HTML view

`html-view` reads `<style>` blocks and `style=` attributes into its model and
applies neither, by design: the page follows the application's theme
(`Html`'s doc comment, and *An HTML engine* is not scheduled on the limitations
page). Every gap above applies to `html.css` and `markdown.css` too, since they
are ordinary sheets.

## Findings beyond the gaps

1. **Nesting refuses the whole sheet in lenient mode.** `a { color: red; &:hover
   { color: blue } }` throws `expected a property name, found "&"`, and `a { b {
   color: red } }` throws `expected ":", found "{"`. Both are *malformed*, not
   *unsupported*, so a lenient application sheet does not drop the one rule: the
   window gets no application sheet at all. That breaks the guide's promise
   ("drops **that rule**, with one warning"). The cheapest fix is for the
   declaration parser to recognise a nested rule and raise the
   unsupported-feature error, which the lenient path already knows how to skip.
   The same goes for `:nth-child(2n of .x)`, which is malformed rather than
   refused.
2. **The guide undersells the code** (`book/src/guide/styling.md`):
   - *Transform* lists `translate`, `scale` and `rotate`. The code also reads
     `translateX/Y`, `scaleX/Y`, `skew`, `skewX/Y` and `matrix`.
   - *Colour* says "a hex value, `rgb()` or `rgba()`, `transparent`, or a
     `var()`". The code also reads the 16 named colours and the space-separated
     form with a slash alpha.
   - `opacity` takes a percentage.
   - `overflow: auto` is accepted (as `scroll`), which the guide does not say.

   These belong with the next guide edit, which waits for the release
   (`docs/snapshot/`). They are not urgent, since the guide errs on the side
   of saying less than the code does.
3. **`transition: all` fails the whole declaration.** It is probably the most
   commonly written transition in CSS, and a sheet copied from the web loses
   every transition that uses it.
4. **`cubic-bezier()` is closer than it looks.** The three built-in curves are
   already cubic-bezier control points, so reading arbitrary ones is a parser
   change with no new maths. That would also let `ease`, `ease-in-out` and the
   rest run as themselves instead of the nearest of three.

## Suggested order

Picked for what a CSS author hits first, and what the engine underneath
already supports.

**Wiring and small parser work (days):**
1. Make nesting and `:nth-child(… of S)` *unsupported* rather than malformed.
2. `display: none` and `aspect-ratio` (both already bound in Yoga).
3. `inherit`, `initial`, `unset`, and `currentcolor`.
4. `hsl()`, `hwb()`, `oklch()`/`oklab()`, the 148 named colours.
5. `cubic-bezier()`, exact CSS easing keywords, `transition: all`, the
   `transition-*` longhands.
6. `%` and per-corner `border-radius`; `border-style` longhands.
7. `:enabled`, the `-of-type` family, `line-height: normal`, `%` font sizes,
   viewport units, the missing cursor keywords.

**Moderate (a batch each):**
8. `calc()` / `min()` / `max()` / `clamp()`, folded where no `%` is involved.
9. `:not()`, `:is()`, `:where()`, `:focus-within`.
10. `letter-spacing`, `text-transform`, `text-shadow`, `line-clamp`.
11. `z-index` beyond one bit, `visibility`, `pointer-events`.
12. `@font-face`, `@import`, `@supports`, `@layer` (within the application layer).
13. `hover`/`pointer`/`resolution`/`prefers-contrast` media features.
14. `conic-gradient()`, `background-clip`, `color-mix()`.

**Large, or a decision first:**
15. `filter` and `backdrop-filter` (offscreen layers with a filter pass).
16. Container queries.
17. Grid, which Yoga does not have.
18. Sibling combinators, attribute selectors, `:has()`, pseudo-elements: each
    reopens a choice the guide made on purpose.
19. 3D transforms, `clip-path`, masks, blend modes.

## Status

| Item | State | Where |
|---|---|---|
| This analysis | done 2026-10-10 | this file |
| Findings 1–4 | open, not filed | here; `book/src/TODO.md` when one is picked up |
| Guide corrections (finding 2) | open, waits for the release | to be parked in `docs/snapshot/` when written |
| Suggested order, items 1–19 | not scheduled | each gets an ADR when it is decided |
