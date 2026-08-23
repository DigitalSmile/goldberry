# 172. A package is a role, and the module is the fence

Date: 2026-08-23

## Status

Accepted. Completes the package move begun for `backend` → `render` and
`layout` → `paint`, and extends it across `:core`, `:natives` and `:widgets`.
Relates to `docs/ARCHITECTURE.md` §2, §3.1 and §15.

## Context

`:widgets` had already learned this lesson. [ADR-0091](0091-one-module-a-package-per-control.md)
split it by group, and [ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)
gave each control a package of its own so that a `slider-thumb` is invisible
outside `…controls.slider` — a boundary the compiler keeps rather than a
convention a reviewer has to notice. Thirty-nine packages, none of them large.

`:core` and `:natives` had not. Four packages carried a third of the toolkit:

| package | types |
| --- | --- |
| `…goldberry.css` | 23 |
| `…goldberry.backend` | 21 |
| `…natives.yoga` | 22 |
| `…natives.blend2d` | 20 |

A package that size is not a boundary, it is a folder. `CssTokenizer` and
`ComputedStyle` are at opposite ends of a pipeline and could see each other's
package-private members; `BlendCompOp`, which is a table of C constants, sat
beside `BlendContext`, which owns a native handle. The names said "CSS" and
"Blend2D" — the library the code came from — and said nothing about what any of
it *does*.

The move `backend` → `render` had already started answering that, and had
already found the cost: the first build of this branch was red, because
`BoxPainter.paintOne` and `FrameRing` had been package-private and their callers
had walked out of the package. That is the real question this ADR settles. Every
split turns some package-private call into a compile error, and there are only
two honest answers to one: the boundary is real and the member becomes public,
or the boundary is imaginary and the split was wrong.

## Decision

**A package is named for the part its contents play, not for the library or the
file they came from.** The CSS engine is a compiler, so it is
`css.parse` → `css.select` → `css.cascade` → `css.value`. Input is a dispatcher,
so it is what arrives (`input.event`), the vocabulary an accelerator is written
in (`input.key`), the snapshot it is routed against (`input.hit`) and the
interfaces a widget implements to hear any of it (`input.handler`). A native
library is split where the foreign memory stops: the wrappers that hold a handle
stay beside the binding class they are the only callers of, and the enums and
values, which touch no foreign memory at all, get packages of their own.

**When a split makes a package-private call illegal, the member becomes public
and says why.** Every promotion in this change carries a doc comment naming this
ADR. There are eleven of them across three modules, which is the number worth
recording: a split that needed thirty would have been the wrong split.

**Encapsulation that a package can no longer carry is carried by the module.**
`docs/ARCHITECTURE.md` §3.1 says a raw `MemorySegment` never leaves `:natives`,
and until now that was mostly enforced by `Blend2D` being package-private. It is
now enforced by the module descriptor and by a test that reads it
(`ExportedSurfaceTest`), which is the arrangement [ADR-0171](0171-a-column-is-an-x-and-a-width-arrives-late.md)
already reached for `…form.parts`: public in a package nothing can see.

**Where the split would leak internals into the public API, there is no split.**
Two candidates were tried and reverted, and they are in "Alternatives" below,
because a refactor that only records its successes is a refactor nobody can
argue with.

The result:

| module | packages before | after | largest package |
| --- | --- | --- | --- |
| `:core` | 15 | 35 | 10 |
| `:natives` | 7 | 15 | 12 |
| `:widgets` | 38 | 39 | 11 |

## Alternatives considered

**Split the root `…goldberry` package into an API half and a runtime half.**
This is the split the shape of the code suggests: `Goldberry`, `Application` and
`Host` are what an application writes against, and `Launcher` and
`GoldberryRuntime` are what runs it. It was rejected on a count. `Launcher` and
`GoldberryRuntime` make **21 calls** into `Window`'s package-private surface —
`handlePointerMoved`, `handleResize`, `handleCloseRequest`, `backendWindow`,
`frameRing`, and thirteen more — plus `Popup.handleKey`,
`Popup.dismissedByInput` and `Overlay.attached`. `Window` and `Popup` are types
an application holds, so every one of those would become public *API*: a
toolkit whose `Window` offers the application a `handlePointerMoved` has
published its own event loop by accident. Eleven promotions across the rest of
this change bought four packages; these twenty-one would buy one, and cost the
public surface. The ten types left in the root are one role — the running shell
— and are documented as such.

