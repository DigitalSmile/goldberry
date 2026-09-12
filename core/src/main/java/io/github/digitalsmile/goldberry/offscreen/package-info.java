/// Rendering without a window: a painter or a widget tree into an
/// [Image][io.github.digitalsmile.goldberry.image.Image].
///
/// **Its own package rather than part of `render`**, because the dependency runs
/// the wrong way (ADR-0284). `render` is the backend SPI — what a platform
/// implements, underneath everything — and this composes the layers above it: the
/// element tree, the cascade, the render tree and the paint pipeline. A widget
/// renderer inside the backend package would point the toolkit's own layering at
/// itself.
///
/// `@NullMarked`, which puts this package under NullAway.
@NullMarked
package io.github.digitalsmile.goldberry.offscreen;

import org.jspecify.annotations.NullMarked;
