package dev.goldberry.media.platform.macos;

import java.time.Duration;

/// What an output device's latency is made of, as Core Audio reports it
/// (ADR-0474): four counts of frames at the device's rate.
///
/// The sum is PortAudio's reading of the same properties for an output
/// stream's latency: the time from an IO buffer being handed to the HAL to its
/// first frame leaving the device.
///
/// - `deviceFrames`, `kAudioDevicePropertyLatency`: the device's own, from its
///   converter to its output. A Bluetooth device reports its radio link here.
/// - `safetyOffsetFrames`, `kAudioDevicePropertySafetyOffset`: how far ahead of
///   the device's read position the HAL writes, so as never to be caught up.
/// - `bufferFrames`, `kAudioDevicePropertyBufferFrameSize`: one IO cycle, which
///   the HAL fills before the device reads any of it.
/// - `streamFrames`, `kAudioStreamPropertyLatency` of the first output stream:
///   what the stream adds on top of the device.
///
/// @param device             the `AudioObjectID` it was read from
/// @param sampleRate         the device's nominal rate, frames a second
/// @param deviceFrames       the device's latency
/// @param safetyOffsetFrames the safety offset
/// @param bufferFrames       the IO buffer
/// @param streamFrames       the stream's latency
/// @param transport          `kAudioDevicePropertyTransportType`, a
///                           four-character code: `'blue'` for Bluetooth,
///                           `'bltn'` for built in; for the log
record DeviceLatency(
        int device,
        double sampleRate,
        int deviceFrames,
        int safetyOffsetFrames,
        int bufferFrames,
        int streamFrames,
        int transport) {

    DeviceLatency {
        if (!(sampleRate > 0)) {
            throw new IllegalArgumentException("sample rate " + sampleRate);
        }
    }

    /// Every part, in frames. Unsigned `UInt32`s, so read as such: a device that
    /// reports nonsense is not made to report a negative latency.
    long frames() {
        return Integer.toUnsignedLong(deviceFrames)
                + Integer.toUnsignedLong(safetyOffsetFrames)
                + Integer.toUnsignedLong(bufferFrames)
                + Integer.toUnsignedLong(streamFrames);
    }

    /// [#frames()] as time at the device's rate.
    Duration total() {
        return Duration.ofNanos(Math.round(frames() * 1e9 / sampleRate));
    }

    /// Whether the device is on Bluetooth: `'blue'`, or `'blea'` for LE Audio.
    boolean bluetooth() {
        return transport == OsStatus.code("blue") || transport == OsStatus.code("blea");
    }

    @Override
    public String toString() {
        return "device " + device + (bluetooth() ? " (Bluetooth)" : "") + " at " + Math.round(sampleRate) + " Hz: "
                + deviceFrames + " + " + safetyOffsetFrames + " + " + bufferFrames + " + " + streamFrames + " frames = "
                + total().toMillis() + " ms";
    }
}
