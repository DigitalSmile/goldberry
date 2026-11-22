package dev.goldberry.vector.decode;

import java.nio.ByteBuffer;

import dev.goldberry.image.ScalableImage;
import dev.goldberry.image.spi.VectorDecoder;
import dev.goldberry.vector.Svg;

/// SVG for `Image.decode`: the bytes parsed into an [Svg], `currentColor`
/// black, as a browser draws an SVG image.
///
/// The provider the module announces; nothing names it.
public final class SvgDecoder implements VectorDecoder {

    /// What the service loader calls.
    public SvgDecoder() {}

    @Override
    public ScalableImage decode(ByteBuffer bytes) {
        return Svg.parse(bytes);
    }
}
