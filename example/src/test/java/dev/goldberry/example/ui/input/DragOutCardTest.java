package dev.goldberry.example.ui.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.RendererRequirement;
import dev.goldberry.image.Image;
import dev.goldberry.input.dragout.DragOperation;
import dev.goldberry.input.dragout.DragOutcome;

/// The drag-out card: what it offers, what it says, and the in-app half,
/// driven through a session. The desktop's half is the backends' and is
/// tested there.
@DisplayName("the drag-out card")
class DragOutCardTest {

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Nested
    @DisplayName("in a session")
    class InASession {

        @Test
        @DisplayName("the picture dragged onto the tray stays in the window, and the tray says it holds it")
        void pictureOntoTheTray() {
            try (var card = new CardSession(new DragOutCard())) {
                assertEquals(
                        DragOutCard.holding(Set.of()), card.text(DragOutCard.TRAY_READOUT), "the tray starts empty");

                card.session.drag(DragOutCard.idOf(Keepsake.PICTURE), DragOutCard.TRAY);

                assertEquals("The tray holds the picture.", card.text(DragOutCard.TRAY_READOUT));
                assertTrue(
                        card.text(DragOutCard.READOUT).startsWith("Drag the picture"),
                        "a drag that never left hears no outcome: " + card.text(DragOutCard.READOUT));
            }
        }

        @Test
        @DisplayName("both dragged onto the tray, it holds both, in the order the enum names them")
        void bothOntoTheTray() {
            try (var card = new CardSession(new DragOutCard())) {
                card.session.drag(DragOutCard.idOf(Keepsake.NOTE), DragOutCard.TRAY);
                card.session.drag(DragOutCard.idOf(Keepsake.PICTURE), DragOutCard.TRAY);

                assertEquals("The tray holds the picture and the note.", card.text(DragOutCard.TRAY_READOUT));
            }
        }

        @Test
        @DisplayName("the picture is painted and both tiles name their files")
        void tiles() {
            try (var card = new CardSession(new DragOutCard())) {
                assertTrue(card.rect("drag-out-art").height() > 0, "the picture has room");
                assertTrue(card.texts(DragOutCard.idOf(Keepsake.PICTURE)).contains("goldberry-picture.png"));
                assertTrue(card.texts(DragOutCard.idOf(Keepsake.NOTE)).contains("goldberry-note.txt"));
            }
        }
    }

    @Nested
    @DisplayName("the offers")
    class Offers {

        @TempDir
        Path folder;

        @Test
        @DisplayName("the picture is a PNG, and the same PNG written to a file beside it")
        void picture() throws IOException {
            var offer = DragOutExports.in(folder).offer(Keepsake.PICTURE);

            var image = offer.image();
            assertNotNull(image);
            var png = image.get();
            var decoded = Image.decode(png);
            assertEquals(DragOutExports.PICTURE_WIDTH, decoded.width());
            assertEquals(DragOutExports.PICTURE_HEIGHT, decoded.height());
            assertEquals(List.of(folder.resolve("goldberry-picture.png").toAbsolutePath()), offer.files());
            assertArrayEquals(png, Files.readAllBytes(offer.files().getFirst()));
            assertNull(offer.text());
        }

        @Test
        @DisplayName("the note is text, and a .txt file holding the same line")
        void note() throws IOException {
            var offer = DragOutExports.in(folder).offer(Keepsake.NOTE);

            var text = offer.text();
            assertNotNull(text);
            assertEquals(DragOutExports.NOTE, text.get());
            assertEquals(
                    DragOutExports.NOTE + "\n", Files.readString(offer.files().getFirst(), StandardCharsets.UTF_8));
            assertNull(offer.image());
        }

        @Test
        @DisplayName("a folder that cannot be written leaves the file out and keeps the rest")
        void unwritable() {
            var offer = DragOutExports.in(folder.resolve("missing")).offer(Keepsake.NOTE);

            assertEquals(List.of(), offer.files());
            assertNotNull(offer.text());
        }

        @Test
        @DisplayName("each offer is a copy only")
        void copyOnly() {
            var exports = DragOutExports.in(folder);
            for (var keepsake : Keepsake.values()) {
                assertEquals(
                        EnumSet.of(DragOperation.COPY), exports.offer(keepsake).operations());
            }
        }
    }

    @Nested
    @DisplayName("the readouts")
    class Readouts {

        @Test
        @DisplayName("every ending the source can hear is a sentence about what was dragged")
        void endings() {
            assertEquals(
                    "The picture went to another application, which made a copy.",
                    DragOutCard.describe(Keepsake.PICTURE, new DragOutcome.Delivered(DragOperation.COPY)));
            assertEquals(
                    "The note went to another application, which says it moved it; the showcase keeps its own.",
                    DragOutCard.describe(Keepsake.NOTE, new DragOutcome.Delivered(DragOperation.MOVE)));
            assertEquals(
                    "The note went to another application, which linked to it.",
                    DragOutCard.describe(Keepsake.NOTE, new DragOutcome.Delivered(DragOperation.LINK)));
            assertEquals(
                    "The picture went to another application, which did not say what it did with it.",
                    DragOutCard.describe(Keepsake.PICTURE, new DragOutcome.Delivered(null)));
            assertEquals(
                    "The picture came back over this window and was dropped on the tray.",
                    DragOutCard.describe(Keepsake.PICTURE, new DragOutcome.Landed(true)));
            assertEquals(
                    "The note came back over this window and was let go where nothing takes it.",
                    DragOutCard.describe(Keepsake.NOTE, new DragOutcome.Landed(false)));
            assertEquals("The note was put back.", DragOutCard.describe(Keepsake.NOTE, new DragOutcome.Cancelled()));
        }

        @Test
        @DisplayName("the tray names what it holds")
        void tray() {
            assertTrue(DragOutCard.holding(Set.of()).startsWith("The tray. Drop"));
            assertEquals("The tray holds the note.", DragOutCard.holding(EnumSet.of(Keepsake.NOTE)));
        }
    }

    @Nested
    @DisplayName("the picture")
    class Picture {

        @Test
        @DisplayName("sky at the top, the sun at its centre, a hill at the bottom, all opaque")
        void painted() {
            var image = Landscape.paint(192, 120);

            assertEquals(0xFF5E81AC, image.argb(0, 0), "the top of the sky");
            assertEquals(0xFFEBCB8B, image.argb((int) (192 * 0.72), (int) (120 * 0.36)), "the sun");
            assertEquals(0xFFA3BE8C, image.argb(96, 119), "the near hill");
            for (var y = 0; y < image.height(); y += 7) {
                for (var x = 0; x < image.width(); x += 7) {
                    assertEquals(0xFF, image.argb(x, y) >>> 24, "opaque at " + x + "," + y);
                }
            }
        }

        @Test
        @DisplayName("a colour mixed halfway is halfway in every channel, and the ends are the ends")
        void mix() {
            assertEquals(0xFF000000, Landscape.mix(0xFF000000, 0xFFFFFFFF, 0));
            assertEquals(0xFFFFFFFF, Landscape.mix(0xFF000000, 0xFFFFFFFF, 1.5));
            assertEquals(0xFF808080, Landscape.mix(0xFF000000, 0xFFFFFFFF, 0.5));
        }
    }
}
