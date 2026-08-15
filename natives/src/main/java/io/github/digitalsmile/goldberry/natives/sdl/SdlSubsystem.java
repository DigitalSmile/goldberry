package io.github.digitalsmile.goldberry.natives.sdl;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/// The SDL subsystems Goldberry can initialize.
///
/// Mirrors the `SDL_INIT_*` flags in `SDL3/SDL_init.h`. They are a bit mask, not
/// an enumeration, so the values are declared explicitly and converted through
/// [#mask] and [#decode].
///
/// SDL initializes some subsystems implicitly: video, audio, joystick, sensor and
/// camera each imply events, and gamepad implies joystick. So what [Sdl#wasInit()]
/// reports is generally a superset of what was asked for.
public enum SdlSubsystem {

    AUDIO(0x00000010),

    /// Windowing and displays. SDL requires this one on the main thread.
    VIDEO(0x00000020),

    JOYSTICK(0x00000200),

    HAPTIC(0x00001000),

    GAMEPAD(0x00002000),

    /// The event queue. Implied by most of the others.
    EVENTS(0x00004000),

    SENSOR(0x00008000),

    CAMERA(0x00010000);

    private final int bit;

    SdlSubsystem(int bit) {
        this.bit = bit;
    }

    /// The `SDL_INIT_*` value.
    public int bit() {
        return bit;
    }

    /// Folds a set of subsystems into an `SDL_InitFlags` mask.
    public static int mask(Collection<SdlSubsystem> subsystems) {
        var flags = 0;
        for (var subsystem : subsystems) {
            flags |= subsystem.bit;
        }
        return flags;
    }

    /// Reads an `SDL_InitFlags` mask back into subsystems.
    ///
    /// Bits this enum does not know are **ignored**, not rejected. `SDL_WasInit`
    /// reports what SDL initialized, and a future SDL may initialize something
    /// this enum predates; failing on it would turn a routine dependency bump
    /// into a crash. That is the opposite of the rule in
    /// [io.github.digitalsmile.goldberry.natives.yoga.MeasureMode#of(int)], where
    /// an unrecognised value means the binding signature is wrong rather than
    /// that the world moved on.
    public static Set<SdlSubsystem> decode(int flags) {
        var subsystems = EnumSet.noneOf(SdlSubsystem.class);
        for (var subsystem : values()) {
            if ((flags & subsystem.bit) == subsystem.bit) {
                subsystems.add(subsystem);
            }
        }
        return subsystems;
    }
}
