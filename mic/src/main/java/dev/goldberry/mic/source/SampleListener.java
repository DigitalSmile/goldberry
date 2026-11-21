package dev.goldberry.mic.source;

/// Hears each block a [SampleSource] delivers, on the source's own thread.
///
/// Called in order, one block at a time, and expected to be quick: a listener
/// that takes longer than a block lasts makes the source fall behind. What it
/// throws is logged and the source carries on.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
@FunctionalInterface
public interface SampleListener {

    /// One block, borrowed for the duration of the call.
    void onSamples(SampleBlock block);
}
