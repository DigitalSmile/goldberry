package dev.goldberry.image.spi;

import java.nio.ByteBuffer;

import dev.goldberry.image.ScalableImage;

/// Reads SVG: the service `goldberry-vector` provides, and through which
/// [dev.goldberry.image.Image#decode(ByteBuffer)] draws an SVG.
///
/// ```java
/// module my.app {
///     requires dev.goldberry.vector;   // provides this; nothing else to do
/// }
/// ```
///
/// `:core` carries no SVG reader. A document is parsed and drawn by a native
/// library an application adds on purpose, so the decoder arrives as a service,
/// as the emoji face and spell checkers do. Without one, SVG bytes are an
/// [dev.goldberry.image.ImageDecodeException] naming the artifact to add.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
public interface VectorDecoder {

    /// Parses `bytes`, from their position to their limit, without moving it.
    ///
    /// @throws dev.goldberry.image.ImageDecodeException when they are not a
    ///                                                  document it can read
    /// @throws IllegalStateException                    when it cannot draw on
    ///                                                  this machine, naming why
    ScalableImage decode(ByteBuffer bytes);
}
