package dev.goldberry.mic;

/// A microphone came or went, as [Microphones#onChange] tells it.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public sealed interface MicrophoneChange {

    /// The microphone concerned.
    Microphone microphone();

    /// It was plugged in, or found.
    record Added(Microphone microphone) implements MicrophoneChange {}

    /// It went away. A recording pinned to it is [RecordingStatus.Lost].
    record Removed(Microphone microphone) implements MicrophoneChange {}
}
