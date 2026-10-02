package dev.goldberry.media.platform.macos;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.audio.OutputLatency;

/// The default output device's latency on macOS, read from Core Audio: what
/// `goldberry-media`'s SDL sink takes off the audio clock beyond SDL's own
/// buffers, so that the clock reads what is heard.
///
/// Found by `ServiceLoader` like the decoders, so an application that plays
/// media has its pictures held back for a Bluetooth headset with no code. On any other system it answers empty.
///
/// Core Audio is bound on the first question, apart from the decoders'
/// frameworks: a Mac whose VideoToolbox could not be bound still has its
/// latency read, and the other way round.
///
/// The answer is logged at debug whenever it changes, device and parts, which is
/// how a latency is measured on a given Mac and headset.
public final class CoreAudioLatency implements OutputLatency {

    private static final Logger LOG = Logs.of(CoreAudioLatency.class);

    private final @Nullable CoreAudio coreAudio;
    private @Nullable DeviceLatency last;

    /// The provider `ServiceLoader` makes: Core Audio, bound once per process.
    public CoreAudioLatency() {
        this(Holder.CORE_AUDIO);
    }

    /// A provider over `coreAudio`, or one that answers nothing for null.
    CoreAudioLatency(@Nullable CoreAudio coreAudio) {
        this.coreAudio = coreAudio;
    }

    @Override
    public Optional<Duration> defaultOutput() {
        var bound = coreAudio;
        if (bound == null) {
            return Optional.empty();
        }
        var read = bound.defaultOutputLatency();
        read.ifPresent(this::log);
        return read.map(DeviceLatency::total);
    }

    /// Logs `latency` when it is news: another device, or other numbers.
    private synchronized void log(DeviceLatency latency) {
        if (!Objects.equals(latency, last)) {
            last = latency;
            LOG.debug("default output latency: {}", latency);
        }
    }

    /// Opens Core Audio on the first provider made, or leaves it null: not
    /// macOS, or a macOS without the function.
    private static final class Holder {
        static final @Nullable CoreAudio CORE_AUDIO = bind();

        private static @Nullable CoreAudio bind() {
            if (!Frameworks.isMac(System.getProperty("os.name", ""))) {
                return null;
            }
            try {
                return new CoreAudio(Framework.CORE_AUDIO.open());
            } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                LOG.debug("no output latency from Core Audio: {}", e.getMessage());
                return null;
            }
        }
    }
}
