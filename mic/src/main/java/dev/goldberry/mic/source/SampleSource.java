package dev.goldberry.mic.source;

/// Somewhere samples come from: a microphone's recording, or a
/// [SyntheticSource] that makes them, which is what a test and a golden image
/// draw from. The analysis and the widgets read this, and do not know which.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public interface SampleSource {

    /// The rate and channel count of every block.
    SampleFormat format();

    /// Tells `listener` about every block from now on, until the subscription
    /// is closed.
    ///
    /// @return what removes the listener
    AutoCloseable onSamples(SampleListener listener);
}
