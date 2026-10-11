# ADR-0617: Transforms stay two-dimensional

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-45), `docs/css-support-2026-10-10.md`
  §7

## Context

`transform` here reads the 2D functions (`translate`, `scale`, `rotate`,
`skew`, `matrix`) into `css.value.Affine`, a 2D affine matrix, and the paint
path, the hit test, culling and the CPU raster all take that matrix.
`rotateX()`, `translate3d()`, `perspective`, `transform-style` and
`backface-visibility` are dropped or unknown.

A 4×4 matrix would reach every one of those readers. The CPU raster (Blend2D)
is 2D only and would have to draw a projected quad of an offscreen layer, and
the hit test would have to invert a projection. The interfaces the toolkit
draws have no use for depth beyond a card flip, which a `scale` animated through
`-1` already draws flat.

The user decided on 2026-10-11 to refuse 3D transforms permanently.

## Decision

**`Affine` stays 2D. The 3D transform functions and the 3D properties are
refused for good, with a reason, rather than flattened or approximated.**

- `rotateX()`, `rotateY()`, `rotate3d()`, `translate3d()`, `translateZ()`,
  `scale3d()`, `scaleZ()`, `matrix3d()` and `perspective()` drop the
  `transform` declaration with a warning in a lenient sheet, as they do now.
- `perspective`, `perspective-origin`, `transform-style` and
  `backface-visibility` stay unknown.
- Their conformance rows move from `CSS-45` to `by-design`.

## Consequences

- No flattening: a `rotateY(60deg)` is not drawn as a narrower box. A
  stylesheet that wrote one asked for depth, and a squashed box is not that.
- `translate`, `rotate` and `scale` as properties take two-dimensional values
  only, as they do since CSS-38.

## Status

Answered 2026-10-11. Nothing built.
