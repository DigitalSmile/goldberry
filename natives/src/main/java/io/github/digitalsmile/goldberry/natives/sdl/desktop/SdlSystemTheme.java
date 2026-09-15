package io.github.digitalsmile.goldberry.natives.sdl.desktop;

/// What the desktop is set to — SDL's `SDL_SystemTheme`.
///
/// One call covers all three platforms, which is the whole reason this is bound
/// rather than read: the alternatives are `AppleInterfaceStyle` through
/// `NSUserDefaults`, the `AppsUseLightTheme` registry value, and the XDG settings
/// portal over D-Bus — three platform integrations for one boolean
/// (`docs/gaps.md` G26, ADR-0322).
///
/// Ordinals in a C enum, so every value is checked against the compiled SDL by
/// the layout probe (ADR-0010): a wrong one starts the application in the wrong
/// theme and reports nothing.
public enum SdlSystemTheme {

    /// The desktop does not say — SDL's answer where the platform has no such
    /// setting, and **not** the same as light. An application needs to tell "the
    /// desktop says light" from "the desktop does not say": the first is a theme
    /// and the second is a default.
    UNKNOWN(0),

    LIGHT(1),

    DARK(2);

    private final int value;

    SdlSystemTheme(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_SYSTEM_THEME_" + name();
    }

    /// The theme `value` names, or [#UNKNOWN] for anything this enum predates.
    ///
    /// Tolerant rather than strict, which is
    /// [io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem#decode]'s rule
    /// and not
    /// [io.github.digitalsmile.goldberry.natives.yoga.measure.MeasureMode#of(int)]'s:
    /// a future SDL that learns a third theme should leave an application in its
    /// default rather than crash it, because "the desktop says something I do not
    /// understand" and "the desktop does not say" are the same answer to everyone
    /// above this line.
    public static SdlSystemTheme of(int value) {
        for (var theme : values()) {
            if (theme.value == value) {
                return theme;
            }
        }
        return UNKNOWN;
    }
}
