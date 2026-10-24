# Third-party notices

Goldberry is licensed under the [Apache License 2.0](LICENSE). It bundles
third-party software and assets that carry their own licences, listed here.

Every entry has a corresponding file in [`licenses/`](licenses/).
`./gradlew checkLicenses` verifies that this document and that directory agree.

> **Status.** All of these are bundled. The superbuild links seven upstreams into
> `libgoldberry`, the fonts and the icon set are compiled into the published jars,
> and every file in `licenses/` holds the verbatim upstream text copied from the
> revision actually vendored — the `NOT-VENDORED` markers this document was
> written around are gone (`docs/releasing.md`, 2026-09-17).
> `./gradlew checkLicenses -Pgoldberry.releaseCheck=true` is what says so.

## Statically linked into `libgoldberry`

These matter most. `docs/ARCHITECTURE.md` §3.2 links every native dependency
**statically** into one shared library, so their compiled code is present in
every binary artifact Goldberry publishes. That is a redistribution in object
form, and the MIT-licensed components below require their copyright and
permission notices to travel with it.

| Component | Licence | Upstream | Notes |
|---|---|---|---|
| [Blend2D](licenses/blend2d.txt) | Zlib | <https://blend2d.com> | 2D rasterization |
| [AsmJit](licenses/asmjit.txt) | Zlib | <https://asmjit.com> | JIT backend, pulled in by Blend2D |
| [SDL3](licenses/sdl3.txt) | Zlib | <https://www.libsdl.org> | Windowing, input, DPI, GPU |
| [Yoga](licenses/yoga.txt) | MIT | <https://www.yogalayout.dev> | Flexbox layout |
| [HarfBuzz](licenses/harfbuzz.txt) | MIT ("Old MIT") | <https://harfbuzz.github.io> | Text shaping |
| [md4c](licenses/md4c.txt) | MIT | <https://github.com/mity/md4c> | Markdown parser and the HTML5 entity table, behind `goldberry-html` |
| [libwebp](licenses/libwebp.txt) | BSD-3-Clause | <https://github.com/webmproject/libwebp> | WebP decoding — the `webpdecoder` target only, no encoder and no animation demuxer |
| [webview/webview](licenses/webview.txt) | MIT | <https://github.com/webview/webview> | the `web-view` widget, behind the **separate and optional** `libgoldberry-webview` |

webview/webview is the only entry here that is not inside `libgoldberry`, and
the only one an installation may legitimately not have. It also brings **no
engine of its own**: what renders a page is the WebKitGTK, WebView2 or WKWebView
already on the user's machine, under whatever licence their operating system
ships it under. Nothing of those is vendored, linked or redistributed here, so
this row covers one MIT header and the shim compiled from it.

libwebp is the only BSD-3-Clause component here, and it is the strictest of the
three licences in this table: the copyright notice, the conditions **and** the
disclaimer have to travel with a binary redistribution, and the name of the
copyright holder may not be used to endorse anything derived from it. Its notice
file therefore has to be vendored verbatim like HarfBuzz's rather than
paraphrased.

Zlib imposes no notice requirement on binary distribution — only that the origin
is not misrepresented and altered *source* is marked. The notices are included
regardless, because splitting the disclosure by what is strictly compulsory
makes it harder to audit and saves nothing.

HarfBuzz is a special case: it does **not** use the standard MIT text but its
own "Old MIT" licence, with a long accumulated list of copyright holders. It has
to be copied verbatim; substituting standard MIT would misstate it.

md4c is here rather than in a notice file of its own, which is a departure from
`docs/ARCHITECTURE.md` §11.1's rule that an optional content module carries its
own `THIRD-PARTY-NOTICES`. It is compiled **into `libgoldberry`** rather than
into a native of that module's own — one MIT C file of tens of kilobytes, against
the four CI legs a second superbuild would cost — so it is redistributed in the
same artifact as the four above and is disclosed beside them. `html-view`
was built without an engine under it — the HTML parser is Java — so no second
native library and no second notice file were needed after all.

## Statically linked into `libgoldberry-media`, in `goldberry-media`'s `natives-<platform>` classifiers

The media engine's decoders and its colour conversion, in one shared library
of their own. They are **not** in `libgoldberry` and not in any artifact an
application gets without asking: they ship in the `natives-<platform>`
classifier jars of the optional `goldberry-media`, which an application adds
by name. The demuxers, the clock and the I/O are Java and link nothing.

| Component | Licence | Upstream | Notes |
|---|---|---|---|
| [dav1d](licenses/dav1d.txt) | BSD-2-Clause | <https://code.videolan.org/videolan/dav1d> | AV1 decoder |
| [libvpx](licenses/libvpx.txt) | BSD-3-Clause | <https://chromium.googlesource.com/webm/libvpx> | VP9 and VP8 decoders; the encoders are not built |
| [Opus](licenses/opus.txt) | BSD-3-Clause | <https://opus-codec.org> | Opus decoder; the machine-learning extensions are off |
| [libogg](licenses/libogg.txt) | BSD-3-Clause | <https://xiph.org/ogg> | libvorbis's bit reader and packet type; Ogg framing is read in Java |
| [libvorbis](licenses/libvorbis.txt) | BSD-3-Clause | <https://xiph.org/vorbis> | Vorbis decoder, the synthesis half |
| [FLAC](licenses/FLAC.txt) | BSD-3-Clause | <https://xiph.org/flac> | libFLAC's stream decoder; the GPL tools are not built |
| [libyuv](licenses/libyuv.txt) | BSD-3-Clause | <https://chromium.googlesource.com/libyuv/libyuv> | YUV to BGRA conversion and scaling on the CPU |
| [minimp3](licenses/minimp3.txt) | CC0-1.0 | <https://github.com/lieff/minimp3> | MP3 decoder, a header instantiated once |

