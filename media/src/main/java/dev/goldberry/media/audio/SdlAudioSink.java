package dev.goldberry.media.audio;

import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.audio.SdlAudioStream;

/// The desktop's [AudioSink]: an SDL audio stream on the default playback device.
///
/// SDL is already inside `libgoldberry`, so this costs no second audio library.
/// The stream is fed interleaved
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
/// rate: what the device is playing, rather than what it last fetched. ffplay
/// corrects its audio clock by the callback time for the same reason. The pulls
/// themselves come unevenly, so the drain is a [DrainEstimate]: a line at the
/// stream's rate steered toward where the pulls say the device is, which never
/// jumps, since a clock that jumps by a pull passes over a picture at 60 fps.
/// Paused, the estimate stands still, and a clear starts it over. At
/// a [#setRate] other than 1 the device takes samples that much faster, and the
/// estimate drains that much faster with it.
///
/// ## Latency
///
/// [#latencyNanos()] is how far behind that estimate the sound actually is, in
/// two parts:
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
///
/// ## No device
///
/// When SDL has no device to open, the sink plays into a [SilentAudioSink]
/// instead, and says so in the log. This happens on a headless
/// machine, or with an SDL built without a backend for the sound server. The
/// source plays on without sound, rather than failing as media it could not
/// play. Each [#open] tries the device again, so the next source opened after a
/// device appears is heard.
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
public final class SdlAudioSink implements AudioSink {

    private static final Logger LOG = Logs.of(SdlAudioSink.class);

    /// Whether this process has already warned that it plays without sound. Every
    /// source opened after the first says so at debug only, so a playlist on a
    /// headless machine logs one warning, not one per track.
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    /// Opens SDL's stream on the default device. A test hands in one that finds
    /// none.
    @FunctionalInterface
    interface Device {

        /// @throws SdlException when there is no device to open
        SdlAudioStream open(int sampleRate, int channels);
    }

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
    private final Device device;
    /// What times the silence when there is no device.
    private final LongSupplier silenceClock;
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
    /// Where the samples go instead of [#stream] when no device opened.
    private @Nullable SilentAudioSink silence;
    private float gain = 1f;
    private float rate = 1f;
    /// The raw queue as last seen, plus what has been written since.
    private long lastRaw;
    /// How far the device has got through what it took; made when opened.
    private @Nullable DrainEstimate drain;
    private int sampleRate;

    /// A sink that opens nothing until [#open], and asks the installed
    /// [OutputLatency] providers for the system's latency.
    public SdlAudioSink() {
        this(Installed.LATENCY);
    }

    /// A sink that asks `outputLatency` for the system's latency: [OutputLatency#NONE]
    /// for one that counts SDL's buffers only.
    public SdlAudioSink(OutputLatency outputLatency) {
        this(outputLatency, SdlAudioStream::open, System::nanoTime);
    }

    /// A sink that opens `device`, and times the silence it falls back to by
    /// `silenceClock`.
    SdlAudioSink(OutputLatency outputLatency, Device device, LongSupplier silenceClock) {
        this.outputLatency = Objects.requireNonNull(outputLatency, "outputLatency");
        this.device = Objects.requireNonNull(device, "device");
        this.silenceClock = Objects.requireNonNull(silenceClock, "silenceClock");
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
        if (stream != null || silence != null) {
            throw new IllegalStateException("already open");
        }
        SdlAudioStream opened;
        try {
            opened = device.open(preferred.sampleRate(), preferred.channels());
        } catch (SdlException e) {
            return openSilence(preferred, e);
        }
        opened.gain(gain);
        if (rate != 1f) {
            opened.frequencyRatio(rate);
        }
        opened.resume();
        stream = opened;
        sampleRate = preferred.sampleRate();
        lastRaw = 0;
        var estimate = new DrainEstimate(sampleRate, System.nanoTime());
        estimate.rate(rate, System.nanoTime());
        drain = estimate;
        asked = false;
        refreshLatency();
        return preferred;
    }

    /// Plays into silence, at the rate asked for so far, and logs why.
    private AudioFormat openSilence(AudioFormat preferred, SdlException cause) {
        var silent = new SilentAudioSink(silenceClock);
        silent.setRate(rate);
        var format = silent.open(preferred);
        silence = silent;
        if (WARNED.compareAndSet(false, true)) {
            LOG.warn("no audio device, so media plays without sound: {}", cause.getMessage());
        } else {
            LOG.debug("no audio device, so media plays without sound: {}", cause.getMessage());
        }
        return format;
    }

    /// Whether this sink plays into silence because no device opened.
    synchronized boolean silent() {
        return silence != null;
    }

    /// Under the lock with the bookkeeping, so a reading between the put and the
    /// count cannot mistake the new samples for a pull.
    @Override
    public synchronized void write(MemorySegment data, int samples) {
        if (silence != null) {
            silence.write(data, samples);
            return;
        }
        var bytes = (long) samples * stream().channels() * Float.BYTES;
        stream().put(data.asSlice(0, bytes).asByteBuffer());
        lastRaw += samples;
        refreshLatency();
    }

    /// SDL's buffers, as pulls of the size last seen, and the system's latency as
    /// last asked. See the class note.
    @Override
    public synchronized long latencyNanos() {
        var estimate = drain;
        if (stream == null || estimate == null || sampleRate == 0) {
            return 0;
        }
        var sdl = Math.round(pullsAhead * (double) estimate.typicalPull() * 1e9 / (sampleRate * (double) rate));
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
        if (silence != null) {
            return silence.queuedSamples();
        }
        var estimate = drain;
        if (stream == null || estimate == null) {
            return 0;
        }
        var raw = stream.queuedFrames();
        var now = System.nanoTime();
        if (raw < lastRaw) {
            estimate.pulled(lastRaw - raw, now);
        }
        lastRaw = raw;
        return Math.max(raw - estimate.drained(now), 0);
    }

    /// What SDL's stream holds, unsmoothed: the queue [#queuedSamples()] steers
    /// its estimate from, which steps once a pull. For the test that tells the
    /// two apart.
    synchronized long rawQueuedSamples() {
        if (silence != null) {
            return silence.queuedSamples();
        }
        var current = stream;
        return current == null ? 0 : current.queuedFrames();
    }

    @Override
    public synchronized void clear() {
        if (silence != null) {
            silence.clear();
            return;
        }
        stream().clear();
        lastRaw = 0;
        if (drain != null) {
            drain.reset(System.nanoTime());
        }
    }

    @Override
    public synchronized void pause() {
        if (silence != null) {
            silence.pause();
        } else if (stream != null) {
            stream.pause();
            if (drain != null) {
                drain.pause(System.nanoTime());
            }
        }
    }

    @Override
    public synchronized void resume() {
        if (silence != null) {
            silence.resume();
        } else if (stream != null) {
            stream.resume();
            if (drain != null) {
                drain.resume(System.nanoTime());
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
        if (silence != null) {
            silence.setRate(rate);
        } else if (stream != null) {
            stream.frequencyRatio(rate);
            if (drain != null) {
                drain.rate(rate, System.nanoTime());
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
        if (silence != null) {
            silence.close();
            silence = null;
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
