/// Rendering without a window: a painter or a widget tree into an
/// [Image][dev.goldberry.image.Image], a tree kept mounted and photographed
/// as its clock moves, and a tree driven by clicks and keys through a
/// [Session].
///
/// **Its own package rather than part of `render`**, because the dependency would
/// run the wrong way. `render` is the backend SPI — what a platform
/// implements, underneath everything — and this composes the layers above it: the
/// element tree, the cascade, the render tree and the paint pipeline. A widget
/// renderer inside the backend package would point the toolkit's own layering at
/// itself.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Testing an application](https://goldberry.dev/docs/guide/testing.html#pictures).
@NullMarked
package dev.goldberry.offscreen;

import org.jspecify.annotations.NullMarked;
