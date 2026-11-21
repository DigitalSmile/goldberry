package dev.goldberry.natives.desktop.dragout;

import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/// One clipboard format a drag offers on Windows, and its bytes.
///
/// Either a standard format, by number, or one registered by name (`PNG`,
/// `UniformResourceLocatorW`, a MIME type of the application's own). The bytes
/// are what goes in the format's global memory, made when a target asks.
///
/// @param standard   the standard format's number, or 0 for a registered one
/// @param registered the registered format's name, or null for a standard one
/// @param bytes      made on request, on the UI thread
public record OleFormat(int standard, @Nullable String registered, Supplier<byte[]> bytes) {

    /// `CF_UNICODETEXT`.
    public static final int UNICODE_TEXT = 13;

    /// `CF_HDROP`.
    public static final int FILES = 15;

    /// `CF_DIBV5`.
    public static final int BITMAP_V5 = 17;

    public OleFormat {
        Objects.requireNonNull(bytes, "bytes");
        if ((standard == 0) == (registered == null)) {
            throw new IllegalArgumentException("a format is standard or registered, not both or neither");
        }
        if (registered != null && registered.isBlank()) {
            throw new IllegalArgumentException("a registered format needs a name");
        }
    }

    /// The standard format `number`.
    public static OleFormat standard(int number, Supplier<byte[]> bytes) {
        if (number <= 0) {
            throw new IllegalArgumentException("a standard clipboard format is positive: " + number);
        }
        return new OleFormat(number, null, bytes);
    }

    /// The format registered as `name`.
    public static OleFormat registered(String name, Supplier<byte[]> bytes) {
        return new OleFormat(0, Objects.requireNonNull(name, "name"), bytes);
    }

    /// The format's name, for a log: its registered name, or `CF_` and its
    /// number.
    public String label() {
        return registered != null ? registered : "CF_" + standard;
    }
}