**Move `WidgetRenderer` and `FrameTrace` into `widget.render`.** Attempted, and
reverted the same hour. `WidgetRenderer` reads and writes `Element`'s style
cache through `cachedStyle`, `cacheStyle`, `stableStyle`, `isAnimating` and
`animations`, all package-private and all deliberately so — the cache protocol
is [ADR-0152](0152-the-cascade-looks-at-rules-that-could-match.md)'s and is not
something a second implementation is meant to exist for. The renderer is not a
neighbouring role; it is the element tree's own paint pass. It stays beside
`Element`.

**Make the raw binding classes public in `blend2d.ffm` / `yoga.ffm`, so that
every wrapper could move out.** This would have allowed `blend2d.font`,
`blend2d.image` and `blend2d.path` as separate packages. It was rejected because
it inverts the boundary: `Blend2D` and `Yoga` expose about two hundred methods
taking and returning `MemorySegment`, and making them public — even in an
unexported package — moves the fence from "one class in one package" to "one
line in `module-info`". The unexported-package trick is the right tool for
`…form.parts`, which is three small widgets; it is the wrong tool for the entire
FFM surface. So `MeasureCallback`, `MeasureProbe`, `SdlWindowHandle`,
`SdlEventBuffer` and `SdlEventWatch` were each moved out and then moved back the
moment they turned out to traffic in `MemorySegment`. Their packages are smaller
than they would have been, and the boundary is where §3.1 says it is.

**Leave `bind` alone, because the weaver writes its package name as a string.**
`ModelWeaver` builds ten `ClassDesc` constants from one `BIND_PACKAGE` prefix
and emits them into somebody else's class file. Splitting `bind` meant the
prefix became three, and nothing in the compiler would have caught getting that
wrong — the weave would succeed and a woven native image would fail to start,
much later, with a `NoClassDefFoundError` naming a package that no longer
exists. This was not a reason to leave `bind` alone; it was a hole. It is now
`WrittenNamesTest`, which reflects over the weaver's own constants and resolves
each one.

## Consequences

**A package name now tells you what its contents do.** `css.value` holds the
things a declaration resolves to; `render.event` holds the loop; `input.hit`
holds the snapshot. The pipeline in §5 of `ARCHITECTURE.md` can be read off the
package list, which was the point.

**Eleven members are public that were not.** They are:
`BoxPainter.paintOne`, `Frame.end`, `Frame.over` (replacing a package-private
constructor), `FrameRing` and its three recorder methods, `Transform.parse`,
`Transform.parseOrigin`, `Selector.PseudoClass.parse`, `BlendException`'s
constructor, `SdlException`'s constructor, and `Edge.isPhysicalSide`. Each says
why in its own doc comment. `Frame.end` is the one worth watching: it used to be
unreachable and is now merely *wrong* to call, so the frame enforces its own
lifetime — ending twice is a no-op, painting afterwards throws, and `FrameTest`
covers both.

**Two new architecture tests, and they were needed.** `ExportedSurfaceTest`
reads `:natives`' own descriptor and its own class files and fails if any
reachable member mentions `MemorySegment`; it was checked against a deliberate
break. `WrittenNamesTest` resolves every class name the weavers write as text.
Both are written to discover their subject rather than list it, so a package
added next month is checked next month.

**An import diff of about nine hundred lines, and a merge conflict for anything
in flight.** Unavoidable, and the reason this was done in eight commits that
each build and test green rather than one. The moves were made by
`tools/refactor/move_package.py`, which is kept: it does the four edits a package
move needs, and the fourth — the file left behind that used a type without an
import because it shared a package with it — is the one nobody does by hand.

**Two things this does not fix.** The root `…goldberry` package still holds ten
types, for the reason above; if `Window`'s event intake is ever separated from
`Window` itself, the split becomes cheap and should be revisited. And
`blend2d.enums` is named after a Java construct rather than a role, which is the
one place this ADR does not follow its own rule — the honest description of its
contents is "the enums, which map a C constant to a Java name and touch no
foreign memory", and no shorter name says that.
