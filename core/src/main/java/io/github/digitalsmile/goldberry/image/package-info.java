/// Decoded images: bytes that were a PNG, a JPEG or a QOI, and are pixels now.
///
/// **Its own package rather than a class in `paint`**, because three parts of the
/// toolkit want the same value and only one of them draws (ADR-0283).
/// [Frame][io.github.digitalsmile.goldberry.paint.Frame] draws one; an offscreen
/// render produces one; a clipboard carries one. `paint` already depends on
/// `render`, so an [Image][io.github.digitalsmile.goldberry.image.Image] living
/// in `paint` would have made `render.Clipboard` depend on the paint package to
/// name the thing it holds.
///
/// `@NullMarked`, which puts this package under NullAway — see
/// `docs/testing.md` §2.
@NullMarked
package io.github.digitalsmile.goldberry.image;

import org.jspecify.annotations.NullMarked;
