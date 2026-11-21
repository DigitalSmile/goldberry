package dev.goldberry.mic;

import java.util.Objects;

/// Where a [Recording] is.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public sealed interface RecordingStatus {

    /// Recording, and handing blocks over.
    record Active() implements RecordingStatus {}

    /// Stopped by [Recording#pause()]; [Recording#resume()] starts it again.
    record Paused() implements RecordingStatus {}

    /// The device can no longer record: it was unplugged, or the system took
    /// it. Final.
    ///
    /// @param reason what happened, for a person to read
    record Lost(String reason) implements RecordingStatus {
        public Lost {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /// Closed. Final.
    record Closed() implements RecordingStatus {}

    /// Whether nothing more will be recorded.
    default boolean isFinal() {
        return this instanceof Lost || this instanceof Closed;
    }
}
