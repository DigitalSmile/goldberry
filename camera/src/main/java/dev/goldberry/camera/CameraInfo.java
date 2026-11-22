package dev.goldberry.camera;

import java.util.List;
import java.util.Objects;

/// One camera the system lists, and what it offers.
///
/// @param id       the system's id for it, which lasts while it stays
///                 plugged in
/// @param name     the name to show, possibly empty
/// @param position where it faces
/// @param formats  the modes it offers, largest first, compressed ones
///                 included
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public record CameraInfo(int id, String name, CameraPosition position, List<CameraFormat> formats) {

    public CameraInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        formats = List.copyOf(formats);
    }

    /// The modes a camera opens: the uncompressed ones.
    public List<CameraFormat> uncompressed() {
        return formats.stream().filter(format -> !format.compressed()).toList();
    }
}
