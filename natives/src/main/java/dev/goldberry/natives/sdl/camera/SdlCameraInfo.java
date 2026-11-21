package dev.goldberry.natives.sdl.camera;

import java.util.List;
import java.util.Objects;

/// One camera SDL lists, and what it offers.
///
/// @param id       the `SDL_CameraID`, which lasts while it stays plugged in;
///                 never zero
/// @param name     the name to show, possibly empty
/// @param position where it faces
/// @param formats  the modes it offers, in SDL's order (largest first)
public record SdlCameraInfo(int id, String name, SdlCameraPosition position, List<SdlCameraSpec> formats) {

    public SdlCameraInfo {
        if (id == 0) {
            throw new IllegalArgumentException("SDL's camera ids start at one");
        }
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        formats = List.copyOf(formats);
    }
}
