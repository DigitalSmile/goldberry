package dev.goldberry.image;

import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalSize;

/// A picture with no pixels of its own: a document drawn at whatever size it is
/// asked for. An SVG, once `goldberry-vector` is on the module path.
///
/// ```java
/// Image icon = Image.decode(svgBytes);          // a raster at its natural size
/// icon.scalable().ifPresent(document -> ...);   // and the document behind it
/// ```
///
/// An application rarely holds one directly. [Image#decode(byte[])] answers an
/// [Image] for SVG bytes as for any other format: a raster at the document's
/// natural size, carrying the document. Wherever that image is drawn, an
/// `image`, a CSS `background-image` or `border-image`, a picture in an HTML or
/// Markdown view, or a canvas painter's [dev.goldberry.paint.Frame#drawImage(Image,
/// double, double, double, double)], it is drawn from a raster made for the
/// device pixels it covers, so it is sharp at every size and every display
/// scale.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
public interface ScalableImage {

    /// The size the document gives itself, in logical pixels.
    LogicalSize naturalSize();

    /// The document drawn into a raster of exactly `size` pixels: premultiplied
    /// BGRA, as every image is. A size whose shape is not the document's own is
    /// a viewport the document places itself in, as a browser draws an SVG
    /// image of that size.
    ///
    /// Safe to call from any thread.
    ///
    /// @throws IllegalArgumentException when `size` is empty or larger than the
    ///                                  implementation draws
    /// @throws IllegalStateException    when the document has been released
    Image render(PhysicalSize size);
}
