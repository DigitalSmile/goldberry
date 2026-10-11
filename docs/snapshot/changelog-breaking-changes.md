<!--
Destination: CHANGELOG.md, in the next release's section (2026.3 unless the
release is renamed), as a `### Breaking changes` heading placed right after the
release's one- or two-sentence summary paragraph and before `### Added`.

Every entry ends with an HTML comment naming its record. When the parked ADRs in
docs/snapshot/adr/ are renumbered at release, renumber the ADR-NNNN notes with
them. If the release notes should carry no record references, remove the notes
from the changelog text. A note that names a CSS-NN item (docs/css-plan.md) or a
commit marks a change that has no ADR of its own.

Everything below is measured against 2026.2 (tag commit d3eacfdc).
-->

### Breaking changes

#### Java API

- Several public records have new components. A record pattern that takes one
  apart, or a call to its canonical constructor, has to be written for the new
  shape; the accessors and `with…` methods are unchanged. Each of these keeps a
  constructor of the old shape: `Corners` (its components are four
  `Corners.Radius` now, each a horizontal and a vertical radius; `topLeft()` to
  `bottomLeft()` stay as methods, and so does the four-`double` constructor),
  `Decoration` (`borderImage`, `outlineStyle`), `Typography` (`lettering`,
  `variation`), `TextFlow` (`maxLines`), `Keyframes` (`media`), `StyleRule`
  (`layer`, `containers`), `Stylesheet` (`fontFaces`, `layers`),
  `CssParser.Parsed` (`imports`, `fontFaces`, `layers`), `Selector.Compound`
  (`functional`, `pseudoElement`), `Transitions` (`lists`), `KeyframeAnimations`
  and its `Entry` (the play state), `MediaContext` (`resolution`, `input`,
  `preferences`), `CssLength.Context` (`viewport`, `fontUnits`, `container`),
  `Transform` (`individual`, `box`), `Transform.Length` (`offset`),
  `BackgroundPosition` (`xPercent`, `yPercent`), `Background.Layers` (lists of
  positions, sizes, repeats, origins and clips in place of its one `position`),
  `GradientLayer.ColorStop` (`hint`), `GradientLayer.Linear` and `Radial`
  (`interpolation`), `Box.Text` (`spans`, `shadows`), `Attributes` (`draggable`,
  `dropTarget`), `HitTest.Region` (`shape`), `Canvas` (`overlay`), `ListView`
  (`rowHeights`), and in `goldberry-media` `Track` (`frameCount`) and
  `TrackParams.Video` (`frameRate`, `alpha`). Two have no constructor of the old
  shape: `ComputedStyle`, whose `overflow` component is now `overflowX` and
  `overflowY` (`overflow()` still answers), and `PlayerStatus`, which gained
  `looping`. Build those through their factories and `with…` methods. `Box` and
  `PipelineSpec` are described below.
  <!-- ADR-0584, ADR-0585, ADR-0586, ADR-0591, ADR-0592, ADR-0597, ADR-0599, ADR-0600, ADR-0604, ADR-0606, ADR-0608, ADR-0610, ADR-0611, ADR-0613, ADR-0615, ADR-0618, ADR-0621, ADR-0556, ADR-0558; CSS-16, CSS-26, CSS-27, CSS-31, CSS-34, CSS-35, CSS-36, CSS-38, CSS-39 -->
- `Box` has no `elevated` component. Its place is taken by `presence`, a
  `layout.Presence` that carries `z-index` and the box's other presence
  properties, and both long constructors take `Overflow overflow, Presence
  presence` where they took `boolean elevated, Overflow overflow`. Pass
  `Presence.NORMAL`, or `Presence.NORMAL.zIndex(1)` for a box that was elevated.
  `box.elevated(true)` and `box.elevated()` still work: the first is `z-index:
  1` unless a stylesheet gave the box more, the second says whether the
  `z-index` is above `auto`.
  <!-- ADR-0609; CSS-28 -->
