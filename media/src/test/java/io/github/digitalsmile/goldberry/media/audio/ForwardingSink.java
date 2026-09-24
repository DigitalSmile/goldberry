package io.github.digitalsmile.goldberry.media.audio;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/// A sink that hands every call to another: what a test overrides one method of
/// to watch the Engine use a [VirtualSink].
public class ForwardingSink implements AudioSink {

    private final AudioSink delegate;

    public ForwardingSink(AudioSink delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public AudioFormat open(AudioFormat preferred) {
        return delegate.open(preferred);
    }

    @Override
    public void write(MemorySegment data, int samples) {
        delegate.write(data, samples);
    }

    @Override
    public long queuedSamples() {
        return delegate.queuedSamples();
    }

    @Override
    public long latencyNanos() {
        return delegate.latencyNanos();
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public void pause() {
        delegate.pause();
    }

    @Override
    public void resume() {
        delegate.resume();
    }

    @Override
    public void setGain(float gain) {
        delegate.setGain(gain);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
