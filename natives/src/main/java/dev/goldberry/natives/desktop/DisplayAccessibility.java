package dev.goldberry.natives.desktop;

import java.util.Objects;

/// The desktop's display accessibility switches: more contrast, colours forced
/// by the desktop, and less transparency.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param increasedContrast   the desktop asks for more contrast: CSS's
///                            `prefers-contrast: more`
/// @param forcedColors        the desktop draws in its own colours, Windows'
///                            high-contrast mode: CSS's `forced-colors: active`
/// @param reducedTransparency the desktop asks for less transparency: CSS's
///                            `prefers-reduced-transparency: reduce`
public record DisplayAccessibility(Setting increasedContrast, Setting forcedColors, Setting reducedTransparency) {

    /// Nothing known.
    public static final DisplayAccessibility UNKNOWN =
            new DisplayAccessibility(Setting.UNKNOWN, Setting.UNKNOWN, Setting.UNKNOWN);

    public DisplayAccessibility {
        Objects.requireNonNull(increasedContrast, "increasedContrast");
        Objects.requireNonNull(forcedColors, "forcedColors");
        Objects.requireNonNull(reducedTransparency, "reducedTransparency");
    }
}