- `Easing` is a sealed interface over `CubicBezier`, `Steps` and
  `PiecewiseLinear` (in `dev.goldberry.motion.easing`), not an enum.
  `Easing.EASE_ENTER`, `EASE_EXIT` and `LINEAR` still compile where they are
  named, but `Easing.values()`, `valueOf`, `ordinal()`, `name()`, `EnumSet` and
  `EnumMap` of it, and a `switch` over the three constants do not: use
  `Easing.BUILT_IN` for the list, `cssName()` for the name, and switch over the
  three kinds of curve. `Easing.parse("ease-in-out")` and the other CSS keywords
  now answer CSS's own curves rather than the nearest of the three.
  <!-- ADR-0600 -->
- `Paragraph.isBidiApproximate()` is gone, because a paragraph no longer
  approximates bidirectional text. Ask `isBidirectional()` whether the text has
  right-to-left runs, and `directionAt(offset)` or `directionality()` which way
  it reads.
  <!-- ADR-0622 -->
- Public enums have new constants, so a `switch` over one of them that lists
  every constant without a `default` no longer compiles: `render.Cursor` (21
  more shapes: `NONE`, `HELP`, `CONTEXT_MENU`, `CELL`, `VERTICAL_TEXT`, `ALIAS`,
  `COPY`, `NO_DROP`, `ALL_SCROLL`, `COL_RESIZE`, `ROW_RESIZE`, the eight edge
  and corner resizes from `N_RESIZE` to `SW_RESIZE`, `ZOOM_IN` and `ZOOM_OUT`),
  `TextAlign` (`LEFT`, `RIGHT`, `JUSTIFY`), `Selector.PseudoClass` (`ENABLED`,
  `FOCUS_WITHIN`, `DRAG_OVER`), `Selector.Combinator` (`NEXT_SIBLING`,
  `SUBSEQUENT_SIBLING`, `PART`), `Overflow` (`CLIP`), `Transitions.Animatable`
  (`BORDER_RADIUS`, `OUTLINE_COLOR`, `OUTLINE_OFFSET`, `TRANSLATE`, `ROTATE`,
  `SCALE`), `Role` (`TEXT`), in `goldberry-gpu` `TextureFormat` (two sRGB, three
  float and nine block-compressed formats), `TextureUsage` and `BufferUsage`
  (`GRAPHICS_STORAGE_READ`, `COMPUTE_STORAGE_READ`, `COMPUTE_STORAGE_WRITE`) and
  `BlendMode` (`ADDITIVE`), and in `goldberry-media` `PixelFormat` (`I420A`) and
  `Transport.Glyph` (`LOOP`). Add the cases, or a `default`.
  <!-- ADR-0563, ADR-0565, ADR-0580, ADR-0581, ADR-0586, ADR-0591, ADR-0598, ADR-0599, ADR-0612, ADR-0613, ADR-0615, ADR-0556; CSS-17, CSS-19, CSS-22, CSS-38, CSS-39 -->
- Sealed types have new permitted subtypes, so an exhaustive `switch` over one
  needs another case: `layout.Length` (`Mixed`, a percentage plus points, which
  a painted property's length can be), `MediaCondition` (`AspectRatio`,
  `Resolution`, `PointerQuery`, `HoverQuery`, `ContrastQuery`, `ForcedColors`,
  `ReducedTransparency`, `ColorGamut`), `Structural` (`OfType`), `GradientLayer`
  and `paint.Gradient` (`Conic`), `ImageSource` (`Region`), and in
  `goldberry-gpu` `RenderTarget` (`TextureView`) and `GpuResource`
  (`ComputePipeline`).
  <!-- ADR-0563, ADR-0565, ADR-0579, ADR-0604; CSS-17, CSS-34, CSS-35 -->
- `Background.layers()` and `BackgroundParser.images(…)` answer a
  `List<CssImage>`, since a layer may be a `url()` picture as well as a
  gradient. `GradientLayer` is a `CssImage`, so code that wants the gradients
  keeps the `instanceof GradientLayer` ones. `Background.of` and `layers(…)`
  take `List<? extends CssImage>`, so passing a list of gradients still
  compiles.
  <!-- ADR-0584 -->
