package io.github.digitalsmile.goldberry.media.audio;

import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.natives.sdl.audio.SdlAudioStream;

/// The desktop's [AudioSink]: an SDL audio stream on the default playback device.
///
/// SDL is already inside `libgoldberry`, so this costs no second audio library
/// (`docs/goldberry-media.md` §3, "Audio decode"). The stream is fed interleaved
/// f32 at the rate and channel count the Engine asked for, and SDL converts to
/// whatever the device runs at. The format the Engine converts to is therefore
/// always the one it asked for, and the OS mixer, which runs at 48 kHz on every
/// desktop, does the last step.
///
/// The stream follows the default device: headphones plugged in mid-song take the
/// song with them.
///
/// ## A queue that drains smoothly
///
/// SDL's device takes samples in pulls, 1024 at a time at 48 kHz, so the raw
/// queue, and the audio clock made from it, moves in steps of 21 ms. A picture
/// timed against that clock is shown up to a step late, and a view that wakes when
/// the next picture falls due finds the clock not yet there and wakes again.
/// So between pulls [#queuedSamples()] reports the queue draining at the stream's
/// rate from the moment of the last pull, never by more than that pull took: what
/// the device is playing, rather than what it last fetched. ffplay corrects its
/// audio clock by the callback time for the same reason. Paused, the estimate
/// stands still, and a clear starts it over. At a [#setRate] other than 1 the
/// device takes samples that much faster, and the estimate drains that much
/// faster with it.
///
/// ## Latency
///
/// [#latencyNanos()] is how far behind that estimate the sound actually is
/// (ADR-0474), in two parts:
///
/// - **SDL's own buffers**, counted in pulls ([#pullsAhead]). The estimate above
///   drains the pull *after* the last one, so it runs one pull ahead of what SDL
///   has handed over by construction. On macOS SDL's CoreAudio backend then keeps
///   three AudioQueue buffers, the one playing and two waiting, and a pull fills
///   the one that has just finished: a sample is heard two pulls after SDL takes
///   it. Three pulls in all, 64 ms at 48 kHz. Elsewhere only the first is
///   counted, until SDL's WASAPI and PulseAudio backends are read as closely.
/// - **The operating system's**, from [OutputLatency]: the device, its safety
///   offset and its stream, where Bluetooth spends 150–250 ms. Asked every
///   [#REFRESH] from the audio thread, so a headset connected mid-song is in the
///   clock within a second.
public final class SdlAudioSink implements AudioSink {

    /// How often the system's latency is asked again: the default device can
    /// change under a playing stream, and SDL follows it.
    public static final Duration REFRESH = Duration.ofSeconds(1);

    /// The providers on the path, found once per process: a sink is made for
    /// every source opened, and a `ServiceLoader` scan each time is work for
    /// nothing.
    private static final class Installed {
        static final OutputLatency LATENCY = OutputLatency.installed();
    }

    private final OutputLatency outputLatency;
    private final int pullsAhead = pullsAhead(System.getProperty("os.name", ""));
    /// What the system last said, in nanoseconds; 0 when it said nothing.
    private long systemLatency;
    /// When the system was last asked, on [System#nanoTime()], or never.
    private long askedAt;
    private boolean asked;

    /// The slowest and fastest SDL's stream resamples to.
    static final float MIN_RATE = 0.01f;

    static final float MAX_RATE = 100f;

    private @Nullable SdlAudioStream stream;
    private float gain = 1f;
    private float rate = 1f;
    /// The raw queue as last seen, plus what has been written since.
    private long lastRaw;
    /// How many samples the last pull took: the most the estimate drains by.
    private long pull;
    /// When the last pull was seen, on [System#nanoTime()].
    private long pulledAt;
    /// While paused, how far into the current pull the device had got.
    private long pausedAfter = -1;
    private int sampleRate;

    /// A sink that opens nothing until [#open], and asks the installed
    /// [OutputLatency] providers for the system's latency.
    public SdlAudioSink() {
        this(Installed.LATENCY);
    }

    /// A sink that asks `outputLatency` for the system's latency: [OutputLatency#NONE]
    /// for one that counts SDL's buffers only.
    public SdlAudioSink(OutputLatency outputLatency) {
        this.outputLatency = Objects.requireNonNull(outputLatency, "outputLatency");
    }

    /// How many pulls behind the smoothed queue a sample is heard, before the
    /// system's latency: three on macOS, where SDL keeps three AudioQueue buffers,
    /// and elsewhere the one the smoothing runs ahead by (see the class note).
    ///
    /// @param osName the `os.name` property
    static int pullsAhead(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("mac") ? 3 : 1;
    }

