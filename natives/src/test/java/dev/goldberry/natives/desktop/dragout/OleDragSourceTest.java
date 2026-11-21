package dev.goldberry.natives.desktop.dragout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.desktop.dragout.ole.OleDataObject;
import dev.goldberry.natives.desktop.dragout.ole.OleStructs;

/// The parts of [OleDragSource] that do not need Windows: formats made
/// numbers, actions made effects, and a refusal off Windows.
@DisplayName("a Windows drag source")
class OleDragSourceTest {

    @Test
    @DisplayName("registers named formats in order, drops one the system refuses, and offers a format once")
    void resolves() {
        var formats = List.of(
                OleFormat.standard(OleFormat.FILES, () -> new byte[] {1}),
                OleFormat.registered("PNG", () -> new byte[] {2}),
                OleFormat.registered("refused", () -> new byte[] {3}),
                OleFormat.standard(OleFormat.FILES, () -> new byte[] {4}));

        var offered = OleDragSource.resolve(
                formats, name -> name.equals("PNG") ? OptionalInt.of(0xC0FE) : OptionalInt.empty());

        assertEquals(
                List.of(OleFormat.FILES, 0xC0FE),
                offered.stream().map(OleDataObject.Offered::format).toList());
        assertArrayEquals(new byte[] {1}, offered.getFirst().bytes().get(), "the first of two, as given");
    }

    @Test
    @DisplayName("allows the effects of every action offered")
    void effects() {
        assertEquals(OleStructs.DROPEFFECT_COPY, OleDragSource.effects(Set.of(DragAction.COPY)));
        assertEquals(
                OleStructs.DROPEFFECT_COPY | OleStructs.DROPEFFECT_MOVE | OleStructs.DROPEFFECT_LINK,
                OleDragSource.effects(EnumSet.allOf(DragAction.class)));
    }

    @Test
    @DisplayName("is a standard format or a registered one, never both")
    void formats() {
        assertEquals("CF_15", OleFormat.standard(15, () -> new byte[0]).label());
        assertEquals("PNG", OleFormat.registered("PNG", () -> new byte[0]).label());
        assertThrows(IllegalArgumentException.class, () -> new OleFormat(15, "PNG", () -> new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new OleFormat(0, null, () -> new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> OleFormat.standard(0, () -> new byte[0]));
    }

    @Test
    @DisplayName("is not offered off Windows")
    void onlyOnWindows() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return;
        }
        var opened = OleDragSource.open(
                List.of(OleFormat.standard(OleFormat.UNICODE_TEXT, () -> new byte[] {0, 0})),
                Set.of(DragAction.COPY),
                null,
                window -> true,
                new DragSourceListener() {
                    @Override
                    public void over(long window, int x, int y) {}

                    @Override
                    public void outside() {}

                    @Override
                    public void delivered(@Nullable DragAction action) {}

                    @Override
                    public void releasedOver(long window, int x, int y) {}

                    @Override
                    public void cancelled() {}
                },
                () -> {});

        assertTrue(opened.isEmpty());
    }
}
