package dev.goldberry.natives.sdl.desktop;

import java.util.Objects;

/// What `SDL_GetPowerInfo` answered.
///
/// @param state   where the power comes from
/// @param seconds the battery time left, or -1 where unknown or not on battery
/// @param percent the battery left, 0 to 100, or -1 where unknown or there is
///                no battery
public record SdlPowerInfo(SdlPowerState state, int seconds, int percent) {

    /// The answer for a library that cannot ask.
    public static final SdlPowerInfo UNKNOWN = new SdlPowerInfo(SdlPowerState.UNKNOWN, -1, -1);

    public SdlPowerInfo {
        Objects.requireNonNull(state, "state");
    }
}