- `ContainingBlock.insetFor` and `acrossBorderBox` take the containing block's
  `Border` where they took its padding `Insets`, and `insetFor` hands an inset
  back as it is, since layout now knows the border. A part placed in the content
  box uses `ContainingBlock.inContentBox(inset, padding, border)`.
  `StyleResolver.positionMatters` takes two `SiblingPosition`s in place of four
  `int`s.
  <!-- ADR-0575, ADR-0620; CSS-17 -->
- `Host` has two more abstract methods, `shortcut(Shortcut, Runnable, Repeat)`
  and `shortcut(Shortcut, Runnable, Object, Repeat)`. A class of the
  application's that implements `Host`, such as a test double, has to implement
  them; `Repeat.FIRE` is what the existing forms bind.
  <!-- ADR-0576 -->
- `State` has a `protected final BuildContext context()`. A state class that
  declares a method `context()` of its own no longer compiles; rename it, or use
  the inherited one, which answers from `initState` until `dispose`.
  <!-- ADR-0569 -->

#### GPU

- `RenderPass.target()` answers an `Optional<RenderTarget>`, and
  `PipelineSpec.targetFormat()` an `Optional<TextureFormat>`, because a pass and
  a pipeline may now draw depth alone. `PipelineSpec`'s nine-argument
  constructor takes the format as an `Optional`; `PipelineSpec.builder` is
  unchanged.
  <!-- ADR-0563 -->
- `TextureSpec` (`type`, `depth`, `layers`, `mipLevels`, `samples`),
  `SamplerSpec` (`mipFilter`, `compare`, `maxAnisotropy`), `ShaderCode`
  (`storageTextures`, `storageBuffers`) and `PipelineSpec` (`samples`) have new
  components. Their old constructors and factories stay and mean what they
  meant, apart from `PipelineSpec`'s above; a record pattern over any of them
  has to be written for the new shape.
  <!-- ADR-0563, ADR-0565, ADR-0566, ADR-0578, ADR-0582, ADR-0583 -->

#### Stylesheets and layout

- A border takes room, outside the padding, as in CSS, and `box-sizing` is read
  with `border-box` as its initial value. A box given a `width` or `height`
  keeps that size and its content is narrower by the border; a box sized by its
  content grows by its border, so a chip, a menu, a tooltip, a dialog or a toast
  with a 1px border is 2px larger and its content sits 1px further in. A rule
  that padded a box by its border's width to keep the content clear of it can
  take that width off the padding; `box-sizing: content-box` adds the border and
  the padding to the size instead. Screenshot tests of bordered boxes need their
  references taken again.
  <!-- ADR-0620 -->
