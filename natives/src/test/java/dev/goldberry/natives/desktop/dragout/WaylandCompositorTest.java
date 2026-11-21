package dev.goldberry.natives.desktop.dragout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.natives.wayland.Descriptors;
import dev.goldberry.natives.wayland.WaylandArguments;
import dev.goldberry.natives.wayland.WaylandClient;
import dev.goldberry.natives.wayland.WaylandQueue;

/// What a drag out makes, against a real compositor: the queue and its
/// registry, a data source offering types and actions, and the icon in a
/// shared-memory buffer, all accepted without a protocol error.
///
/// On a connection of the test's own to `$WAYLAND_DISPLAY`, so it runs on a
/// Wayland desktop or beside CI's headless weston, and is skipped elsewhere.
/// `start_drag` itself needs the serial of a real press, which no test here
/// makes; that half is the hand check the plan's log records.
@DisplayName("a drag out's objects, on a real compositor")
@Timeout(30)
class WaylandCompositorTest {

    private static final int MANAGER_CREATE_DATA_SOURCE = 0;
    private static final int SOURCE_OFFER = 0;
    private static final int SOURCE_DESTROY = 1;
    private static final int SOURCE_SET_ACTIONS = 2;

    private WaylandClient client;
    private MemorySegment display = MemorySegment.NULL;

    @BeforeEach
    void connect() {
        assumeTrue(System.getenv("WAYLAND_DISPLAY") != null, "no Wayland compositor here");
        var found = WaylandClient.get();
        assumeTrue(found.isPresent(), "no libwayland-client");
        client = found.get();
        display = client.connect(null);
        assumeTrue(display.address() != 0, "the compositor did not answer");
    }

    @AfterEach
    void disconnect() {
        if (display.address() != 0) {
            client.disconnect(display);
        }
    }

    @Test
    @DisplayName("announces the globals a drag needs on a queue of its own")
    void globals() {
        try (var queue = WaylandQueue.open(client, display).orElseThrow()) {
            assertTrue(queue.global("wl_data_device_manager").isPresent());
            assertTrue(queue.global("wl_compositor").isPresent());
            assertTrue(queue.global("wl_shm").isPresent());
        }
    }

    @Test
    @DisplayName("offers types and actions on a data source without an error")
    void source() {
        try (var queue = WaylandQueue.open(client, display).orElseThrow()) {
            var global = queue.global("wl_data_device_manager").orElseThrow();
            assumeTrue(global.version() >= WaylandDragSource.MANAGER_VERSION, "an old data device manager");
            var manager = queue.bind(global, "wl_data_device_manager_interface", WaylandDragSource.MANAGER_VERSION);
            var source = queue.create(
                    manager, MANAGER_CREATE_DATA_SOURCE, "wl_data_source_interface", 1, WaylandArguments::newId);
            for (var type : new String[] {"text/uri-list", "image/png", "application/x-goldberry-test"}) {
                queue.send(source, SOURCE_OFFER, 1, arguments -> arguments.string(type));
            }
            queue.send(
                    source,
                    SOURCE_SET_ACTIONS,
                    1,
                    arguments -> arguments.unsigned(WaylandSourceEvents.DND_COPY | WaylandSourceEvents.DND_MOVE));
            queue.roundtrip();

            assertEquals(0, queue.error());
            queue.destroy(source, SOURCE_DESTROY);
            queue.forget(manager);
            queue.roundtrip();
            assertEquals(0, queue.error());
        }
    }

    @Test
    @DisplayName("puts a picture in a shared-memory buffer on a surface without an error")
    void icon() {
        assumeTrue(Descriptors.isAvailable(), "no memfd_create");
        try (var queue = WaylandQueue.open(client, display).orElseThrow()) {
            var pixels = ByteBuffer.allocateDirect(16 * 8 * 4);
            var icon = WaylandIcon.make(queue, new DragIcon(16, 8, 64, pixels, 3, 2, 2))
                    .orElseThrow();
            icon.show();
            queue.roundtrip();

            assertEquals(0, queue.error());
            assertEquals(2, icon.scale());
            icon.close();
            queue.roundtrip();
            assertEquals(0, queue.error());
        }
    }

    @Test
    @DisplayName("shows a picture whose sides the scale does not divide at scale 1")
    void oddPicture() {
        assumeTrue(Descriptors.isAvailable(), "no memfd_create");
        try (var queue = WaylandQueue.open(client, display).orElseThrow()) {
            var pixels = ByteBuffer.allocateDirect(15 * 7 * 4);
            var icon = WaylandIcon.make(queue, new DragIcon(15, 7, 60, pixels, 0, 0, 2))
                    .orElseThrow();
            icon.show();
            queue.roundtrip();

            assertEquals(1, icon.scale());
            assertEquals(0, queue.error());
            icon.close();
        }
    }
}
