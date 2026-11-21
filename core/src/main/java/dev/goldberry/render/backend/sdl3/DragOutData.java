package dev.goldberry.render.backend.sdl3;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.input.dragout.DragOperation;
import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.DragAction;
import dev.goldberry.natives.desktop.dragout.DragData;
import dev.goldberry.render.dragout.OfferedData;

/// An offer as the platforms' drag sources take it, the same on X11 and
/// Wayland: types and bytes, the actions in the platform's words, and an
/// image written to a file for the targets that take only files.
final class DragOutData {

    private static final Logger LOG = Logs.of(DragOutData.class);

    /// The old X11 name for UTF-8 text, which X11 clients ask by and Wayland
    /// toolkits that bridge to X11 (GTK, SDL) still accept.
    static final String UTF8_STRING = "UTF8_STRING";

    private DragOutData() {}

    /// The offer's types and bytes, with `UTF8_STRING` beside UTF-8 text.
    static DragData data(DragOffer offer) {
        var offered = OfferedData.of(offer);
        var types = new ArrayList<>(offered.types());
        if (types.contains(OfferedData.TEXT_UTF8)) {
            types.add(types.indexOf(OfferedData.TEXT) + 1, UTF8_STRING);
        }
        var list = List.copyOf(types);
        return new DragData() {
            @Override
            public List<String> types() {
                return list;
            }

            @Override
            public byte @Nullable [] bytes(String mime) {
                return offered.bytes(mime).orElse(null);
            }
        };
    }

    /// What a target may do, in the platform's words.
    static Set<DragAction> actions(DragOffer offer) {
        var actions = EnumSet.noneOf(DragAction.class);
        for (var operation : offer.operations()) {
            actions.add(
                    switch (operation) {
                        case COPY -> DragAction.COPY;
                        case MOVE -> DragAction.MOVE;
                        case LINK -> DragAction.LINK;
                    });
        }
        return actions;
    }

    /// What the target did, in the toolkit's words, or null where it did not say.
    static @Nullable DragOperation operation(@Nullable DragAction action) {
        return action == null
                ? null
                : switch (action) {
                    case COPY -> DragOperation.COPY;
                    case MOVE -> DragOperation.MOVE;
                    case LINK -> DragOperation.LINK;
                };
    }

    /// `offer`, with its image also written to a PNG file, for the targets that
    /// take only files. The file is made once per drag and goes when the
    /// process exits.
    static DragOffer withImageFile(DragOffer offer) {
        var image = offer.image();
        if (image == null) {
            return offer;
        }
        try {
            var file = Files.createTempFile("goldberry-drag-", ".png");
            Files.write(file, image.get());
            file.toFile().deleteOnExit();
            return offer.withFiles(file);
        } catch (IOException | RuntimeException e) {
            LOG.warn("a drag's image could not be written to a file; it is offered as image/png only", e);
            return offer;
        }
    }
}
