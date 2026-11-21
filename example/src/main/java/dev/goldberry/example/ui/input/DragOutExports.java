package dev.goldberry.example.ui.input;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goldberry.image.Image;
import dev.goldberry.input.dragout.DragOffer;

/// The offers behind the drag-out card: the picture as a PNG and the note as
/// text, each also written to a file when its drag starts, for the targets that
/// take only files.
///
/// The files go to one folder, made the first time one is needed, and are
/// removed when the application exits. A folder that cannot be written leaves
/// the file out and keeps the rest of the offer.
final class DragOutExports {

    private static final Logger LOG = LoggerFactory.getLogger(DragOutExports.class);

    /// The note's text.
    static final String NOTE = "A note dragged out of the Goldberry showcase.";

    /// The picture's size, in pixels.
    static final int PICTURE_WIDTH = 192;

    static final int PICTURE_HEIGHT = 120;

    private final Supplier<Path> folder;

    private @Nullable Path made;

    private @Nullable Image picture;

    private DragOutExports(Supplier<Path> folder) {
        this.folder = folder;
    }

    /// Offers whose files are written to a temporary folder of their own,
    /// removed at exit.
    static DragOutExports temporary() {
        return new DragOutExports(DragOutExports::temporaryFolder);
    }

    /// Offers whose files are written to `folder`, which exists.
    static DragOutExports in(Path folder) {
        Objects.requireNonNull(folder, "folder");
        return new DragOutExports(() -> folder);
    }

    /// What a drag of `keepsake` carries out of the window.
    DragOffer offer(Keepsake keepsake) {
        return switch (keepsake) {
            case PICTURE -> withFile(DragOffer.image(this::png), keepsake, this::png);
            case NOTE -> withFile(DragOffer.text(NOTE), keepsake, () -> (NOTE + "\n").getBytes(StandardCharsets.UTF_8));
        };
    }

    /// The landscape the card shows and the offer encodes, painted once.
    Image picture() {
        var painted = picture;
        if (painted == null) {
            painted = Landscape.paint(PICTURE_WIDTH, PICTURE_HEIGHT);
            picture = painted;
        }
        return painted;
    }

    private byte[] png() {
        return picture().encodePng();
    }

    /// `offer`, also offering the file `bytes` are written to now, or `offer`
    /// alone when the file cannot be written.
    private DragOffer withFile(DragOffer offer, Keepsake keepsake, Supplier<byte[]> bytes) {
        try {
            var file = folder().resolve(keepsake.fileName());
            Files.write(file, bytes.get());
            file.toFile().deleteOnExit();
            return offer.withFiles(file);
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Could not write {} for a drag out; offering it without the file", keepsake.fileName(), e);
            return offer;
        }
    }

    private Path folder() {
        var known = made;
        if (known == null) {
            known = folder.get();
            made = known;
        }
        return known;
    }

    private static Path temporaryFolder() {
        try {
            var created = Files.createTempDirectory("goldberry-drag-out-");
            // Registered before the files, so it is deleted after them.
            created.toFile().deleteOnExit();
            return created;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
