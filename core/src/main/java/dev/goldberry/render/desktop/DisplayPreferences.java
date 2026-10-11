package dev.goldberry.render.desktop;

/// The desktop's display accessibility switches: more contrast, colours forced
/// by the desktop, and less transparency.
///
/// ```java
/// if (window.displayPreferences().increasedContrast()) { … }
/// ```
///
/// Each is false where the desktop says off and where it does not say, which
/// are the same thing to a caller: a default is not an instruction. The
/// launcher hands them to the renderer, so `@media (prefers-contrast: more)`
/// and the rest follow the desktop with no code of an application's own.
///
/// Read more: [Media queries](https://goldberry.dev/docs/guide/styling.html#media-queries).
///
/// @param increasedContrast   the desktop asks for more contrast
/// @param forcedColors        the desktop draws in its own colours, Windows'
///                            high-contrast mode
/// @param reducedTransparency the desktop asks for less transparency
public record DisplayPreferences(boolean increasedContrast, boolean forcedColors, boolean reducedTransparency) {

    /// No switch on: what a desktop that says nothing gets.
    public static final DisplayPreferences NONE = new DisplayPreferences(false, false, false);
}
