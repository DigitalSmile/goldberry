package dev.goldberry.render.backend.sdl3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.natives.desktop.dragout.PasteboardFormat;
import dev.goldberry.render.dragout.OfferedData;

/// An offer in macOS pasteboard types, the way Finder, browsers and Mail read
/// a drag. The files go as file URLs, one dragging item each, and are not
/// here. On one pasteboard item, in order:
///
/// - the first link as `public.url`, which a browser takes as a link;
/// - text, or the links one per line where there is none, as
///   `public.utf8-plain-text`;
/// - the image as `public.png`;
/// - the application's own MIME types, each under the identifier the system
///   has for it.
///
/// Everything is made when a target asks for it.
final class MacDragData {

    private MacDragData() {}

    /// The pasteboard types of `offer`, most specific first.
    static List<PasteboardFormat> formats(DragOffer offer) {
        var formats = new ArrayList<PasteboardFormat>();
        var data = OfferedData.of(offer);
        if (!offer.links().isEmpty()) {
            var link = offer.links().getFirst().toString();
            formats.add(PasteboardFormat.uti(PasteboardFormat.URL, () -> link.getBytes(StandardCharsets.UTF_8)));
        }
        if (offer.text() != null || !offer.links().isEmpty()) {
            formats.add(PasteboardFormat.uti(
                    PasteboardFormat.TEXT, () -> data.text().getBytes(StandardCharsets.UTF_8)));
        }
        var image = offer.image();
        if (image != null) {
            formats.add(PasteboardFormat.uti(PasteboardFormat.PNG, image));
        }
        for (var entry : offer.data().entrySet()) {
            formats.add(PasteboardFormat.mime(entry.getKey(), entry.getValue()));
        }
        return formats;
    }
}
