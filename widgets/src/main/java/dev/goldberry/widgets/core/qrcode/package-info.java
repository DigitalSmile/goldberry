/// The `qr-code` widget: a QR code drawn from a string, at whatever size the
/// box gives it.
///
/// The encoder lives in `dev.goldberry.image.qr`; this package is the widget
/// that asks it for a module grid and paints the grid as squares, with the
/// standard's four-module quiet zone around it. Every reference is non-null
/// unless it says `@Nullable`.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#qr-code).
@NullMarked
package dev.goldberry.widgets.core.qrcode;

import org.jspecify.annotations.NullMarked;
