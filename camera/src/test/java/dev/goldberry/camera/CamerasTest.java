package dev.goldberry.camera;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.camera.backend.CameraStream;
import dev.goldberry.camera.backend.DeviceEvent;
import dev.goldberry.media.picture.VideoPlanes;

/// Cameras listed and opened over a backend the test drives: the mode chosen,
/// frames copied out of what the camera lends, the permission asked, and a
/// camera unplugged.
@DisplayName("cameras")
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class CamerasTest {

    private static final CameraFormat VGA = CameraFormat.of(64, 48, 30);

    private final FakeCameraBackend backend = new FakeCameraBackend();

    /// Waits for `condition`, which the camera's own thread makes true.
    private static void await(BooleanSupplier condition) {
        while (!condition.getAsBoolean()) {
            LockSupport.parkNanos(1_000_000L);
        }
    }

    @Test
    @DisplayName("prefer the first camera facing the user, else the first, and list what the system lists")
    void preferred() {
        backend.devices.add(FakeCameraBackend.camera(1, "Desk", CameraPosition.UNKNOWN, VGA));
        backend.devices.add(FakeCameraBackend.camera(2, "Lid", CameraPosition.FRONT, VGA));
        try (var cameras = new Cameras(backend)) {
            assertEquals(2, cameras.list().size());
            assertEquals("Lid", cameras.preferred().orElseThrow().name());
            backend.devices.remove(1);
            assertEquals("Desk", cameras.preferred().orElseThrow().name());
            backend.devices.clear();
            assertEquals(Optional.empty(), cameras.preferred());
            assertThrows(CameraException.class, () -> cameras.open(CameraSpec.PREVIEW));
        }
        assertTrue(backend.closed);
    }

    @Test
    @DisplayName("open the uncompressed mode nearest the spec, and refuse a camera that only compresses")
    void mode() {
        var compressed = new CameraFormat(1280, 720, 30, 1, true);
        backend.devices.add(FakeCameraBackend.camera(1, "Cam", CameraPosition.UNKNOWN, compressed, VGA));
        backend.devices.add(FakeCameraBackend.camera(2, "Jpeg", CameraPosition.UNKNOWN, compressed));
        try (var cameras = new Cameras(backend);
                var camera = cameras.open(backend.devices.getFirst(), CameraSpec.CALL)) {
            assertEquals(VGA, camera.format());
            assertEquals(VGA, backend.opened.getFirst().mode);
            assertThrows(CameraException.class, () -> cameras.open(backend.devices.get(1), CameraSpec.CALL));
            backend.refuses = true;
            assertThrows(CameraException.class, () -> cameras.open(backend.devices.getFirst(), CameraSpec.CALL));
        }
    }

    @Test
    @DisplayName("copy each lent frame into a picture of their own, and tell the views")
    void frames() throws Exception {
        backend.devices.add(FakeCameraBackend.camera(1, "Cam", CameraPosition.UNKNOWN, VGA));
        try (var cameras = new Cameras(backend);
                var camera = cameras.open(CameraSpec.PREVIEW)) {
            var told = new ArrayList<Integer>();
            try (var _ = camera.onPicture(() -> told.add(1))) {
                var stream = backend.opened.getFirst();
                stream.queue(90);
                await(() -> camera.shownPicture().isPresent());
                var first = assertInstanceOf(
                        VideoPlanes.class, camera.shownPicture().orElseThrow());
                assertEquals(90, first.sample(0, 10, 10));
                assertEquals(64, first.stride(0), "copied without the camera's padding");

                stream.queue(200);
                await(() -> camera.shownPicture().orElseThrow() != first);
                var second = (VideoPlanes) camera.shownPicture().orElseThrow();
                assertAll(
                        () -> assertEquals(200, second.sample(0, 63, 47)),
                        () -> assertEquals(90, first.sample(0, 10, 10), "the picture before is still whole"),
                        () -> assertNotSame(first.plane(0), second.plane(0)),
                        () -> assertTrue(told.size() >= 2));
            }
        }
    }

    @Test
    @DisplayName("ask while the permission is unanswered, and take pictures once it is given")
    void asking() {
        backend.permission = CameraStream.Permission.PENDING;
        backend.devices.add(FakeCameraBackend.camera(7, "Cam", CameraPosition.FRONT, VGA));
        try (var cameras = new Cameras(backend);
                var camera = cameras.open(CameraSpec.PREVIEW)) {
            assertInstanceOf(CameraStatus.Asking.class, camera.status());
            backend.opened.getFirst().queue(50);
            backend.permission = CameraStream.Permission.APPROVED;
            backend.raise(new DeviceEvent.Approved(7));
            await(() -> camera.shownPicture().isPresent());
            assertInstanceOf(CameraStatus.Active.class, camera.status());
        }
    }

    @Test
    @DisplayName("end in Denied when the user refuses, and stay so when closed")
    void denied() {
        backend.permission = CameraStream.Permission.PENDING;
        backend.devices.add(FakeCameraBackend.camera(7, "Cam", CameraPosition.FRONT, VGA));
        try (var cameras = new Cameras(backend)) {
            var camera = cameras.open(CameraSpec.PREVIEW);
            var heard = new ArrayList<CameraStatus>();
            camera.onStatus(heard::add);
            backend.permission = CameraStream.Permission.DENIED;
            backend.raise(new DeviceEvent.Denied(7));
            await(() -> camera.status() instanceof CameraStatus.Denied);
            await(() -> backend.opened.getFirst().closed);
            camera.close();
            assertAll(
                    () -> assertInstanceOf(CameraStatus.Denied.class, camera.status()),
                    () -> assertEquals(List.of(new CameraStatus.Denied()), heard));
        }
    }

    @Test
    @DisplayName("lose a camera that is unplugged, or that stops, and tell the change")
    void unplugged() {
        backend.devices.add(FakeCameraBackend.camera(3, "Cam", CameraPosition.UNKNOWN, VGA));
        backend.devices.add(FakeCameraBackend.camera(4, "Other", CameraPosition.UNKNOWN, VGA));
        try (var cameras = new Cameras(backend)) {
            var changes = new ArrayList<CameraChange>();
            cameras.onChange(changes::add);
            var first = cameras.open(backend.devices.getFirst(), CameraSpec.PREVIEW);
            var second = cameras.open(backend.devices.get(1), CameraSpec.PREVIEW);
            backend.raise(new DeviceEvent.Removed(3));
            await(() -> first.status() instanceof CameraStatus.Lost);
            assertEquals(new CameraStatus.Lost("Cam was unplugged"), first.status());
            assertInstanceOf(CameraStatus.Active.class, second.status(), "only the camera unplugged");

            backend.opened.get(1).fails = "the driver gave up";
            await(() -> second.status() instanceof CameraStatus.Lost);
            assertEquals(new CameraStatus.Lost("the driver gave up"), second.status());

            backend.devices.add(FakeCameraBackend.camera(9, "New", CameraPosition.BACK, VGA));
            backend.raise(new DeviceEvent.Added(9));
            assertEquals(
                    List.of("Removed:Cam", "Added:New"),
                    changes.stream()
                            .map(change -> change.getClass().getSimpleName() + ":"
                                    + change.camera().name())
                            .toList());
            first.close();
            second.close();
        }
    }

    @Test
    @DisplayName("close the stream and say Closed when closed, once")
    void close() {
        backend.devices.add(FakeCameraBackend.camera(1, "Cam", CameraPosition.UNKNOWN, VGA));
        try (var cameras = new Cameras(backend)) {
            var camera = cameras.open(CameraSpec.PREVIEW);
            var heard = new ArrayList<CameraStatus>();
            camera.onStatus(heard::add);
            camera.close();
            camera.close();
            assertAll(
                    () -> assertTrue(backend.opened.getFirst().closed),
                    () -> assertEquals(List.of(new CameraStatus.Closed()), heard));
        }
    }
}
