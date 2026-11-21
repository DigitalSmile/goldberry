package dev.goldberry.natives.sdl.audio;

import java.util.Objects;

/// One audio device SDL lists: its instance id, which lasts while it stays
/// plugged in, and the name the system gives it.
///
/// @param id   the `SDL_AudioDeviceID`, never zero
/// @param name the name to show, possibly empty
public record SdlAudioDevice(int id, String name) {

    public SdlAudioDevice {
        if (id == 0) {
            throw new IllegalArgumentException("SDL's device ids start at one");
        }
        Objects.requireNonNull(name, "name");
    }
}
