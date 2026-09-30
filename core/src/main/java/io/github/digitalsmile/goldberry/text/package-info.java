/// Text layout: where HarfBuzz's shaping meets Blend2D's rasterizer, and a
/// paragraph that wraps itself for Yoga (ADR-0034).
///
/// The two libraries know nothing of each other, and this package holds them to the
/// one thing they must agree on — the units a glyph position is in. A paragraph is
/// shaped once and re-wrapped at any width over the same glyphs, because Yoga asks
/// for its size several times per layout pass. The shaped run and the direction are
/// the toolkit's own values, so no `:natives` type crosses here (ADR-0282).
///
/// Fonts, editing, long documents, itemization, line flow and OpenType tables each
/// have a subpackage.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.text;

import org.jspecify.annotations.NullMarked;
