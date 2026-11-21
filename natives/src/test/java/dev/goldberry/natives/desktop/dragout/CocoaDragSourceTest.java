package dev.goldberry.natives.desktop.dragout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The parts of [CocoaDragSource] that do not need a Mac: pasteboard types
/// resolved, an origin checked, and a refusal elsewhere.
@DisplayName("a macOS drag source")
class CocoaDragSourceTest {

    @Test
    @DisplayName("offers identifiers as given and MIME types as the system names them, each once")
    void provided() {
        var provided = CocoaDragSource.provided(
                List.of(
                        PasteboardFormat.uti(PasteboardFormat.TEXT, () -> new byte[] {1}),
                        PasteboardFormat.mime("application/x-board", () -> new byte[] {2}),
                        PasteboardFormat.uti(PasteboardFormat.TEXT, () -> new byte[] {3})),
                mime -> "dyn.board");

        assertEquals(List.of(PasteboardFormat.TEXT, "dyn.board"), List.copyOf(provided.keySet()));
        assertArrayEquals(new byte[] {1}, provided.get(PasteboardFormat.TEXT).get(), "the first of two, as given");
    }

    @Test
    @DisplayName("is an offer of something")
    void origin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CocoaDragSource.Origin(
                        1, 0, 0, 100, Set.of(), List.of(), List.of(), Set.of(DragAction.COPY), null));
        assertThrows(IllegalArgumentException.class, () -> PasteboardFormat.uti(" ", () -> new byte[0]));
    }

    @Test
    @DisplayName("is not offered off macOS")
    void onlyOnMac() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return;
        }
        var started = CocoaDragSource.start(
                new CocoaDragSource.Origin(
                        1,
                        0,
                        0,
                        100,
                        Set.of(1L),
                        List.of(Path.of("/tmp/a.txt")),
                        List.of(),
                        Set.of(DragAction.COPY),
                        null),
                new CocoaDragListener() {
                    @Override
                    public void over(long window, double x, double yFromBottom) {}

                    @Override
                    public void outside() {}

                    @Override
                    public void delivered(@Nullable DragAction action) {}

                    @Override
                    public void releasedOver(long window, double x, double yFromBottom) {}

                    @Override
                    public void cancelled() {}
                });

        assertTrue(started.isEmpty());
    }
}
