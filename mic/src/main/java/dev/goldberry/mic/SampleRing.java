package dev.goldberry.mic;

import java.nio.FloatBuffer;

/// The recorded frames a puller has not read yet, up to a capacity; when it is
/// full the oldest are dropped and counted.
final class SampleRing {

    private final float[] samples;
    private final int channels;
    private final int capacity;
    private int start;
    private int size;
    private long dropped;

    SampleRing(int capacityFrames, int channels) {
        this.capacity = capacityFrames;
        this.channels = channels;
        this.samples = new float[capacityFrames * channels];
    }

    /// Adds the first `frames` frames of `from`, dropping the oldest held when
    /// there is no room.
    synchronized void write(FloatBuffer from, int frames) {
        var skip = Math.max(0, frames - capacity);
        if (skip > 0) {
            dropped += skip;
        }
        var over = size + (frames - skip) - capacity;
        if (over > 0) {
            start = (start + over) % capacity;
            size -= over;
            dropped += over;
        }
        for (var frame = skip; frame < frames; frame++) {
            var at = (start + size) % capacity;
            from.get(frame * channels, samples, at * channels, channels);
            size++;
        }
    }

    /// Moves up to as many whole frames as fit in `into` out, oldest first.
    synchronized int read(FloatBuffer into) {
        var frames = Math.min(size, into.remaining() / channels);
        for (var frame = 0; frame < frames; frame++) {
            into.put(samples, start * channels, channels);
            start = (start + 1) % capacity;
        }
        size -= frames;
        return frames;
    }

    synchronized int available() {
        return size;
    }

    synchronized long dropped() {
        return dropped;
    }

    synchronized void clear() {
        start = 0;
        size = 0;
    }
}
