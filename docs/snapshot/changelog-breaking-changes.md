<!--
Destination: CHANGELOG.md, in the next release's section (2026.3 unless the
release is renamed), as a `### Breaking changes` heading placed right after the
release's one- or two-sentence summary paragraph and before `### Added`. Add
`### Breaking changes` to the template comment at the top of CHANGELOG.md too.

Only what makes an application's code fail to compile, fail at run time, or
need a migration step is listed. How things look (a border taking room, a
weight drawn at its real weight, CSS that used to be dropped and now applies,
right-to-left text in reading order) is not a break, and goes under
`### Changed` if the release notes mention it.

Every entry ends with an HTML comment naming its record. When the parked ADRs in
docs/snapshot/adr/ are renumbered at release, renumber the notes with them, or
remove the notes if the release notes should carry no record references.

Everything below is measured against 2026.2 (tag commit d3eacfdc).
-->

### Breaking changes

#### Java API
#### goldberry-media: FFmpeg left the module

- The natives classifiers are `natives-<target>`, not `ffmpeg-<target>`:
  `runtimeOnly 'dev.goldberry:goldberry-media::natives-linux-x64'`. There is
  no `ffmpeg-sources` classifier any more; nothing in the module is LGPL.
  <!-- ADR-0651 -->
- A player's status names the decoder that plays: `dav1d`, `libvpx`, `opus`,
  `vorbis`, `flac` or `minimp3` where it said `ffmpeg`, and
  `MediaCapabilities.decoders()` lists codec names the built-in decoders have.
  `MediaCapabilities.demuxers()` names the containers read in Java:
  `matroska`, `webm`, `mp4`, `ogg`, `mp3`. <!-- ADR-0651 -->
- AVI is no longer demuxed: an `.avi` opens as `UnsupportedContainer("AVI")`,
  as MPEG-TS and FLV already did. <!-- ADR-0651 -->
- `HardwareDecoding` reaches the decoders: `DecoderRequest` has a sixth
  component, `hardware`, and `softwareOnly()`. The five-argument constructor
  stays and means `AUTO`; a record pattern over `DecoderRequest` names six
  components. With `OFF` the system decoders decode in software (GStreamer
  passes over NVDEC, VA-API and V4L2; VideoToolbox is asked for its software
  decoder), and a provider with only hardware should say it does not support
  the track. The built-in decoders are software either way. <!-- ADR-0654 -->
- The system property `goldberry.media.ffmpegLog` is gone.
  `goldberry.media.libdir` still names a directory holding the library and its
  `media-layout.properties`. <!-- ADR-0651 -->


- `Easing` is a sealed interface over `CubicBezier`, `Steps` and
  `PiecewiseLinear` (in `dev.goldberry.motion.easing`), not an enum.
  `Easing.EASE_ENTER`, `EASE_EXIT` and `LINEAR` still compile where they are
  named; `values()`, `valueOf`, `name()`, `ordinal()`, `EnumSet`, `EnumMap` and a
  `switch` over the constants do not. Use `Easing.BUILT_IN` for the list,
  `cssName()` for the name, and switch over the three kinds of curve.
  <!-- ADR-0600 -->
- `Goldberry.run()` shuts the runtime down when its loop ends, as
  `Goldberry.shutdown()` does: every window still open is closed and the
  backend is released. An application that showed the same windows again after
  `run()` returned opens new ones; a window opened afterwards starts a fresh
  runtime. `Goldberry.launch()` is unchanged.
  <!-- ADR-0635 -->
- `Trays.show` returns `Optional<ShownTray>` rather than
  `Optional<BackendTray>`. `ShownTray` is a `BackendTray`, so code that only
  calls `close`, `icon` or `tooltip` on it still compiles; a field declared
  `Optional<BackendTray>` changes its type.
  <!-- ADR-0639 -->
- `BackendTray` has a new abstract method, `rows(List<TrayItem>)`. A class that
  implements it, such as a test double, must implement it.
  <!-- ADR-0639 -->
- `Paragraph.isBidiApproximate()` is removed: a paragraph places right-to-left
  text in reading order now. Use `isBidirectional()`, and `directionAt(offset)`
  or `directionality()` for which way it reads.
  <!-- ADR-0622 -->
