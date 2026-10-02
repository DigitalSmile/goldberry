package dev.goldberry.media.codec;

/// A decoder for one track: packets in, frames out.
///
/// The same send/receive shape as FFmpeg's, because it is the one that fits every
/// codec: one packet may give no frame, one frame or several, and a codec with
/// reordering holds frames back until more packets or the end arrive. The loop the
/// Engine runs on its decode thread:
///
/// ```java
/// while (running) {
///     switch (decoder.receive()) {
///         case Received.Decoded(var frame) -> consume(frame);
///         case Received.NeedsInput _ -> { if (!decoder.send(next())) { /* full: receive first */ } }
///         case Received.Ended _ -> running = false;
///     }
/// }
/// ```
///
/// One thread uses a decoder at a time, the track's decode thread. A decoder that
/// throws from any method is dropped, and the Engine tries the next provider for
/// the track: the fallback ladder.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public interface Decoder extends AutoCloseable {

    /// Feeds one packet.
    ///
    /// @return false when the decoder cannot take it yet: [#receive] frames first
    ///         and send the same packet again. The packet is not consumed
    boolean send(Packet packet);

    /// Says that no more packets are coming. [#receive] then answers the frames it
    /// held back, and [Received#ENDED] after the last one.
    void sendEnd();

    /// The next decoded frame, if one is ready.
    Received receive();

    /// Drops every packet and frame in flight: the Engine seeked. The decoder is
    /// ready for packets from the new position, and a decoder that was drained is
    /// ready again.
    void flush();

    /// Frees the decoder. Frames it handed out are invalid after this.
    @Override
    void close();
}
