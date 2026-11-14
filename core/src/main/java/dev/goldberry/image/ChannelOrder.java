package dev.goldberry.image;

/// The order of a pixel's four bytes in memory, for
/// [Image#decodeInto(java.nio.ByteBuffer, ChannelOrder, java.util.function.Function)].
///
/// Both are premultiplied, eight bits a channel, alpha last: what a GPU texture
/// in `B8G8R8A8_UNORM` or `R8G8B8A8_UNORM` samples. Named by the bytes' order in
/// memory, not by a packed integer, so the same name means the same bytes on
/// every platform.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
public enum ChannelOrder {

    /// Blue, green, red, alpha: what every [Image] holds, and what a window
    /// presents. Decoding into it is a copy.
    BGRA,

    /// Red, green, blue, alpha: what most texture formats and image libraries
    /// expect. Red and blue are swapped while the pixels are copied, in the
    /// same pass.
    RGBA;

    /// Whether a pixel in this order has red and blue the other way round from
    /// an [Image]'s.
    boolean swapsRedAndBlue() {
        return this == RGBA;
    }
}
