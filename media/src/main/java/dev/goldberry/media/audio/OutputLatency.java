package dev.goldberry.media.audio;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/// How long the operating system takes to make a sample heard once the audio
/// library has handed it over: the device's own latency (`docs/goldberry-media.md`
/// §3, "Master clock", ADR-0474).
///
/// SDL says how much it holds and nothing about what happens after, and after is
/// where Bluetooth spends its 150–250 ms. A picture timed against a clock that
/// ignores it leads its sound by that much. Only the operating system knows the
/// number, so this is an SPI: a provider per system, found by [ServiceLoader].
/// `…media.platform.macos` has the CoreAudio one; WASAPI and PulseAudio ones
/// are still to come, and until then those systems report nothing and
/// [dev.goldberry.media.MediaPlayer#setAudioDelay] is the way to
/// correct them by hand.
///
/// Asked about the **default playback device**, because that is the device the
/// SDL sink follows: headphones plugged in mid-song take the song with them, and
/// the latency with it. The sink asks again every [SdlAudioSink#REFRESH] rather
/// than once, so a switch to a Bluetooth headset is heard in the clock within
/// a second.
///
/// Called from the audio thread. A provider must answer quickly, a few system
/// calls, and never throw: a system it cannot read answers empty.
@FunctionalInterface
public interface OutputLatency {

    /// Reports nothing: the latency of a system with no provider, and of a sink
    /// that is not a device.
    OutputLatency NONE = Optional::empty;

    /// How long a sample handed to the default playback device now takes to be
    /// heard, or empty when this provider cannot say: another operating system,
    /// no device, or a query the system refused.
    Optional<Duration> defaultOutput();

    /// The providers on the module path or class path, asked in turn: the first
    /// answer is the latency. [#NONE] when there are none.
    static OutputLatency installed() {
        return firstOf(ServiceLoader.load(OutputLatency.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList());
    }

    /// `providers` asked in turn, the first answer taken; a provider that throws
    /// is logged and passed over rather than taking the clock down with it.
    static OutputLatency firstOf(List<OutputLatency> providers) {
        var copy = List.copyOf(providers);
        return copy.isEmpty() ? NONE : new FirstAnswer(copy);
    }
}
