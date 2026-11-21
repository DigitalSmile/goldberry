package dev.goldberry.natives.sdl.audio;

/// The format a device runs at, or would open in: what SDL converts to and
/// from.
///
/// @param sampleRate   frames a second
/// @param channels     channels per frame
/// @param bufferFrames the device's buffer, in sample frames: how much SDL
///                     moves at a time
public record SdlAudioDeviceFormat(int sampleRate, int channels, int bufferFrames) {

    public SdlAudioDeviceFormat {
        if (sampleRate <= 0 || channels <= 0 || bufferFrames < 0) {
            throw new IllegalArgumentException(
                    "rate " + sampleRate + ", channels " + channels + ", buffer " + bufferFrames);
        }
    }
}
