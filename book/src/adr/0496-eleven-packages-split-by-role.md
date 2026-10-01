# 496. Eleven packages split by role

Date: 2026-09-30

## Status

Accepted. Applies [ADR-0172](0172-a-package-is-a-role-and-the-module-is-the-fence.md)
again, a month and six modules later.

## Context

ADR-0172 split `:core`, `:natives` and `:widgets` by role, but the modules
built since then (`:gpu`, `:media`, the showcase) had grown their own folders.
An audit went over every main package, 215 of them. It started from the
largest and the ones that mixed roles, and counted for each candidate split
the package-private members it would break. The count came from CodeGraph's
edge table cross-checked by grep, and the compiler had the last word. The
rule was ADR-0172's. A split that needs more than about three promotions is
probably the wrong split. A split that would leak internals into an exported
package is not made.

## Decision

Eleven moves, made with `tools/refactor/move_package.py`:

| Module | From | To | Types | Promotions |
| --- | --- | --- | --- | --- |
| `:gpu` | `gpu.render` | `gpu.composite` | `SdlCompositor`, `SdlCompositedWindow`, `SdlReadbackSurface`, `UiComposite`, `LayerTextures`, `DeviceOptions`, `ApiAccess` and their 7 tests | none |
| `:natives` | `sdl.gpu` | `sdl.gpu.enums` | the 17 `SdlGpu…` enumerations | `SdlGpuShaderFormat.createProperty()` |
| `:example` | `example.ui` | `example.ui.sheet` | the Icons and Emoji screens, tiles, catalogs and specimens, and 2 tests | none |
| `:example` | `example.ui` | `example.media` | `ShowcaseMedia`, `ShowcaseServer`, `JavaPcmDecoder` and 1 test | none |
| `:widgets` | `widgets.data` | `widgets.data.plot` | `Scale`, `Ticks`, `LogTicks`, `TimeTicks`, `Lttb`, `Gaps`, `Curves` and 7 tests | none |
| `:media` | `media` | `media.picture` | `Picture`, `VideoPicture`, `VideoPlanes`, `PictureForm` and 2 tests | none |
| `:media` | `media.view` | `media.view.gpu` | `GpuVideo`, `GpuVideoPresenter`, `VideoPresenter` and 3 tests | `VideoPresenter`, `GpuVideo`, `GpuVideo.available()`, `GpuVideo.presenter(…)` |
| `:core` | `paint` | `paint.stroke` | `Stroke`, `Cap`, `Join`, `Dash` | none |
| `:core` | `render` | `render.clipboard` | `Clipboard`, `UriList` and 1 test | none |
| `:widgets` | `widgets.core` | `widgets.core.presence` | `Phase`, `Departure` and 1 test | none |
| `:assets` | `assets` | `assets.prepare` | the whole build tool, and 4 tests | none |

The reasons, one line each:

- **`gpu.composite`.** `gpu.render`'s own package comment said it held the
  shaders and the quad arithmetic. The compositor, which claims a window,
  composites it and reads it back, is a different role. The new name mirrors
  the `render.composite` SPI in `:core` that it implements.
- **`sdl.gpu.enums`.** ADR-0172's rule: split where the foreign memory stops.
  These are tables of C constants beside the wrappers that own handles. The
  name is the one `blend2d.enums`, `harfbuzz.enums` and `md4c.enums` already
  use.
- **`example.ui.sheet`, `example.media`.** Forty-two types in one showcase
  package. The glyph sheets are a closed group. The media sources, a loopback
  server and a decoder are not UI at all.
- **`widgets.data.plot`.** The deterministic arithmetic between a series and a
  plot, which every chart shares and none owns. The widgets stay behind.
- **`media.picture`.** What a view draws, apart from the player that makes it.
- **`media.view.gpu`.** The only code that touches the optional `:gpu`. The
  package boundary makes that isolation visible, and `GpuVideoPresenter`, the
  one class that names `:gpu`'s types, stays package-private inside it. That
  took three promotions, all in an unexported package, which is at ADR-0172's
  limit and is recorded here for that reason.
- **`paint.stroke`, `render.clipboard`.** Values and an SPI that sat inside
  larger packages and used nothing package-private from them.
