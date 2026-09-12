# 280. `:natives` exports to `:core`, and to nobody else

Date: 2026-09-12

## Status

Accepted, and **partial** — deliberately. Yoga's three packages are sealed;
Blend2D's and HarfBuzz's are not yet, and the reason is written down below rather
than left as an absence.

## Context

`docs/ARCHITECTURE.md` §3.1 states one boundary rule and `ExportedSurfaceTest`
enforces it: a raw `MemorySegment` never leaves `:natives`. That rule has held.

There is a second rule, which nobody wrote down: **no `:natives` type appears in
a signature an application can read.** It was broken in three families, and the
reason it could be broken at all is four words in a module descriptor:

```
:core     requires transitive io.github.digitalsmile.goldberry.natives
:natives  exports …blend2d, …yoga, …harfbuzz   (unqualified)
:widgets  requires transitive io.github.digitalsmile.goldberry.core
```

An application requiring `:widgets` therefore read `:natives`, and did.
[ADR-0277](0277-a-path-is-a-value-and-the-rasterizers-is-package-private.md)
closed the drawing family and
[ADR-0279](0279-flexbox-is-the-toolkits-vocabulary-not-yogas.md) the layout one.
This is the descriptor change that makes those closures **enforced** rather than
merely achieved.

## Decision

```java
exports io.github.digitalsmile.goldberry.natives.yoga to
        io.github.digitalsmile.goldberry.core;
exports io.github.digitalsmile.goldberry.natives.yoga.style to
        io.github.digitalsmile.goldberry.core;
exports io.github.digitalsmile.goldberry.natives.yoga.measure to
        io.github.digitalsmile.goldberry.core;
```

A module outside `:core` that names `StyleLength` is now refused by javac —
*"package … is declared in module io.github.digitalsmile.goldberry.natives, which
does not export it"* — which was checked against a scratch compilation rather
than assumed.

## What is not sealed, and why it is recorded here

`blend2d`, `blend2d.enums`, `harfbuzz`, `harfbuzz.enums`, the four `sdl` packages
and `natives.blend2d`'s font types stay unqualified, and `:core` keeps `requires
transitive`. Three APIs in the text stack still name a `:natives` type in an
exported signature:

| | names | called from |
|---|---|---|
| `Font.shape` / `Font.draw` / `Font.widthOf` | `GlyphRun`, `TextDirection` | `text.Paragraph` |
| `Paragraph.glyphs()` | `GlyphRun` | nothing |
| `Frame.drawGlyphs` | `BlendFont`, `BlendGlyphBuffer` | `text.font.Font` |

**None of the three has a consumer outside `:core`.** Two of them are value-shaped
and as mirrorable as the layout family was — `GlyphRun` is six `int[]` and nothing
else, with no native memory and no lifetime. The third is not: `Frame.drawGlyphs`
takes two native *handles*, and the question of where the text/paint seam should
sit is a design decision rather than a transcription, so it gets its own ADR
rather than being improvised at the end of this one.

## `-Xlint:exports` is the test that was going to be written

The plan for this ADR included a `PublicSurfaceTest` in `:core`: read the module's
own descriptor, walk every exported package's public members, fail if any
signature names `io.github.digitalsmile.goldberry.natives` — the shape
`ExportedSurfaceTest` uses for the `MemorySegment` rule.

It was not written, because the compiler already does it and does it better. With
`requires transitive` removed, `-Xlint:exports` under `-Werror` named all eleven
remaining sites, by file and line, in one build. A hand-written test would have
been a slower copy with its own reflection bugs.

So the enforcement mechanism for this rule is **the word `transitive`**: while it
is absent the compiler refuses any leak, and the eleven warnings above are the
to-do list. It is currently present, and the comment in `core/module-info.java`
says exactly why and what to do about it.

## Consequences

- **A qualified export "upward" warns, and `-Werror` rejects it.** `:core` cannot
  be on `:natives`' compile module path — the dependency runs the other way — so
  javac reports *module not found* for every `exports … to`. The fix is
  `@SuppressWarnings("module")` on the module declaration itself, which is
  narrower than the `-Xlint:-module` the build file would otherwise have needed:
  it covers this descriptor's twelve directives and nothing else in the project.
- **`:gpu` needed no qualification.** It has zero `natives` references, which was
  checked rather than assumed.
- **Test source sets are unaffected.** They run on the classpath, not the module
  path, so the descriptor does not bind them — which is right: a test should be
  able to reach past a boundary to check it. The boundary is enforced where it
  matters, at `main` compilation, and by the scratch-module probe.
- **The seal is reversible by one word**, and that is the risk: adding
  `transitive` back, or an unqualified `exports`, would reopen it silently. The
  compiler catches the first of those the moment a leak appears; nothing catches
  the second, and `BoundaryTest` in `:widgets` is where a rule about it would go
  when the text family lands.

## Alternatives considered

- **Seal everything now and mirror the text types in the same change.** Two of
  the three are easy; the third is a seam question, and answering it badly in a
  hurry would be worse than one more ADR.
- **Leave `requires transitive` and rely on the arch test alone.** A test that
  scans for imports catches a leak after it is written. The descriptor prevents
  it from compiling.
- **`-Xlint:-module` in `goldberry.java-conventions.gradle`.** Switches the lint
  off for every module in the project to quiet twelve directives in one.
- **An `io.github.digitalsmile.goldberry.internal` package exported to nothing.**
  Java exports by package and reads by module; a public method of an exported type
  may not name a type from an unexported one without the same warning. It moves
  the problem rather than solving it.
