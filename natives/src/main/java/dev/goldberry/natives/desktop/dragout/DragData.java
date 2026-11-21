package dev.goldberry.natives.desktop.dragout;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// What a drag carries, as a target asks for it: by MIME type.
public interface DragData {

    /// Every type offered, most specific first.
    List<String> types();

    /// The bytes for `mime`, made now on the UI thread, or null when it is not
    /// offered.
    byte @Nullable [] bytes(String mime);
}
