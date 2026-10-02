/// The QR code encoder (ISO/IEC 18004), and nothing that draws one.
///
/// `QrEncoder.encode(payload, level)` turns a string into a
/// [QrMatrix][dev.goldberry.image.qr.QrMatrix] of dark and light modules; the
/// `qr-code` widget draws one on screen, and an application that wants a code in
/// a PNG draws the matrix itself.
///
/// A specification with one right answer, the same kind of thing as
/// [dev.goldberry.image.gif.GifDecoder]: mode selection,
/// Reed–Solomon over GF(256), eight masks scored by the standard's own penalty
/// rules, and a grid of dark and light modules out the other end. It is here
/// rather than in the widget catalogue because nothing in it names a widget, and
/// an application that wants a code in a file rather than on screen should not
/// have to build a widget tree to get one. It sits under `image`, beside
/// `image.gif` and `image.png`, because it is one more format this toolkit owns
/// outright. Exported to applications.
///
/// `@NullMarked`, which puts this package under NullAway: every type is non-null
/// unless it says `@Nullable`, and the build fails on a violation.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#qr-code).
@NullMarked
package dev.goldberry.image.qr;

import org.jspecify.annotations.NullMarked;
