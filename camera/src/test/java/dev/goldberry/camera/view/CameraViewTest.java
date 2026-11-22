package dev.goldberry.camera.view;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.goldberry.camera.CameraFormat;
import dev.goldberry.camera.CameraInfo;
import dev.goldberry.camera.CameraPosition;
import dev.goldberry.camera.CameraStatus;
import dev.goldberry.camera.synthetic.SyntheticCamera;
import dev.goldberry.i18n.ToolkitLabel;
import dev.goldberry.junit.HeadlessRuntime;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.media.view.PictureView;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.image.Fit;
import dev.goldberry.widgets.markup.Named;
import dev.goldberry.widgets.markup.Wiring;
import dev.goldberry.widgets.text.Text;

/// `camera-view` as an application holds it: inflated from markup, and built
/// for each state its camera is in.
@DisplayName("camera-view")
@ExtendWith(HeadlessRuntime.class)
class CameraViewTest {

    private static List<Element> walk(Element from) {
        var all = new ArrayList<Element>();
        all.add(from);
        for (var child : from.children()) {
            all.addAll(walk(child));
        }
        return all;
    }

    private static <T> T built(ElementTree tree, Class<T> type) {
        tree.flush();
        return walk(tree.root()).stream()
                .map(Element::widget)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("built")
    class Built {

        @Test
        @DisplayName("shows an active camera's pictures, mirrored for one that faces the user")
        void active() {
            var camera = SyntheticCamera.of(64, 48);
            var tree = new ElementTree(new CameraView(camera));
            var box = built(tree, CameraBox.class);
            var picture = built(tree, PictureView.class);
            assertAll(
                    () -> assertEquals("camera-view", box.cssType()),
                    () -> assertEquals(Set.of("active", "mirrored"), box.classes()),
                    () -> assertInstanceOf(MirroredPictures.class, picture.source()),
                    () -> assertEquals(Fit.COVER, picture.fit()));
            tree.unmount();
        }

        @Test
        @DisplayName("shows the camera's own pictures with the mirror off, or for one facing away")
        void unmirrored() {
            var camera = SyntheticCamera.of(64, 48);
            var off = new ElementTree(new CameraView(camera, Fit.CONTAIN, Mirror.OFF, Attributes.NONE));
            assertSame(camera, built(off, PictureView.class).source());
            off.unmount();

            var format = CameraFormat.of(64, 48, 30);
            var back = new SyntheticCamera(
                    new CameraInfo(1, "Back", CameraPosition.BACK, List.of(format)), format, new CameraStatus.Active());
            var auto = new ElementTree(new CameraView(back));
            assertFalse(built(auto, CameraBox.class).classes().contains("mirrored"));
            auto.unmount();
        }

        @Test
        @DisplayName("says what the camera is doing instead of a picture, with its state as a class")
        void states() {
            var asking = SyntheticCamera.asking(64, 48);
            var lost = SyntheticCamera.of(64, 48);
            lost.lose("gone");
            var closed = SyntheticCamera.of(64, 48);
            closed.close();
            var denied = SyntheticCamera.asking(64, 48);
            denied.deny();
            for (var expected : List.of(
                    new Object[] {asking, "asking", ToolkitLabel.CAMERA_ASKING},
                    new Object[] {denied, "denied", ToolkitLabel.CAMERA_DENIED},
                    new Object[] {lost, "lost", ToolkitLabel.CAMERA_LOST},
                    new Object[] {closed, "off", ToolkitLabel.CAMERA_OFF},
                    new Object[] {null, "none", ToolkitLabel.CAMERA_NONE})) {
                var tree = new ElementTree(new CameraView((SyntheticCamera) expected[0]));
                var box = built(tree, CameraBox.class);
                var text = built(tree, Text.class);
                assertAll(
                        () -> assertTrue(
                                box.classes().contains(expected[1]),
                                () -> box.classes().toString()),
                        () -> assertEquals(((ToolkitLabel) expected[2]).english(), text.content()),
                        () -> assertTrue(text.attributes().classes().contains("camera-message")));
                tree.unmount();
            }
        }

        @Test
        @DisplayName("listens to its camera while mounted, and lets go when unmounted")
        void listens() {
            var camera = SyntheticCamera.asking(64, 48);
            var tree = new ElementTree(new CameraView(camera, Fit.COVER, Mirror.AUTO, Attributes.NONE.classes("a")));
            built(tree, Text.class);
            camera.approve();
            tree.update(new CameraView(camera, Fit.COVER, Mirror.AUTO, Attributes.NONE.classes("b")));
            assertTrue(built(tree, CameraBox.class).classes().contains("active"));
            tree.unmount();
        }
    }

    @Nested
    @DisplayName("in markup")
    class Markup {

        private final SyntheticCamera front = SyntheticCamera.of(64, 48);
        private final Wiring wiring = Wiring.none().with(Named.strict().bind("front", front));

        private CameraView inflate(String kdl, Wiring with) {
            var node = KdlParser.parse(kdl).getFirst();
            return (CameraView) CameraView.inflate(node, List.<Widget>of(), with);
        }

        @Test
        @DisplayName("takes the camera it names, its fit and its mirror")
        void named() {
            var view = inflate("camera-view camera=\"front\" fit=\"contain\" mirror=\"off\"", wiring);
            assertAll(
                    () -> assertSame(front, view.camera()),
                    () -> assertEquals(Fit.CONTAIN, view.fit()),
                    () -> assertEquals(Mirror.OFF, view.mirror()));
            var plain = inflate("camera-view camera=\"front\"", wiring);
            assertEquals(Fit.COVER, plain.fit());
            assertEquals(Mirror.AUTO, plain.mirror());
        }

        @Test
        @DisplayName("shows no camera where nothing is bound to the name, and refuses what it cannot be")
        void refuses() {
            assertNull(inflate("camera-view camera=\"front\"", Wiring.none()).camera());
            var node = KdlParser.parse("camera-view").getFirst();
            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> CameraView.inflate(node, List.of(new Text("x")), wiring)),
                    () -> assertThrows(
                            IllegalArgumentException.class, () -> inflate("camera-view mirror=\"sideways\"", wiring)),
                    () -> assertThrows(
                            IllegalArgumentException.class, () -> inflate("camera-view fit=\"zoom\"", wiring)));
        }
    }
}
