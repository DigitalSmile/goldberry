package dev.goldberry.natives.sdl.desktop;

/// Where the machine's power comes from, as `SDL_GetPowerInfo` answers it:
/// `SDL_PowerState`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlPowerState {

    /// SDL could not ask.
    ERROR(-1),

    /// The platform would not say.
    UNKNOWN(0),

    /// Running on a battery that is not being charged.
    ON_BATTERY(1),

    /// Plugged in, with no battery at all: a desktop machine.
    NO_BATTERY(2),

    /// Plugged in, charging a battery.
    CHARGING(3),

    /// Plugged in, with the battery full.
    CHARGED(4);

    private final int value;

    SdlPowerState(int value) {
        this.value = value;
    }

    /// The C enumerator's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_POWERSTATE_" + name();
    }

    /// The state SDL reported as `value`; anything it does not define reads as
    /// [#UNKNOWN].
    public static SdlPowerState of(int value) {
        for (var state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
