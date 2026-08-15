# Third-party notices

Goldberry is licensed under the [Apache License 2.0](LICENSE). It bundles
third-party software and assets that carry their own licences, listed here.

Every entry has a corresponding file in [`licenses/`](licenses/).
`./gradlew checkLicenses` verifies that this document and that directory agree.

> **Status.** None of these are bundled yet — no native library has been built
> and no font or icon set has been vendored. This document is the disclosure
> framework, written ahead of the assets so that adding one is a deliberate act
> with a licence entry attached rather than something noticed at release time.
> Each file in `licenses/` carries a `NOT-VENDORED` marker until its verbatim
> upstream text is copied in.

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
| [libxkbcommon](licenses/libxkbcommon.txt) | MIT | <https://xkbcommon.org> | Keyboard translation; **Linux artifacts only** |

Zlib imposes no notice requirement on binary distribution — only that the origin
is not misrepresented and altered *source* is marked. The notices are included
regardless, because splitting the disclosure by what is strictly compulsory
makes it harder to audit and saves nothing.

HarfBuzz is a special case: it does **not** use the standard MIT text but its
own "Old MIT" licence, with a long accumulated list of copyright holders. It has
to be copied verbatim; substituting standard MIT would misstate it.

## Embedded in the published jars

| Component | Licence | Upstream | Notes |
|---|---|---|---|
| [Inter](licenses/inter.txt) | OFL 1.1 | <https://rsms.me/inter/> | Default UI font |
| [JetBrains Mono](licenses/jetbrains-mono.txt) | OFL 1.1 | <https://www.jetbrains.com/lp/mono/> | Default monospace font |
| [Lucide](licenses/lucide.txt) | ISC | <https://lucide.dev> | Icon set, compiled to a binary path table |
| [OpenMoji](licenses/openmoji.txt) | **CC BY-SA 4.0** | <https://openmoji.org> | Emoji font — **modified**, see below |

### OpenMoji is the one that constrains us

§6.2 ships the COLRv0 colour variant with its CPAL palette re-themed toward
Nord. That makes it a **derivative work** of a share-alike asset, and three
obligations follow that no other component here imposes:

1. **Attribution**, including an explicit statement that changes were made.
   `NOTICE` and `licenses/openmoji.txt` carry it; any About dialog Goldberry
   ships must carry it too.
2. **Share-alike** — the re-themed font is licensed CC BY-SA 4.0 and must be
   distributed as such. It cannot be relicensed Apache-2.0.
3. **No additional restrictions** — Goldberry's own terms must not restrict what
   CC BY-SA permits for the font.

**Share-alike reaches the font, not the toolkit.** The font is an embedded
asset; CC BY-SA has no linking or combination clause of the kind copyleft
software licences use, so Goldberry's Java and native code stay Apache-2.0.

If that obligation is ever unwanted, the escape is to ship only the unmodified
monochrome variant, or to swap the emoji font for a permissively licensed one —
`docs/ARCHITECTURE.md` §6.1 already treats the emoji slot as one of exactly two
font slots, so it is a replaceable component rather than a structural commitment.

## Not distributed

Build- and test-time only. These never reach a published artifact and are listed
for completeness, not obligation.

| Component | Licence | Used for |
|---|---|---|
| JUnit 5/6 | EPL 2.0 | Tests |
| Gradle | Apache 2.0 | Build |
| CMake, Ninja, Meson | BSD 3-Clause / Apache 2.0 | Native build |

## Adding a dependency

1. Add a row to the correct table above.
2. Add `licenses/<component>.txt` with the **verbatim** upstream licence file,
   copied from the revision actually vendored — not from a licence template, and
   not from memory. Copyright lines are part of the licence.
3. If it is statically linked or embedded, add it to `NOTICE`.
4. Run `./gradlew checkLicenses`.