    @Override
    public synchronized AudioFormat open(AudioFormat preferred) {
        Objects.requireNonNull(preferred, "preferred");
        if (stream != null) {
            throw new IllegalStateException("already open");
        }
        var opened = SdlAudioStream.open(preferred.sampleRate(), preferred.channels());
        opened.gain(gain);
        if (rate != 1f) {
            opened.frequencyRatio(rate);
        }
        opened.resume();
        stream = opened;
        sampleRate = preferred.sampleRate();
        lastRaw = 0;
        pull = 0;
        pulledAt = System.nanoTime();
        pausedAfter = -1;
        asked = false;
        refreshLatency();
        return preferred;
    }

    /// Under the lock with the bookkeeping, so a reading between the put and the
    /// count cannot mistake the new samples for a pull.
    @Override
    public synchronized void write(MemorySegment data, int samples) {
        var bytes = (long) samples * stream().channels() * Float.BYTES;
        stream().put(data.asSlice(0, bytes).asByteBuffer());
        lastRaw += samples;
        refreshLatency();
    }

    /// SDL's buffers, as pulls of the size last seen, and the system's latency as
    /// last asked. See the class note.
    @Override
    public synchronized long latencyNanos() {
        if (stream == null || sampleRate == 0) {
            return 0;
        }
        var sdl = Math.round(pullsAhead * (double) pull * 1e9 / (sampleRate * (double) rate));
        return sdl + systemLatency;
    }

    /// Asks the system again if [#REFRESH] has passed. Called with the monitor
    /// held, from the audio thread: a few system calls, once a second.
    private void refreshLatency() {
        var now = System.nanoTime();
        if (asked && now - askedAt < REFRESH.toNanos()) {
            return;
        }
        asked = true;
        askedAt = now;
        systemLatency = outputLatency.defaultOutput().map(Duration::toNanos).orElse(0L);
    }

    /// The samples written and not yet played, drained smoothly between the
    /// device's pulls (see the class note).
    @Override
    public synchronized long queuedSamples() {
        if (stream == null) {
            return 0;
        }
        var raw = stream.queuedFrames();
        var now = System.nanoTime();
        if (raw < lastRaw) {
            pull = lastRaw - raw;
            pulledAt = now;
        }
        lastRaw = raw;
        var after = pausedAfter >= 0 ? pausedAfter : now - pulledAt;
        var drained = Math.min(Math.round(after * (double) sampleRate * rate / 1e9), pull);
        return Math.max(raw - drained, 0);
    }

    @Override
    public synchronized void clear() {
        stream().clear();
        lastRaw = 0;
        pull = 0;
        pulledAt = System.nanoTime();
        if (pausedAfter >= 0) {
            pausedAfter = 0;
        }
    }

    @Override
    public synchronized void pause() {
        if (stream != null) {
            stream.pause();
            if (pausedAfter < 0) {
                pausedAfter = System.nanoTime() - pulledAt;
            }
        }
    }

    @Override
    public synchronized void resume() {
        if (stream != null) {
            stream.resume();
            if (pausedAfter >= 0) {
                pulledAt = System.nanoTime() - pausedAfter;
                pausedAfter = -1;
            }
        }
    }

    @Override
    public synchronized void setGain(float gain) {
        this.gain = gain;
        if (stream != null) {
            stream.gain(gain);
        }
    }

    /// Resamples in SDL's stream, so the pitch moves with the speed. The estimate
    /// between pulls keeps the part of the current pull it has already drained,
    /// and drains the rest at the new rate.
    @Override
    public synchronized boolean setRate(float rate) {
        if (!(rate >= MIN_RATE && rate <= MAX_RATE)) {
            return false;
        }
        if (stream != null) {
            stream.frequencyRatio(rate);
            var scale = this.rate / rate;
            if (pausedAfter >= 0) {
                pausedAfter = Math.round(pausedAfter * (double) scale);
            } else {
                var now = System.nanoTime();
                pulledAt = now - Math.round((now - pulledAt) * (double) scale);
            }
        }
        this.rate = rate;
        return true;
    }

    @Override
    public synchronized void close() {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    private synchronized @Nullable SdlAudioStream current() {
        return stream;
    }

    private SdlAudioStream stream() {
        var current = current();
        if (current == null) {
            throw new IllegalStateException("the sink is not open");
        }
        return current;
    }
}
