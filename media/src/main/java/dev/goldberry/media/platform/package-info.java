/// The operating system's own decoders, as Decoder SPI providers.
///
/// [dev.goldberry.media.platform.PlatformDecoders] lists them
/// for an application that builds its player's providers itself; one that does
/// not gets them from `ServiceLoader`.
///
/// Exported to every module. Null-marked.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.platform;

import org.jspecify.annotations.NullMarked;
