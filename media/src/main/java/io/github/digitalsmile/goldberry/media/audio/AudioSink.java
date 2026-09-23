package io.github.digitalsmile.goldberry.media.audio;

import java.lang.foreign.MemorySegment;

/// Where decoded audio goes: the audio device, or a test.
///
/// The seam between the Engine's audio thread and the output. The desktop one is
/// an SDL audio stream (SDL is already in `libgoldberry`). A test gives the
/// Engine a sink that captures samples and plays them in no time, which is what
/// makes the Engine's behaviour deterministic without a sound card.
///
/// **The sink is the audio clock** (`docs/goldberry-media.md` §3, "Master
/// clock"). The Engine knows the presentation time of the last sample it
/// wrote. [#queuedSamples()] says how many of them have not been heard yet. The
/// difference is what is playing now.
///
/// Called from the audio thread, except [#setGain], [#pause] and [#resume], which
/// may come from any thread.
public interface AudioSink extends AutoCloseable {

    /// Opens the output, asking for `preferred`.
    ///
    /// @return the format the sink actually takes, which the Engine converts to
    AudioFormat open(AudioFormat preferred);

    /// Queues `samples` samples per channel of interleaved f32 from `data`. Does
    /// not block. The Engine keeps [#queuedSamples()] near its own target.
    void write(MemorySegment data, int samples);

    /// Samples per channel written and not yet played.
    long queuedSamples();

    /// Drops everything queued: the Engine seeked.
    void clear();

    /// Stops playing, keeping what is queued.
    void pause();

    /// Plays again.
    void resume();

    /// Linear gain, 0 for silence and 1 for as decoded.
    void setGain(float gain);

    /// Closes the output.
    @Override
    void close();
}
