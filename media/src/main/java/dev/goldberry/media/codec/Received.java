package dev.goldberry.media.codec;

import java.util.Objects;

/// What [Decoder#receive] answers: a frame, a request for more input, or the end
/// of the stream.
///
/// ```java
/// switch (decoder.receive()) {
///     case Received.Decoded(var frame) -> present(frame);
///     case Received.NeedsInput _ -> decoder.send(queue.take());
///     case Received.Ended _ -> finished();
/// }
/// ```
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public sealed interface Received {

    /// The one [NeedsInput].
    Received NEEDS_INPUT = new NeedsInput();

    /// The one [Ended].
    Received ENDED = new Ended();

    /// A decoded frame, borrowed until the decoder's next call (see [Frame]).
    ///
    /// @param frame the frame
    record Decoded(Frame frame) implements Received {
        public Decoded {
            Objects.requireNonNull(frame, "frame");
        }
    }

    /// No frame until another packet is sent. FFmpeg's `EAGAIN`.
    record NeedsInput() implements Received {}

    /// Every frame has been received after [Decoder#sendEnd]. FFmpeg's `EOF`.
    record Ended() implements Received {}
}