- `Host` has two new abstract methods, `shortcut(Shortcut, Runnable, Repeat)` and
  `shortcut(Shortcut, Runnable, Object, Repeat)`. A class that implements `Host`,
  such as a test double, must implement them; `Repeat.FIRE` is what the existing
  forms bind.
  <!-- ADR-0576 -->
- `State` has a `protected final BuildContext context()`. A state class that
  declares its own `context()` no longer compiles; rename it, or use the
  inherited one.
  <!-- ADR-0569 -->
- `Box` has no `elevated` component; `presence` (a `layout.Presence`, which
  carries `z-index`) takes its place, and the long constructors take `Overflow
  overflow, Presence presence` where they took `boolean elevated, Overflow
  overflow`. Pass `Presence.NORMAL`, or `Presence.NORMAL.zIndex(1)` for an
  elevated box. The builder methods, `elevated(true)` included, are unchanged.
  <!-- ADR-0609 -->
- `ComputedStyle`'s `overflow` component is now `overflowX` and `overflowY`, and
  `PlayerStatus` (`goldberry-media`) gained `looping`; neither keeps a
  constructor of the old shape. Build them through their factories and `with…`
  methods; `overflow()` still answers.
  <!-- CSS-39, ADR-0556 -->
- Other public records gained components and keep a constructor of the old
  shape, so only a record pattern that takes one apart must be rewritten:
  `Corners` (four `Corners.Radius`), `Decoration`, `Typography`, `TextFlow`,
  `Keyframes`, `StyleRule`, `Stylesheet`, `CssParser.Parsed`,
  `Selector.Compound`, `Transitions`, `KeyframeAnimations`, `MediaContext`,
  `CssLength.Context`, `Transform`, `BackgroundPosition`, `Background.Layers`,
  `GradientLayer`'s stops and gradients, `Box.Text`, `Attributes`,
  `HitTest.Region`, `Canvas`, `ListView`, and `Track` and `TrackParams.Video` in
  `goldberry-media`.
  <!-- ADR-0584 to ADR-0586, ADR-0591, ADR-0592, ADR-0597, ADR-0599, ADR-0600, ADR-0604, ADR-0606, ADR-0608, ADR-0610, ADR-0611, ADR-0613, ADR-0615, ADR-0618, ADR-0621, ADR-0558 -->
- A `switch` with no `default` that lists every constant or permitted subtype
  of these types no longer compiles, since each has more: `render.Cursor`,
  `TextAlign`, `Selector.PseudoClass`, `Selector.Combinator`, `Overflow`,
  `Transitions.Animatable`, `Role`, `layout.Length`, `MediaCondition`,
  `Structural`, `GradientLayer`, `paint.Gradient`, `ImageSource`; in
  `goldberry-gpu` `TextureFormat`, `TextureUsage`, `BufferUsage`, `BlendMode`,
  `RenderTarget` and `GpuResource`; in `goldberry-media` `PixelFormat` and
  `Transport.Glyph`. Add the cases, or a `default`.
  <!-- ADR-0563, ADR-0565, ADR-0579, ADR-0580, ADR-0581, ADR-0604, ADR-0612, ADR-0613, ADR-0615; CSS-17, CSS-19, CSS-34, CSS-35, CSS-38, CSS-39 -->
- `Background.layers()` and `BackgroundParser.images(…)` answer a
  `List<CssImage>`, since a layer may be a `url()` picture. Keep the
  `instanceof GradientLayer` ones where only gradients are wanted.
  <!-- ADR-0584 -->
- `ContainingBlock.insetFor` and `acrossBorderBox` take the containing block's
  `Border` where they took its padding `Insets`; a part placed in the content box
  uses `ContainingBlock.inContentBox(inset, padding, border)`.
  `StyleResolver.positionMatters` takes two `SiblingPosition`s in place of four
  `int`s.
  <!-- ADR-0575, ADR-0620; CSS-17 -->

#### GPU