- A background layer is placed inside the border (`background-origin:
  padding-box`, CSS's initial value) and is still drawn under the border, where
  2026.2 placed every layer over the whole box. A gradient or picture on a
  bordered box therefore starts and ends a border's width further in. Write
  `background-origin: border-box` for the old placement.
  <!-- CSS-36, no ADR -->
- An absolutely positioned child is placed against its containing block's
  padding box, inside the border, as CSS places it. 2026.2 measured its `top`,
  `right`, `bottom` and `left` from the content box, inside the padding, so a
  child at `top: 10px; right: 10px` in a padded panel now sits the padding's
  width nearer the corner. Add the containing block's padding to the insets to
  keep the old position.
  <!-- ADR-0575, ADR-0620 -->
- A box whose `overflow` is not `visible` and which has a `border-radius` cuts
  its children to the curve inside its border, where 2026.2 cut them to a
  rectangle. A picture in a round avatar is round, a `progress` fill is rounded
  inside its track, and an unpadded rounded `scroll` rounds the rows that reach
  its corners. A box padded wider than its radius keeps the rectangular clip it
  had. A `canvas3d` or a video under such a clip is still cut to the rectangle.
  <!-- ADR-0596 -->
- Declarations an application or theme sheet wrote that 2026.2 dropped or did
  not know are now applied, so a sheet that carried them unread looks different.
  Properties now read include `display`, `aspect-ratio`, `visibility`,
  `pointer-events`, `user-select`, `z-index`, `order`, `direction`,
  `unicode-bidi`, `box-sizing`, `overflow-x` and `overflow-y`, the logical
  properties (`margin-inline`, `padding-block`, `inset-inline`, `inline-size`
  and the rest), `place-content`, `place-items`, `place-self`, `font`,
  `letter-spacing`, `word-spacing`, `text-transform`, `text-shadow`,
  `text-justify`, `line-clamp`, `font-stretch`, `font-variation-settings`,
  `border-style` and the side and corner longhands, `outline-style`,
  `background-size`, `background-repeat`, `background-origin`,
  `background-clip`, `background-position-x` and `-y`, `border-image`, the
  `transition-*` longhands, `animation-play-state`, `translate`, `rotate`,
  `scale`, `transform-box`, `clip-path`, `mix-blend-mode`, `isolation`, `filter`
  and the `container` properties. Values now read include every CSS Color 4
  colour (`hsl()`, `hwb()`, `lab()`, `lch()`, `oklab()`, `oklch()`, `color()`,
  all 148 named colours, `currentcolor`), `color-mix()` and relative colours,
  `inherit`, `initial`, `unset`, `revert`, `revert-layer` and `all`, `calc()`
  and the other math functions, the units `ch`, `ex`, `in`, `cm`, `mm`, `Q`,
  `pt`, `pc`, the viewport and container units, keyword and percentage font
  sizes, `line-height: normal`, `text-align: left`, `right` and `justify`,
  `url()` background layers, `conic-gradient()`, gradient hints and `in <colour
  space>`, percentage and elliptical `border-radius`, `overflow: clip`, the rest
  of CSS's cursor keywords (`cursor: none` hides the pointer), the transform
  functions `translateX`, `translateY`, `scaleX`, `scaleY`, `skew`, `skewX`,
  `skewY` and `matrix`, and `transition: all` and transitions of
  `border-radius`, `outline-color`, `outline-offset`, `translate`, `rotate` and
  `scale`. Each of these was a warning in the 2026.2 log (`dropping …` or an
  unknown property); delete the declarations that should stay without effect.
  <!-- ADR-0598, ADR-0601, ADR-0602, ADR-0603, ADR-0604, ADR-0608, ADR-0609, ADR-0614, ADR-0615, ADR-0618, ADR-0619, ADR-0620, ADR-0621, ADR-0622; CSS-16, CSS-18, CSS-19, CSS-26, CSS-27, CSS-28, CSS-35 to CSS-39 -->
- Rules that 2026.2 dropped from a lenient sheet, or that never held, now apply:
  selectors with `:is()`, `:where()`, `:not()`, `:has()`, `+`, `~`,
  `:nth-child(An+B of S)`, `:enabled`, `:focus-within`, the `-of-type`
  pseudo-classes, `::placeholder` and `::selection`; the at-rules `@import`,
  `@layer`, `@supports`, `@container`, `@font-face` and `@keyframes` inside
  `@media`; and `@media` queries on `aspect-ratio`, `resolution`, `pointer`,
  `any-pointer`, `hover`, `any-hover`, `prefers-contrast`, `forced-colors`,
  `prefers-reduced-transparency` and `color-gamut`. On a desktop with a mouse,
  `(hover: hover)` and `(pointer: fine)` hold, and a desktop's high-contrast
  setting is read. Remove the rules that were kept only because they did
  nothing.
  <!-- ADR-0606, ADR-0610, ADR-0611, ADR-0612, ADR-0613, ADR-0621; CSS-17, CSS-22, CSS-30, CSS-31, CSS-34, CSS-38 -->
- In an application or theme sheet, `ease`, `ease-in`, `ease-out` and
  `ease-in-out` run as CSS defines them rather than as the nearest of the
  toolkit's three curves, and `cubic-bezier()`, `steps()`, `step-start`,
  `step-end` and `linear(…)` are applied rather than dropped in favour of
  `ease-enter`. A transition or animation that names them moves differently.
  Write `ease-enter`, `ease-exit` or `linear` for the toolkit's own motion.
  <!-- ADR-0600 -->
- The toolkit's placeholder rule is `::placeholder` now, the least specific rule
  that can name it, where it was `text-value.placeholder` and
  `select-value.placeholder`. An application rule that colours `text-value` or
  `select-value` therefore colours the placeholder too. Give the placeholder its
  own rule, `text-input::placeholder { color: var(--gb-text-placeholder) }`, or
  write the value's rule as `text-value:not(.placeholder)`.
  <!-- ADR-0613 -->
- Border and outline styles are drawn as written. `groove`, `ridge`, `inset` and
  `outset` are two-toned, lit from the top left, where 2026.2 drew them solid;
  an outline takes `dashed`, `dotted`, `double` and the rest, where 2026.2
  always drew it solid; and the inside of a rounded corner between a thick side
  and a thin one is a quarter ellipse.
  <!-- CSS-16, no ADR -->

#### Text

- Right-to-left text is placed in reading order. A paragraph is split into
  directional runs and each line shows them as they read, so Arabic and Hebrew
  in labels, fields and areas read from the right instead of mirrored. A box's
  text takes its base direction from `direction` (`ltr` by default);
  `Paragraph.of(font, text)` with no direction takes each paragraph's from its
  first letter that has one, so a paragraph of Arabic drawn on a `canvas` is
  placed from the right. In a right-to-left paragraph of a `text-input` or a
  `text-area` the arrow keys follow reading order (`Left` moves on), and an
  untouched right-to-left field shows the right end of its value, where its text
  begins. A painter that places a caret or a highlight from the glyph advances
  itself should use `Paragraph.caretX` and `stretchesOf`. `unicode-bidi:
  bidi-override` with `direction: ltr` draws a box's text in its stored order,
  as 2026.2 did.
  <!-- ADR-0622 -->
- A weight is drawn at the weight written. Upright Inter and JetBrains Mono are
  variable faces, so `font-weight: 500` is a 500 where 2026.2 drew the 400, and
  `bold` or `700` is a 700 where it drew the 600 SemiBold; `600` and `400` are
  the faces they were. A face an application ships that is variable along `wght`
  draws every weight asked of it, where it drew the one weight it was declared
  at. Inter's italics are static and still draw the nearer of 400 and 600. Write
  `font-weight: 600` where the old bold is wanted.
  <!-- ADR-0615 -->

#### Markup

- A document that writes a property no widget reads is refused when it is
  inflated, as an unknown node is. `row gap=8` throws a `KdlSyntaxException`
  naming the node, its position, the property and what that node does read;
  `gap` belongs in the stylesheet. The same goes for `masonry gap=`, `radio
  selected=`, a segmented `option selected=`, `step current=`, and `id=` or
  `class=` on a chart's `series` and `point`. Fix the document, or build the
  inflater with `.unread(UnreadPolicy.WARN)` (one log line per property) or
  `.unread(UnreadPolicy.IGNORE)` while it cannot be fixed. A factory of the
  application's that reads a property on some paths only has to read it on all
  of them.
  <!-- ADR-0587 -->
- `rich-text` and `run` are built-in node names now, and `rich-text`, `run`,
  `canvas-layer` and `canvas-item` are CSS types the catalogue uses. An
  application that registers its own factory under `rich-text` or `run` gets an
  `IllegalStateException` from `register`; rename its node, or call `replace` to
  shadow the built-in on purpose. Type selectors of the application's that used
  those names match the toolkit's nodes too.
  <!-- ADR-0586, ADR-0592 -->

#### Icons

- The bundled icons are Lucide 1.54.0, where 2026.2 bundled 0.469.0. Lucide
  renamed 56 icons and removed 18. `Icon.bundled(name, size)` and
  `Icons.bind(name)` throw a `NoSuchElementException` for an old name, and
  `Icon.find` answers empty; a markup `icon` names what the application bound,
  so it follows the binding. The removed ones are the brand marks: `chrome`,
  `codepen`, `codesandbox`, `dribbble`, `facebook`, `figma`, `framer`, `github`,
  `gitlab`, `instagram`, `linkedin`, `pocket`, `slack`, `trello`, `twitch`,
  `twitter` and `youtube`, and `rail-symbol`; ship them as the application's own
  icons. The renamed ones, old name first: `album` → `square-bookmark`,
  `align-center` → `text-align-center`, `align-justify` → `text-align-justify`,
  `align-left` → `text-align-start`, `align-right` → `text-align-end`, `angry` →
  `face-angry`, `annoyed` → `face-expressionless`, `badge-help` →
  `badge-question-mark`, `book-marked` → `book-bookmark`, `building-2` →
  `building-complex`, `circle-help` → `circle-question-mark`, `file-audio` and
  `file-audio-2` → `file-headphone`, `file-badge-2` → `file-badge`,
  `file-check-2` → `file-check-corner`, `file-code-2` → `file-code-corner`,
  `file-json` → `file-braces`, `file-json-2` → `file-braces-corner`,
  `file-key-2` → `file-key`, `file-lock-2` → `file-lock`, `file-minus-2` →
  `file-minus-corner`, `file-plus-2` → `file-plus-corner`, `file-question` →
  `file-question-mark`, `file-search-2` → `file-search-corner`, `file-type-2` →
  `file-type-corner`, `file-video` → `file-play`, `file-video-2` →
  `file-video-camera`, `file-volume-2` → `file-signal`, `file-warning` →
  `file-exclamation-point`, `file-x-2` → `file-x-corner`, `filter` → `funnel`,
  `filter-x` → `funnel-x`, `fingerprint` → `fingerprint-pattern`,
  `flip-horizontal` → `square-centerline-dashed-horizontal`, `flip-horizontal-2`
  → `triangles-centerline-dashed-horizontal`, `flip-vertical` →
  `square-centerline-dashed-vertical`, `flip-vertical-2` →
  `triangles-centerline-dashed-vertical`, `frown` → `face-slightly-frowning`,
  `grab` → `hand-grab`, `history` → `rotate-ccw-clock`, `indent-decrease` →
  `list-indent-decrease`, `indent-increase` → `list-indent-increase`, `laugh` →
  `face-grinning`, `letter-text` → `text-initial`, `mail-question` →
  `mail-question-mark`, `meh` → `face-neutral`, `message-circle-question` →
  `message-circle-question-mark`, `podcast` → `mic-signal`, `shield-question` →
  `shield-question-mark`, `smile` → `face-slightly-smiling`, `smile-plus` →
  `face-slightly-smiling-plus`, `text` → `text-align-start`, `text-select` →
  `square-dashed-text`, `trash-2` → `trash`, `waves` → `waves-horizontal`,
  `wrap-text` → `text-wrap`. Some icons that kept their names were redrawn.
  <!-- commit 4ecfbfe8, no ADR -->

#### Native library and builds

- `libgoldberry`'s ABI is 24, where 2026.2's was 18, and the jars of this
  release refuse a library built for an older one. Take `goldberry-natives` at
  the same version as the other Goldberry artifacts. An application that loads
  its own build of the library through `-Dgoldberry.native.library` has to build
  it again from this release's sources, which now include HarfBuzz's subsetter,
  so the library is larger. A GraalVM native image carries the library it was
  built with, so build the image again from matching jars.
  <!-- ADR-0562, ADR-0573, ADR-0580, ADR-0589, ADR-0615, ADR-0620 -->
- `goldberry-media` refuses an `ffmpeg-<target>` classifier jar built before
  this release, because the FFmpeg layout it checks has new fields and
  constants. Take the `ffmpeg-<target>` jars at the same version as
  `goldberry-media`; an FFmpeg build of the application's own has to run the
  layout probe of this release again.
  <!-- ADR-0558, ADR-0599 -->
