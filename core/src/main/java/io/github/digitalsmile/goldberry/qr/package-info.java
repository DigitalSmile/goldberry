/// ISO/IEC 18004 — the QR code encoder, and nothing that draws one.
///
/// A specification with one right answer, the same kind of thing as
/// [io.github.digitalsmile.goldberry.image.gif.GifDecoder]: mode selection,
/// Reed–Solomon over GF(256), eight masks scored by the standard's own penalty
/// rules, and a grid of dark and light modules out the other end. It is here
/// rather than in the widget catalog because nothing in it names a widget, and
/// an application that wants a code in a PNG rather than on screen should not
/// have to build a widget tree to get one (ADR-0391).
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Inside a marked package every type is non-null unless it says `@Nullable`,
/// and the build fails on a violation. Packages are marked one at a time on
/// purpose: NullAway runs in `OnlyNullMarked` mode, so an unmarked package is
/// invisible to it and a marked one is checked from the moment it opts in
/// (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.qr;

import org.jspecify.annotations.NullMarked;
