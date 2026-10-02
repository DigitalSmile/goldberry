package dev.goldberry.media.platform.macos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.audio.OutputLatency;

/// The default output device's latency from Core Audio: the sum,
/// read on this Mac, and found by `ServiceLoader`.
@DisplayName("CoreAudioLatency")
class CoreAudioLatencyTest {

    /// Core Audio, bound, or a skip: not macOS. A Mac with no output device at
    /// all (a CI runner can be one) answers empty rather than skipping, and the
    /// tests that need a device say so.
    private static CoreAudio coreAudio() {
        Assumptions.assumeTrue(Frameworks.isMac(System.getProperty("os.name", "")), "Core Audio is macOS's");
        return new CoreAudio(Framework.CORE_AUDIO.open());
    }

    @Test
    @DisplayName("the parts add up in frames, read unsigned, and become time at the device's rate")
    void sum() {
        var latency = new DeviceLatency(7, 48_000, 24, 144, 512, 0, OsStatus.code("bltn"));
        assertEquals(680, latency.frames());
        assertEquals(Duration.ofNanos(14_166_667), latency.total());
        assertFalse(latency.bluetooth());

        var huge = new DeviceLatency(7, 48_000, -1, 0, 0, 0, 0);
        assertEquals(0xFFFF_FFFFL, huge.frames(), "a UInt32 is never negative");
        assertTrue(new DeviceLatency(7, 44_100, 0, 0, 0, 0, OsStatus.code("blue")).bluetooth());
        assertTrue(new DeviceLatency(7, 44_100, 0, 0, 0, 0, OsStatus.code("blea")).bluetooth());
        assertThrows(IllegalArgumentException.class, () -> new DeviceLatency(7, 0, 0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("names the device, its transport and each part, for a log line")
    void describes() {
        var text = new DeviceLatency(91, 48_000, 2_400, 0, 512, 0, OsStatus.code("blue")).toString();
        assertEquals("device 91 (Bluetooth) at 48000 Hz: 2400 + 0 + 512 + 0 frames = 60 ms", text);
    }

    @Test
    @DisplayName("a provider with no Core Audio answers nothing")
    void unbound() {
        assertEquals(Optional.empty(), new CoreAudioLatency(null).defaultOutput());
    }

    /// The measurement: what this Mac's default output says, printed so a run
    /// on a given headset records it.
    @Test
    @DisplayName("reads this Mac's default output device, between nothing and a second")
    void readsTheDefaultDevice() {
        var bound = coreAudio();
        var read = bound.defaultOutputLatency();
        Assumptions.assumeTrue(read.isPresent(), "this Mac has no default output device");
        var latency = read.get();
        System.out.println("default output latency: " + latency);

        assertTrue(latency.sampleRate() >= 8_000 && latency.sampleRate() <= 384_000, latency.toString());
        assertTrue(latency.total().compareTo(Duration.ofSeconds(1)) < 0, latency.toString());
        assertTrue(latency.bufferFrames() > 0, "every running device has an IO buffer: " + latency);
        assertEquals(Optional.of(latency.total()), new CoreAudioLatency(bound).defaultOutput());
    }

    @Test
    @DisplayName("a property an object does not have is empty, not an exception")
    void missingProperty() {
        var bound = coreAudio();
        assertTrue(bound.uint32(CoreAudio.SYSTEM_OBJECT, OsStatus.code("zzzz"), CoreAudio.SCOPE_GLOBAL)
                .isEmpty());
        assertTrue(bound.float64(0x7FFF_FFF0, CoreAudio.NOMINAL_SAMPLE_RATE, CoreAudio.SCOPE_GLOBAL)
                .isEmpty());
        assertTrue(bound.firstObject(0x7FFF_FFF0, CoreAudio.STREAMS, CoreAudio.SCOPE_OUTPUT)
                .isEmpty());
    }

    @Test
    @DisplayName("ServiceLoader finds it, and so does the SDL sink's scan")
    void installed() {
        var providers = ServiceLoader.load(OutputLatency.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        assertEquals(1, providers.size());
        assertInstanceOf(CoreAudioLatency.class, providers.getFirst());
        if (Frameworks.isMac(System.getProperty("os.name", ""))
                && coreAudio().defaultOutputDevice().isPresent()) {
            assertTrue(OutputLatency.installed().defaultOutput().isPresent());
        } else {
            assertEquals(
                    Optional.empty(),
                    OutputLatency.firstOf(List.copyOf(providers)).defaultOutput());
        }
    }
}
