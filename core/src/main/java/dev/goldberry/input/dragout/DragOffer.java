package dev.goldberry.input.dragout;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/// What a drag carries out of the application.
///
/// ```java
/// new Card(frame)
///         .draggable(frame)
///         .dragOut(() -> DragOffer.image(frame::png).withFiles(frame.exportedPath()))
/// ```
///
/// Any mix of five kinds, each offered under the platform's own names:
///
/// - **files** that exist, as `text/uri-list`, `CF_HDROP` or file URLs;
/// - **links**, as `text/uri-list` and as text;
/// - **text**, as UTF-8 text;
/// - an **image**, as `image/png`, and also as a PNG file for the targets that
///   take only files (Explorer, most file managers);
/// - **bytes** under any MIME type the application names.
///
/// Everything but the paths is asked for when a target takes it: the suppliers
/// run on the UI thread, once per request, so an image that is never dropped
/// is never encoded.
///
/// @param files      files that exist, absolute
/// @param links      links, in order
/// @param text       text, or null
/// @param image      a PNG image's bytes, or null
/// @param data       bytes by MIME type, in the order they are offered
/// @param operations what a target may do with it, never empty
/// @param picture    the picture under the pointer, or null for the source as it was painted
public record DragOffer(
        List<Path> files,
        List<URI> links,
        @Nullable Supplier<String> text,
        @Nullable Supplier<byte[]> image,
        Map<String, Supplier<byte[]>> data,
        Set<DragOperation> operations,
        @Nullable DragPicture picture) {

    /// What a drag offers when it says nothing else: a copy.
    public static final Set<DragOperation> COPY_ONLY = Set.of(DragOperation.COPY);

    public DragOffer {
        files = files.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        links = List.copyOf(links);
        var ordered = new LinkedHashMap<String, Supplier<byte[]>>();
        for (var entry : data.entrySet()) {
            ordered.put(mime(entry.getKey()), Objects.requireNonNull(entry.getValue(), "data supplier"));
        }
        data = Collections.unmodifiableMap(ordered);
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("a drag offer allows at least one operation");
        }
        operations = Collections.unmodifiableSet(EnumSet.copyOf(operations));
    }

    /// An offer of nothing, the start of a chain.
    public static DragOffer empty() {
        return new DragOffer(List.of(), List.of(), null, null, Map.of(), COPY_ONLY, null);
    }

    /// An offer of `paths`, which should exist when the drag starts.
    public static DragOffer files(Path... paths) {
        return empty().withFiles(paths);
    }

    /// An offer of `paths`.
    public static DragOffer files(List<Path> paths) {
        return empty().withFiles(paths);
    }

    /// An offer of one link.
    public static DragOffer link(URI link) {
        return empty().withLinks(List.of(link));
    }

    /// An offer of `text`.
    public static DragOffer text(String text) {
        return empty().withText(text);
    }

    /// An offer of a PNG image, encoded when a target asks for it.
    public static DragOffer image(Supplier<byte[]> png) {
        return empty().withImage(png);
    }

    /// An offer of bytes under `mime`.
    public static DragOffer data(String mime, Supplier<byte[]> bytes) {
        return empty().withData(mime, bytes);
    }

    /// This, also offering `paths`.
    public DragOffer withFiles(Path... paths) {
        return withFiles(List.of(paths));
    }

    /// This, also offering `paths`.
    public DragOffer withFiles(List<Path> paths) {
        var all = new ArrayList<>(files);
        all.addAll(paths);
        return new DragOffer(all, links, text, image, data, operations, picture);
    }

    /// This, also offering `more` links.
    public DragOffer withLinks(List<URI> more) {
        var all = new ArrayList<>(links);
        all.addAll(more);
        return new DragOffer(files, all, text, image, data, operations, picture);
    }

    /// This, offering `value` as its text.
    public DragOffer withText(String value) {
        Objects.requireNonNull(value, "value");
        return withText(() -> value);
    }

    /// This, offering text made when a target asks for it.
    public DragOffer withText(Supplier<String> value) {
        return new DragOffer(files, links, Objects.requireNonNull(value, "value"), image, data, operations, picture);
    }

    /// This, offering a PNG image encoded when a target asks for it.
    public DragOffer withImage(Supplier<byte[]> png) {
        return new DragOffer(files, links, text, Objects.requireNonNull(png, "png"), data, operations, picture);
    }

    /// This, also offering bytes under `mime`, after the types it already
    /// offers. A type offered twice keeps its first place and its last bytes.
    public DragOffer withData(String mime, Supplier<byte[]> bytes) {
        var all = new LinkedHashMap<>(data);
        all.put(mime(mime), Objects.requireNonNull(bytes, "bytes"));
        return new DragOffer(files, links, text, image, all, operations, picture);
    }

    /// This, allowing exactly `allowed`.
    public DragOffer allowing(DragOperation first, DragOperation... more) {
        var allowed = EnumSet.of(first, more);
        return new DragOffer(files, links, text, image, data, allowed, picture);
    }

    /// This, with its own picture under the pointer.
    public DragOffer withPicture(DragPicture value) {
        return new DragOffer(files, links, text, image, data, operations, Objects.requireNonNull(value, "value"));
    }

    /// Whether there is nothing to offer, which a drag refuses: it stays in the
    /// application.
    public boolean isEmpty() {
        return files.isEmpty() && links.isEmpty() && text == null && image == null && data.isEmpty();
    }

    private static String mime(String mime) {
        Objects.requireNonNull(mime, "mime");
        var trimmed = mime.strip();
        if (trimmed.isEmpty() || trimmed.indexOf('/') <= 0) {
            throw new IllegalArgumentException("not a MIME type: '" + mime + "'");
        }
        return trimmed;
    }
}
