package dev.goldberry.frame.pacing;

/// What decides how often a window animates: whether it can be seen, whether
/// the application's focus is in it, and whether the desktop is saving power.
///
/// @param occluded whether the window cannot be seen at all: covered, on
///                 another Space, hidden or minimized
/// @param active   whether the keyboard focus is in this window or one of its
///                 popups
/// @param saving   whether the desktop's power-saving mode is on
public record WindowActivity(boolean occluded, boolean active, boolean saving) {

    /// A window in front, focused, on a machine not saving power.
    public static final WindowActivity FOREGROUND = new WindowActivity(false, true, false);
}