- **`widgets.core.presence`.** An enter/exit lifecycle that nine packages
  share. It is not a structural primitive like `column` or `row`.
- **`assets.prepare`.** The build tool shared its package name,
  `dev.goldberry.assets`, with `:core`'s exported runtime
  package, which is also a resource directory (ADR-0387). It was harmless only
  because the two never met on a module path.

Every new package has a `package-info.java`. All but `assets.prepare`, which is
in a build tool without NullAway, are `@NullMarked`. Four NullAway findings
surfaced in the showcase's sheets and were fixed: two `@Nullable` subscription
fields, and a map lookup rewritten as `computeIfAbsent`.

Three things the move tool did not do were done by hand:

- the `:gpu` service file
- the `:assets` `mainClass` strings in `:core`, `:emoji` and `:example`
- six test doc comments that `[linked]` a package-private test class now in
  the other package, which the tool had turned into imports. They are plain
  `code` mentions now.

The tool gained two fixes on the way. It skips `.claude/` (ADR-0494). It also
rewrites a qualified name with a type-use annotation in it,
`…widgets.core.@Nullable Phase`, which it used to leave behind.

## Consequences

- **Source-incompatible for anyone who imported the moved types** from
  `paint`, `render`, `widgets.core`, `widgets.data` or `media`. Nothing is
  released, and each new package is exported where the old one was.
- **The test suites pass after the moves**: `:core` 2823, `:widgets` 2893,
  `:media` 714, `:natives` 569, `:html` 296, `:example` 236 and the rest, with
  0 failures. The exception is `:gpu:gpuTest`, where `GpuLayerBackendTest`
  crashes in `VULKAN_DestroyDevice` on this machine's NVIDIA driver. That crash
  was recorded as found and left in ADR-0491's commit, before any of this.

## Alternatives considered

Eleven splits were looked at and not made. They are recorded because a
refactor that keeps only its successes cannot be argued with.

- **`gpu`'s root enums and specs into `gpu.spec`.** About 30 package-private
  `sdl()`/`of()` mappers would become public, putting `natives.sdl.gpu` types
  in the exported `gpu` API.
- **`media.ffi` into stages** (demux, decode, hardware, resample, convert,
  AVIO). About 60 view-accessor promotions. The views sit beside the one
  binding that calls them.
- **`media.ffi`'s loader** (`FfmpegLibraries`, `FfmpegLibrary`,
  `FfmpegPlatform`). It would need `Ffmpeg`'s constructor to be public, and
  that constructor bypasses the layout verification.
- **`paint`'s box painters.** It would publish `Frame`'s path-pool protocol
  and `Path.replayInto` in an exported package.
- **`paint`'s glyph painting.** It would make `Frame.drawGlyphs` public again,
  undoing ADR-0290.
- **`paint.canvas`** (`Painter`, `StyledPainter`, `CanvasStyle`). There are no
  promotions, but it makes a package cycle with `paint`.
- **`widgets.core.image`'s loader.** The internal sealed `ImageLoad` would
  become exported API.
- **`:core`'s root package, including `Placement` to `render.popup`.**
  ADR-0172 already rejected the root split (21 `Window` promotions).
  `Placement` is application-facing, while `render.popup` is the backend SPI.
- **`css`'s root.** There are no promotions, but `Stylesheet`, `ComputedStyle`
  and `Theme` have about 600 importers, and `css/` is a resource directory.
- **`example.ui`'s documents.** They load resources by relative name, and the
  paths appear in native-image metadata globs and `opens` rules.
- **`markdown.model` into block and inline.** It is one sealed AST, which is
  one role.
- **`SdlFileDialogs`/`SdlLog` into `sdl.dialog`/`sdl.log`.** They hold the
  arena and the upcall stubs. ADR-0287 put only the plain values in those
  packages, on purpose.

Left alone as already single-role: `natives.sdl.calls`, the Blend2D and Yoga
binding packages, one-control packages in `:widgets` (`scroll`, `image`,
`timeline`, `tabs`, `calendar`, `menu`, `slider`, `form.parts`, per
ADR-0091/0065), `layout`, `widget`, `stats`, `bind`, `media.codec`,
`media.io`, and the `:html`, `:common`, `:weaver`, `:emoji` and `build-logic`
packages.
