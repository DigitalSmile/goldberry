/// The operating system's own decoders, as Decoder SPI providers
/// (`docs/goldberry-media.md` §5).
///
/// [io.github.digitalsmile.goldberry.media.platform.PlatformDecoders] lists them
/// for an application that builds its player's providers itself; one that does
/// not gets them from `ServiceLoader`.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media.platform;

import org.jspecify.annotations.NullMarked;
