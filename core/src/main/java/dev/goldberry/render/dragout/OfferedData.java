package dev.goldberry.render.dragout;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.render.clipboard.UriList;

/// An offer as MIME types and bytes: what X11, Wayland and macOS targets ask
/// for, by name.
///
/// Files and links go together as one `text/uri-list`, files first. Text is
/// `text/plain;charset=utf-8` and plain `text/plain`, both UTF-8, since a
/// target asking for the plain one today expects UTF-8 too. A link with no
/// text alongside it is offered as text as well, the way browsers offer one.
/// The image is `image/png`. The application's own types follow, in the
/// order it gave them, and replace a built-in type of the same name.
public final class OfferedData {

    /// UTF-8 text, the name every Linux toolkit asks for first.
    public static final String TEXT_UTF8 = "text/plain;charset=utf-8";

    /// Plain text.
    public static final String TEXT = "text/plain";

    /// A PNG image.
    public static final String PNG = "image/png";

    private final DragOffer offer;

    private OfferedData(DragOffer offer) {
        this.offer = offer;
    }

    /// The bytes behind `offer`.
    public static OfferedData of(DragOffer offer) {
        return new OfferedData(Objects.requireNonNull(offer, "offer"));
    }

    /// Every type offered, most specific first, each once.
    public List<String> types() {
        var types = new LinkedHashSet<String>();
        if (!offer.files().isEmpty() || !offer.links().isEmpty()) {
            types.add(UriList.MIME);
        }
        if (hasText()) {
            types.add(TEXT_UTF8);
            types.add(TEXT);
        }
        if (offer.image() != null) {
            types.add(PNG);
        }
        types.addAll(offer.data().keySet());
        return List.copyOf(types);
    }

    /// The bytes for `mime`, made now, or empty when it is not offered.
    ///
    /// Runs the offer's suppliers, so it is the UI thread's to call. A type is
    /// matched without regard to case and to a `charset` parameter on
    /// `text/plain`, which targets spell several ways.
    public Optional<byte[]> bytes(String mime) {
        Objects.requireNonNull(mime, "mime");
        var own = offer.data().get(mime);
        if (own != null) {
            return Optional.of(own.get());
        }
        var wanted = mime.strip().toLowerCase(Locale.ROOT);
        if (wanted.equals(UriList.MIME)
                && (!offer.files().isEmpty() || !offer.links().isEmpty())) {
            return Optional.of(uris().encode());
        }
        if (isText(wanted) && hasText()) {
            return Optional.of(text().getBytes(StandardCharsets.UTF_8));
        }
        var image = offer.image();
        if (wanted.equals(PNG) && image != null) {
            return Optional.of(image.get());
        }
        return Optional.empty();
    }

    /// The files and links as one list, files first.
    public UriList uris() {
        var all = new ArrayList<URI>(UriList.of(offer.files()).uris());
        all.addAll(offer.links());
        return new UriList(all);
    }

    /// The text offered: the offer's own, or its links one per line where it
    /// has none.
    public String text() {
        var text = offer.text();
        if (text != null) {
            return text.get();
        }
        return String.join("\n", offer.links().stream().map(URI::toString).toList());
    }

    private boolean hasText() {
        return offer.text() != null || !offer.links().isEmpty();
    }

    private static boolean isText(String mime) {
        return mime.equals(TEXT)
                || mime.equals("text/plain;charset=utf-8")
                || mime.equals("text/plain; charset=utf-8")
                || mime.equals("utf8_string")
                || mime.equals("string")
                || mime.equals("text");
    }
}
