package dev.goldberry.natives.desktop.dragout.cocoa;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import dev.goldberry.natives.desktop.macos.ObjC;

/// The source class, its pasteboard item and its picture, as the runtime and
/// AppKit call them, without a dragging session: the methods sent to an object
/// of the class, and the item written to a pasteboard of the test's own and
/// read back, which is what makes the data provider provide. macOS only.
@DisplayName("a macOS drag's source object, through the runtime")
@EnabledOnOs(OS.MAC)
@SuppressWarnings("restricted")
class CocoaDraggingSourceMacTest {

    private static ObjC objc;

    /// What the object was asked, in words.
    private static final class Heard implements CocoaDraggingSource.Callbacks {

        final List<String> calls = new ArrayList<>();

        @Override
        public long operationMask(boolean outside) {
            calls.add("mask " + outside);
            return 17;
        }

        @Override
        public void moved(ObjC.Point screen) {
            calls.add("moved " + screen.x() + "," + screen.y());
        }

        @Override
        public void ended(ObjC.Point screen, long operation) {
            calls.add("ended " + screen.x() + "," + screen.y() + " " + operation);
        }

        @Override
        public byte @Nullable [] provide(String type) {
            calls.add("provide " + type);
            return type.equals("public.utf8-plain-text") ? "goldberry".getBytes() : null;
        }

        @Override
        public void finished() {
            calls.add("finished");
        }
    }

    @BeforeAll
    static void runtime() {
        objc = ObjC.get().orElseThrow();
        assertTrue(ObjC.loadFramework("AppKit"));
    }

    @Test
    @DisplayName("answers the dragging source's messages, NSPoints by value included")
    void messages() {
        var heard = new Heard();
        var source = CocoaDraggingSource.create(objc, heard);
        try {
            assertEquals(
                    17,
                    objc.sendLong(
                            source, "draggingSession:sourceOperationMaskForDraggingContext:", MemorySegment.NULL, 0L));
            objc.sendVoid(source, "draggingSession:movedToPoint:", MemorySegment.NULL, new ObjC.Point(1.5, 2.5));
            objc.sendVoid(
                    source, "draggingSession:endedAtPoint:operation:", MemorySegment.NULL, new ObjC.Point(3, 4), 16L);

            assertEquals(List.of("mask true", "moved 1.5,2.5", "ended 3.0,4.0 16"), heard.calls);
        } finally {
            CocoaDraggingSource.dispose(objc, source);
        }
        assertFalse(CocoaDraggingSource.isLive(source));
    }

    @Test
    @DisplayName("provides a pasteboard item's data when a pasteboard is read")
    void pasteboard() {
        var heard = new Heard();
        var source = CocoaDraggingSource.create(objc, heard);
        try {
            objc.autoreleased(() -> {
                var item = CocoaDragItems.pasteboardItem(objc, source, List.of("public.utf8-plain-text"));
                var board = objc.send(objc.cls("NSPasteboard"), "pasteboardWithUniqueName");
                objc.sendLong(board, "clearContents");
                var items = objc.send(objc.cls("NSMutableArray"), "array");
                objc.sendVoid(items, "addObject:", item);
                assertTrue(objc.sendBoolean(board, "writeObjects:", items));

                var data = objc.send(board, "dataForType:", objc.string("public.utf8-plain-text"));
                assertNotEquals(0, data.address());
                var length = objc.sendLong(data, "length");
                var bytes = objc.send(data, "bytes").reinterpret(length).toArray(JAVA_BYTE);
                assertArrayEquals("goldberry".getBytes(), bytes);
                objc.sendVoid(board, "releaseGlobally");
                return null;
            });
            assertTrue(heard.calls.contains("provide public.utf8-plain-text"), heard.calls::toString);
        } finally {
            CocoaDraggingSource.dispose(objc, source);
        }
    }

    @Test
    @DisplayName("makes the picture an NSImage of its size in points")
    void picture() {
        objc.autoreleased(() -> {
            var pixels = ByteBuffer.allocateDirect(8 * 6 * 4);
            var image = CocoaDragItems.image(objc, 8, 6, 32, pixels, 2);
            assertNotEquals(0, image.address());
            var size = objc.sendPoint(image, "size");
            assertEquals(new ObjC.Point(4, 3), size);
            return null;
        });
    }
}
