package dev.goldberry.camera.synthetic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.camera.CameraFormat;
import dev.goldberry.camera.CameraInfo;
import dev.goldberry.camera.CameraPosition;
import dev.goldberry.camera.CameraStatus;
import dev.goldberry.media.picture.VideoPlanes;

@DisplayName("a synthetic camera")
class SyntheticCameraTest {

    private static VideoPlanes shown(SyntheticCamera camera) {
        return (VideoPlanes) camera.shownPicture().orElseThrow();
    }

    @Test
    @DisplayName("takes a picture per frame of the time it is advanced by, carrying the rest over")
    void advances() {
        var camera = SyntheticCamera.of(64, 48);
        var told = new AtomicInteger();
        camera.onPicture(told::incrementAndGet);
        assertTrue(camera.shownPicture().isEmpty());
        assertEquals(3, camera.advance(Duration.ofMillis(100)));
        assertEquals(0, camera.advance(Duration.ofMillis(30)), "a little under a frame is owed");
        assertEquals(1, camera.advance(Duration.ofMillis(5)));
        assertAll(
                () -> assertEquals(4, camera.frames()),
                () -> assertEquals(4, told.get()),
                () -> assertEquals(3 * 33_333_333L, shown(camera).ptsNanos(), 1));
    }

    @Test
    @DisplayName("paints bars over a ramp in BT.709 limited range, the same for the same picture number")
    void card() {
        var camera = SyntheticCamera.of(70, 60);
        camera.advance(Duration.ofMillis(34));
        var picture = shown(camera);
        assertAll(
                () -> assertEquals(235, picture.sample(0, 2, 2), "white"),
                () -> assertEquals(32, picture.sample(0, 66, 2), "blue"),
                () -> assertEquals(16, picture.sample(0, 0, 55), "the ramp's black end"),
                () -> assertEquals(235, picture.sample(0, 69, 55), "and its white end"),
                () -> assertEquals(240, picture.sample(1, 68, 0), "blue's Cb"),
                () -> assertEquals(118, picture.sample(1, 69, 0), "blue's Cr"));

        var again = SyntheticCamera.of(70, 60);
        again.advance(Duration.ofMillis(10));
        again.advance(Duration.ofMillis(24));
        assertEquals(
                List.of(picture.sample(0, 30, 20), picture.sample(0, 5, 20)),
                List.of(shown(again).sample(0, 30, 20), shown(again).sample(0, 5, 20)));
    }

    @Test
    @DisplayName("moves its square across the bars")
    void moves() {
        var camera = SyntheticCamera.of(100, 60);
        camera.advance(Duration.ofMillis(34));
        var before = row(shown(camera), 20);
        camera.advance(Duration.ofMillis(500));
        assertNotEquals(before, row(shown(camera), 20));
    }

    private static List<Integer> row(VideoPlanes picture, int y) {
        var row = new ArrayList<Integer>();
        for (var x = 0; x < picture.width(); x++) {
            row.add(picture.sample(0, x, y));
        }
        return row;
    }

    @Test
    @DisplayName("takes no pictures until approved, none once denied, lost or closed, and says each change")
    void status() {
        var camera = SyntheticCamera.asking(32, 24);
        var heard = new ArrayList<CameraStatus>();
        camera.onStatus(heard::add);
        assertEquals(0, camera.advance(Duration.ofSeconds(1)));
        camera.approve();
        assertEquals(30, camera.advance(Duration.ofSeconds(1)));
        camera.lose("pulled out");
        camera.approve();
        camera.close();
        assertAll(
                () -> assertEquals(0, camera.advance(Duration.ofSeconds(1))),
                () -> assertEquals(List.of(new CameraStatus.Active(), new CameraStatus.Lost("pulled out")), heard),
                () -> assertInstanceOf(CameraStatus.Lost.class, camera.status()));

        var refused = SyntheticCamera.asking(32, 24);
        refused.deny();
        assertInstanceOf(CameraStatus.Denied.class, refused.status());
    }

    @Test
    @DisplayName("faces the user, and refuses a compressed format")
    void describes() {
        assertEquals(CameraPosition.FRONT, SyntheticCamera.of(32, 24).info().position());
        var jpeg = new CameraFormat(32, 24, 30, 1, true);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyntheticCamera(
                        new CameraInfo(0, "", CameraPosition.BACK, List.of(jpeg)), jpeg, new CameraStatus.Active()));
    }
}
