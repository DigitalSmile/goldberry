package io.github.digitalsmile.goldberry.natives.sdl;

/// Which way round the values in an `SDL_MouseWheelEvent` are.
///
/// SDL does not normalize this. When the platform is configured for "natural"
/// scrolling — the default on macOS, an option everywhere else — SDL reports
/// `FLIPPED` and leaves `x` and `y` inverted, documenting that the caller should
/// multiply by -1 to get them back. A reader that ignores the field is correct on
/// its own machine and scrolls backwards on everyone else's, which is the kind of
/// bug that survives a whole release.
public enum SdlWheelDirection {

    /// The values mean what they say.
    NORMAL(0),

    /// The values are inverted; negate them.
    FLIPPED(1);

    private final int value;

    SdlWheelDirection(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_MOUSEWHEEL_" + name();
    }

    /// `value` as a sign to multiply a wheel delta by.
    ///
    /// An unrecognized direction is treated as [#NORMAL]: SDL has only ever had
    /// these two, and inventing an inversion for a third would be worse than
    /// passing the numbers through.
    public static float sign(int value) {
        return value == FLIPPED.value ? -1f : 1f;
    }
}