Nothing here is under a copyleft licence. Every upstream asks only that its
notice travel with a binary redistribution: `licenses/` carries each text, and
every natives jar carries a `media-NOTICE.txt` under `META-INF/` naming the
eight at the tags and commits that were built. An application that ships
those classifier jars owes its users those notices and nothing more; it owes
no patent review for codecs, because none from a patent pool is built, and
libvpx's and libyuv's own patent grants are reproduced in their files.

### The platform decoders bundle nothing

`goldberry-media`'s system decoders (once the separate `goldberry-media-platform`
module, now part of `goldberry-media`) decode the patent-pool codecs
the published natives leave out — H.264, HEVC, AAC, AC-3 and E-AC-3 — by calling the
decoders the operating system already has: VideoToolbox, AudioToolbox and Core
Audio on macOS, the GStreamer libraries and plugins a Linux distribution
installs, and Media Foundation on Windows. They are bound with FFM from Java;
the jar contains no native code, no headers and no copy of any of them, so they
have no row above and no file in `licenses/`. They are used under the licences
they were installed under, and the codec licences are the operating system
vendor's or the distribution's.

## Embedded in the published jars

| Component | Licence | Upstream | Notes |
|---|---|---|---|
| [Inter](licenses/inter.txt) | OFL 1.1 | <https://rsms.me/inter/> | Default UI font |
| [JetBrains Mono](licenses/jetbrains-mono.txt) | OFL 1.1 | <https://www.jetbrains.com/lp/mono/> | Default monospace font |
| [Lucide](licenses/lucide.txt) | ISC, with MIT for the icons derived from Feather | <https://lucide.dev> | Icon set, compiled to a binary path table. The licence file lists the Feather-derived icons by name |
| [Noto Color Emoji](licenses/noto-emoji.txt) | OFL 1.1 | <https://github.com/googlefonts/noto-emoji> | Emoji font — in **`goldberry-emoji`** and not in core, see below |

### The emoji face is opt-in, and asks for nothing on screen

**It ships in `goldberry-emoji`, not in `goldberry-core`**: five megabytes of paint graphs is a lot for an application that never
draws an emoji to inherit.

What ships is Google's **COLRv1** build of Noto Color Emoji (`Noto-COLRv1.ttf`,
release v2.057), **unmodified** — the toolkit draws its paint graphs itself and
does not re-theme, subset or rename the file. It is under the SIL Open Font
License 1.1, like Inter and JetBrains Mono, so the obligations are theirs: the
licence text travels with the font (`licenses/noto-emoji.txt`, and `NOTICE`),
and a *modified* version could not be distributed under the reserved name
"Noto". `NotoColorEmojiFont.CREDIT` exists for an application that wants to name
the face in an About box; the licence does not require it.

**This replaced OpenMoji** (CC BY-SA 4.0), whose share-alike and on-screen
attribution obligations were why the emoji face became an artifact of its own.
Those obligations no longer apply to anything Goldberry ships.

## Compiled into the showcase

The showcase (`:example`) is an application rather than a library, and nothing
here reaches a toolkit artifact. It groups its Icons and Emoji sheets by
category, from two tables compiled at build time (`PrepareCatalogs`):

| Component | Licence | Upstream | Notes |
|---|---|---|---|
| Lucide icon metadata | ISC | <https://lucide.dev> | Each icon's categories, from the same pinned archive as the icons ([licence](licenses/lucide.txt)) |
| [Unicode emoji data](licenses/unicode.txt) | Unicode License v3 | <https://unicode.org/Public/18.0.0/emoji/emoji-test.txt> | Each emoji's group, compiled from `emoji-test.txt` 18.0 |

## Not distributed

Build- and test-time only. These never reach a published artifact and are listed
for completeness, not obligation.

| Component | Licence | Used for |
|---|---|---|
| JUnit 5/6 | EPL 2.0 | Tests |
| Gradle | Apache 2.0 | Build |
| CMake, Ninja | BSD 3-Clause / Apache 2.0 | Native build |
| DirectXShaderCompiler (DXC) | University of Illinois/NCSA | Compiling `:gpu`'s HLSL to SPIR-V and DXIL (`:gpu:compileShaders`, `docs/gpu-plan.md` D7) |
| SPIRV-Cross | Apache 2.0 | Translating `:gpu`'s SPIR-V to MSL (the same task) |

## Adding a dependency

1. Add a row to the correct table above.
2. Add `licenses/<component>.txt` with the **verbatim** upstream licence file,
   copied from the revision actually vendored — not from a licence template, and
   not from memory. Copyright lines are part of the licence.
3. If it ships in a published artifact (linked, statically or dynamically, or
   embedded), add it to `NOTICE`. `NoticeDisclosureTest` in `build-logic`
   fails when a row of the first three tables is not named there.
4. Run `./gradlew checkLicenses`.
