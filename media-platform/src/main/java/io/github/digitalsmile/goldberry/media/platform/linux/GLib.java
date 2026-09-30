package io.github.digitalsmile.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/// The parts of `libglib-2.0` GStreamer's API hands back: strings and errors the
/// caller frees, and the linked lists the registry answers in.
final class GLib {

    /// `GError`'s `message`, after `GQuark domain` and `gint code`. Checked at load
    /// by [GstLayout], against an error GStreamer raises.
    static final long ERROR_MESSAGE = 8;

    /// `GList`'s `next`, after `gpointer data`.
    static final long LIST_NEXT = 8;

    /// `void g_free(gpointer mem)` and `void g_error_free(GError *error)`
    private static final MethodHandle FD_void_pointer = GstLibrary.link(FunctionDescriptor.ofVoid(ADDRESS));

    private final MemorySegment free;
    private final MemorySegment errorFree;

    GLib(SymbolLookup lookup) {
        var l = GstLibrary.GLIB;
        this.free = l.symbol(lookup, "g_free");
        this.errorFree = l.symbol(lookup, "g_error_free");
    }

    /// An error GStreamer raised, read and freed.
    ///
    /// @param domain  the `GQuark` of the error's domain
    /// @param code    the code within the domain
    /// @param message what it says
    record GError(int domain, int code, String message) {

        GError {
            Objects.requireNonNull(message, "message");
        }

        /// The same error with GStreamer's debugging detail after the message.
        GError withDetail(String detail) {
            return new GError(domain, code, message + " (" + detail + ")");
        }
    }

    /// Reads the `GError` at `error` and frees it. A null pointer is an error
    /// that says nothing.
    @SuppressWarnings("restricted")
    GError takeError(MemorySegment error) {
        if (error.equals(MemorySegment.NULL)) {
            return new GError(0, 0, "no error given");
        }
        var struct = error.reinterpret(ERROR_MESSAGE + ADDRESS.byteSize());
        var message = struct.get(ADDRESS, ERROR_MESSAGE);
        var read = new GError(
                struct.get(JAVA_INT, 0),
                struct.get(JAVA_INT, 4),
                message.equals(MemorySegment.NULL)
                        ? ""
                        : message.reinterpret(Long.MAX_VALUE).getString(0));
        call(errorFree, error, "g_error_free");
        return read;
    }

    /// Reads the string at `string`, which the caller owns, and frees it. Empty
    /// for a null pointer.
    @SuppressWarnings("restricted")
    String takeString(MemorySegment string) {
        if (string.equals(MemorySegment.NULL)) {
            return "";
        }
        var read = string.reinterpret(Long.MAX_VALUE).getString(0);
        call(free, string, "g_free");
        return read;
    }

    /// The element a `GList` node holds.
    @SuppressWarnings("restricted")
    static MemorySegment data(MemorySegment node) {
        return node.reinterpret(LIST_NEXT + ADDRESS.byteSize()).get(ADDRESS, 0);
    }

    /// The node after `node`, or a null pointer at the end.
    @SuppressWarnings("restricted")
    static MemorySegment next(MemorySegment node) {
        return node.reinterpret(LIST_NEXT + ADDRESS.byteSize()).get(ADDRESS, LIST_NEXT);
    }

    private static void call(MemorySegment function, MemorySegment argument, String name) {
        try {
            FD_void_pointer.invokeExact(function, argument);
        } catch (Throwable t) {
            throw GstLibrary.failure(name, t);
        }
    }
}