- `RenderPass.target()` answers an `Optional<RenderTarget>` and
  `PipelineSpec.targetFormat()` an `Optional<TextureFormat>`, because a pass may
  draw depth alone. `PipelineSpec`'s nine-argument constructor takes the format
  as an `Optional`; `PipelineSpec.builder` is unchanged.
  <!-- ADR-0563 -->

#### Markup

- A document that writes a property no widget reads is refused when it is
  inflated, as an unknown node is: `row gap=8` throws a `KdlSyntaxException`
  naming the node, the property and what that node reads. The same goes for
  `masonry gap=`, `radio selected=`, a segmented `option selected=`,
  `step current=`, and `id=` or `class=` on a chart's `series` and `point`. Move
  the property to the stylesheet or remove it, or build the inflater with
  `.unread(UnreadPolicy.WARN)` or `.unread(UnreadPolicy.IGNORE)` while the
  documents are migrated.
  <!-- ADR-0587 -->
- `rich-text` and `run` are built-in node names. Registering an application
  factory under either throws `IllegalStateException`; rename the node, or call
  `replace` to shadow the built-in on purpose.
  <!-- ADR-0586, ADR-0592 -->

#### Icons

- The bundled icons are Lucide 1.54.0 (2026.2 had 0.469.0). `Icon.bundled` and
  `Icons.bind` throw `NoSuchElementException` for a removed or renamed name, and
  `Icon.find` answers empty. Removed: the brand marks `chrome`, `codepen`,
  `codesandbox`, `dribbble`, `facebook`, `figma`, `framer`, `github`, `gitlab`,
  `instagram`, `linkedin`, `pocket`, `slack`, `trello`, `twitch`, `twitter`,
  `youtube`, and `rail-symbol`; ship them as the application's own icons.
  Renamed, old name first: `album` → `square-bookmark`, `align-center` →
  `text-align-center`, `align-justify` → `text-align-justify`, `align-left` →
  `text-align-start`, `align-right` → `text-align-end`, `angry` → `face-angry`,
  `annoyed` → `face-expressionless`, `badge-help` → `badge-question-mark`,
  `book-marked` → `book-bookmark`, `building-2` → `building-complex`,
  `circle-help` → `circle-question-mark`, `file-audio` and `file-audio-2` →
  `file-headphone`, `file-badge-2` → `file-badge`, `file-check-2` →
  `file-check-corner`, `file-code-2` → `file-code-corner`, `file-json` →
  `file-braces`, `file-json-2` → `file-braces-corner`, `file-key-2` →
  `file-key`, `file-lock-2` → `file-lock`, `file-minus-2` → `file-minus-corner`,
  `file-plus-2` → `file-plus-corner`, `file-question` → `file-question-mark`,
  `file-search-2` → `file-search-corner`, `file-type-2` → `file-type-corner`,
  `file-video` → `file-play`, `file-video-2` → `file-video-camera`,
  `file-volume-2` → `file-signal`, `file-warning` → `file-exclamation-point`,
  `file-x-2` → `file-x-corner`, `filter` → `funnel`, `filter-x` → `funnel-x`,
  `fingerprint` → `fingerprint-pattern`, `flip-horizontal` →
  `square-centerline-dashed-horizontal`, `flip-horizontal-2` →
  `triangles-centerline-dashed-horizontal`, `flip-vertical` →
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
  `wrap-text` → `text-wrap`.
  <!-- commit 4ecfbfe8, no ADR -->

#### Native libraries

- `libgoldberry`'s ABI is 25 (2026.2's was 18), and this release's jars refuse a
  library built for an older one. Take `goldberry-natives` at the same version
  as the other artifacts (the BOM does). An application that loads its own build
  through `-Dgoldberry.native.library` builds it again from this release's
  sources, and a GraalVM native image is built again from matching jars.
  <!-- ADR-0562, ADR-0573, ADR-0580, ADR-0589, ADR-0615, ADR-0620, ADR-0639 -->
- `goldberry-media` refuses an `ffmpeg-<target>` jar built before this release,
  because the FFmpeg layout it checks has new fields. Take the `ffmpeg-<target>`
  jars at the same version as `goldberry-media`; an application's own FFmpeg
  build runs this release's layout probe again.
  <!-- ADR-0558, ADR-0599 -->
