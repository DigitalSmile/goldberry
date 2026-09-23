package io.github.digitalsmile.goldberry.media.codec;

/// Decoders an application brings: the one supported way to play a codec the
/// published natives do not build (`docs/goldberry-media.md` §5).
///
/// Found by [java.util.ServiceLoader] and consulted **before** the built-in
/// FFmpeg decoders, highest [#priority()] first. A module declares
/// `provides io.github.digitalsmile.goldberry.media.codec.DecoderProvider with …`.
/// The built-in decoders rank below every provider, whatever a provider answers
/// here.
///
/// Whoever ships a provider for a patented codec holds the licence for it.
/// Goldberry publishes only royalty-free natives and this interface.
///
/// ```java
/// public final class PlatformH264 implements DecoderProvider {
///     public String name() { return "platform-h264"; }
///     public boolean supports(DecoderRequest request) { return request.codec() == CodecId.H264; }
///     public Decoder open(DecoderRequest request) { return new MediaFoundationDecoder(request); }
/// }
/// ```
public interface DecoderProvider {

    /// A short name, for diagnostics and [io.github.digitalsmile.goldberry.media.MediaCapabilities].
    String name();

    /// Higher is asked first when two providers support a track.
    default int priority() {
        return 0;
    }

    /// Whether this provider can decode `request`: the codec, and its profile,
    /// size, bit depth or channel count as the provider requires.
    boolean supports(DecoderRequest request);

    /// Opens a decoder for `request`. Called only when [#supports] said yes.
    ///
    /// @throws RuntimeException when it cannot after all; the Engine asks the next
    ///                          provider, and the built-in decoders last
    Decoder open(DecoderRequest request);
}
