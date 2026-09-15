# 323. An italic is a face, and the matrix closes

Date: 2026-09-15

## Status

Accepted. Closes the other half of `docs/gaps.md` G27, which
[ADR-0321](0321-a-rule-under-text-belongs-to-the-face.md) left open. Extends
[ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md) by one step, and takes
the same trade it took.

## Context

G27 asked for the three controls every board and document tool puts beside
**bold**: italic, underline, strikethrough. ADR-0321 built the last two and said
why the first was a different question:

> Italic is a *face*, and a face is a file … it adds a megabyte of outlines, a
> licence line and a golden-image sweep to a change that has nothing else in
> common with the one below.

That is the cost, not an argument against paying it. The argument for paying it is
that **nothing else can**. The entry is explicit:

> brd cannot add a face to a bundle it does not own, and the alternative available
> to an application — shearing the glyphs in the painter — is a *synthetic
> oblique*, which is a decision about type design rather than a workaround.

Inter's italic is drawn: a single-storey `a`, an `f` with a descending tail,
different advances. Shearing the upright is a different typeface from the one the
designer made, and the toolkit is the layer that gets to decide which typeface it
ships.

## Decision

**Two more files, so the matrix closes.**

| | upright | italic |
|---|---|---|
| 400 | `UI` | **`UI_ITALIC`** |
| 600 | `UI_STRONG` | **`UI_STRONG_ITALIC`** |

`extras/ttf/Inter-Italic.ttf` and `extras/ttf/Inter-SemiBoldItalic.ttf`, from the
release the manifest already pins and the archive already in the cache — the same
SHA-256, verified on the way in, so no new trust and no new network fact.

**Two rather than one, and that is the decision inside the decision.** A single
italic would leave `font-weight: 600; font-style: italic` resolving to "the nearest
of the three we shipped", and a design system acquiring a weight nobody chose is
exactly what ADR-0066 and §1.4's two-weight rule exist to prevent. 830 KB buys a
matrix with no wrong corner.

`BundledFont.Style` is the third thing that names a file, beside the family and the
weight, and `Typography` carries it — because an italic is a **face** and a
`text-decoration` is a **mark**, so one resolves where the font is chosen and the
other where the paragraph is drawn.

### Matching is CSS's order: family, then style, then weight

`BundledFont.of(family, weight, style)`. A family that has the style at another
weight uses it — the slant is what a reader was told to look for — and a family
with no such style falls back to the weight it was asked for, upright. `JetBrains
Mono` ships one face, so italic code stays upright code.

### `oblique` is refused, and visibly

CSS's `oblique` asks for a **slant**. Answering it with Inter's italic would answer
a different question; answering it with a shear would be a type-design decision
taken by a stylesheet. So `font-style: oblique` is dropped with the usual warning,
which is how a stylesheet learns that this toolkit does not synthesize one.

## Consequences

The jar grows by 830 KB, to about four megabytes of fonts. `docs/ARCHITECTURE.md`
§6.1 already states the trade embedding is: identical rendering everywhere, paid
for in bytes. This is the same trade at the same exchange rate.

Nothing renders differently until a stylesheet says `font-style: italic`. The faces
are opened lazily like every other — an application that never writes it never
parses one — so the cost of the two files is bytes on disk and nothing at runtime.

`§2`'s `link` widget is now unblocked twice over: ADR-0321 gave it the underline
and this gives emphasis in running text somewhere to go. It is still unwritten.

What is *not* here: a `font-synthesis` switch, and any of the other six weights in
the archive. Both are additions a specification would have to ask for first, which
is Principle 3's rule and the reason there are two weights rather than nine.

## Alternatives considered

**Instance the `wght`/`slnt` axes at runtime.** The general answer, and ADR-0066
costed it: symbols in *both* HarfBuzz and Blend2D, a new struct layout, three
export branches and a four-target CI run to prove them. It is the right change the
day an intermediate weight is specified, and it buys nothing today that two files
do not.

**One italic face at 400 only.** Half the bytes and a matrix with a wrong corner,
where semibold italic silently renders at 400 — or, worse, upright at 600. A
stylesheet cannot tell which it got.

**Shear the upright glyphs.** Free, available, and a different typeface. The gap
names this one and refuses it in the same sentence.

**`InterVariable-Italic.ttf` instead of two statics.** One file of 910 KB against
two of 415 — barely a saving, and it is a variable file whose `wght` axis nothing
can instance, so it would render at a single weight anyway. The statics are what
the upright side already ships.
