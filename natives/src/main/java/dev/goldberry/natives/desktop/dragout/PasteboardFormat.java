package dev.goldberry.natives.desktop.dragout;

import java.util.Objects;
import java.util.function.Supplier;

/// One type a drag offers on a macOS pasteboard, and its bytes.
///
/// Either a uniform type identifier as it is (`public.png`,
/// `public.utf8-plain-text`), or a MIME type of the application's own, which
/// the drag source turns into the identifier the system has for it, a dynamic
/// one where there is none.
///
/// @param type  the identifier, or the MIME type
/// @param mime  whether `type` is a MIME type still to be turned into one
/// @param bytes made when a target asks, on the main thread
public record PasteboardFormat(String type, boolean mime, Supplier<byte[]> bytes) {

    /// `public.utf8-plain-text`, `NSPasteboardTypeString`.
    public static final String TEXT = "public.utf8-plain-text";

    /// `public.url`, `NSPasteboardTypeURL`.
    public static final String URL = "public.url";

    /// `public.png`, `NSPasteboardTypePNG`.
    public static final String PNG = "public.png";

    public PasteboardFormat {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(bytes, "bytes");
        if (type.isBlank()) {
            throw new IllegalArgumentException("a pasteboard type needs a name");
        }
    }

    /// The uniform type identifier `type`.
    public static PasteboardFormat uti(String type, Supplier<byte[]> bytes) {
        return new PasteboardFormat(type, false, bytes);
    }

    /// The MIME type `type`, turned into its identifier when the drag starts.
    public static PasteboardFormat mime(String type, Supplier<byte[]> bytes) {
        return new PasteboardFormat(type, true, bytes);
    }
}
