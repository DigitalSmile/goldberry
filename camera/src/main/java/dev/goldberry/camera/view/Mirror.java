package dev.goldberry.camera.view;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.camera.CameraInfo;
import dev.goldberry.camera.CameraPosition;

/// Whether a `camera-view` shows its pictures mirrored, left for right, as a
/// person expects to see themselves.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public enum Mirror {

    /// Mirrored unless the camera faces away: a desktop camera, which does not
    /// say where it faces, almost always faces the user.
    AUTO,

    /// Always mirrored.
    ON,

    /// Never mirrored: what the camera sends, as the other end of a call sees it.
    OFF;

    /// Whether `camera` is shown mirrored.
    public boolean mirrors(CameraInfo camera) {
        return switch (this) {
            case AUTO -> camera.position() != CameraPosition.BACK;
            case ON -> true;
            case OFF -> false;
        };
    }

    /// The setting markup names: `auto`, `on` or `off`; nothing named is auto.
    ///
    /// @throws IllegalArgumentException for any other name
    public static Mirror named(@Nullable String name) {
        if (name == null) {
            return AUTO;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "on" -> ON;
            case "off" -> OFF;
            default -> throw new IllegalArgumentException("mirror is auto, on or off, not \"" + name + "\"");
        };
    }
}
