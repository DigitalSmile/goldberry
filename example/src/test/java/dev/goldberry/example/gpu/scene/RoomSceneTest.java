package dev.goldberry.example.gpu.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.raytrace.Vector3;

/// The ray-traced room's pieces that need no device: its settings, its meshes,
/// where its solids are, and the one camera both renderers look through.
/// Read more: [The renderer](https://goldberry.dev/docs/components/gpu.html#the-renderer).
@DisplayName("the ray-traced room")
class RoomSceneTest {

    @Nested
    @DisplayName("its settings")
    class Settings {

        @Test
        @DisplayName("start untraced, with every traced effect on, one sample and no anisotropy")
        void defaults() {
            var settings = SceneSettings.DEFAULT;
            assertEquals(false, settings.rayTracing());
            assertTrue(settings.shadows() && settings.occlusion() && settings.reflections());
            assertEquals(1, settings.samples());
            assertEquals(1, settings.anisotropy());
            assertEquals(
                    new SceneSettings(true, false, true, false, 4, 16),
                    settings.withRayTracing(true)
                            .withShadows(false)
                            .withReflections(false)
                            .withSamples(4)
                            .withAnisotropy(16)
                            .withOcclusion(true));
        }

        @Test
        @DisplayName("refuse a sample count or an anisotropy the panel does not offer")
        void refuse() {
            assertThrows(IllegalArgumentException.class, () -> SceneSettings.DEFAULT.withSamples(3));
            assertThrows(IllegalArgumentException.class, () -> SceneSettings.DEFAULT.withAnisotropy(2));
        }
    }

    @Nested
    @DisplayName("its meshes")
    class Meshes {

        @Test
        @DisplayName("are triangles counter-clockwise from outside, each vertex's normal on the face's side")
        void outward() {
            for (var mesh : new RoomScene().meshes()) {
                var traced = mesh.traced();
                assertEquals(mesh.vertexCount(), traced.vertexCount());
                assertEquals(mesh.vertexCount() / 3, traced.triangleCount());
                var floats = mesh.bytes().asFloatBuffer();
                for (var t = 0; t < traced.triangleCount(); t++) {
                    var a = traced.corner(t, 0);
                    var face = traced.corner(t, 1)
                            .minus(a)
                            .cross(traced.corner(t, 2).minus(a));
                    assertTrue(face.length() > 0, mesh + " triangle " + t + " has an area");
                    for (var corner = 0; corner < 3; corner++) {
                        var at = (t * 3 + corner) * Mesh.FLOATS + 3;
                        var normal = new Vector3(floats.get(at), floats.get(at + 1), floats.get(at + 2));
                        assertTrue(face.dot(normal) > 0, "triangle " + t + " of " + traced + " faces its normals");
                    }
                }
            }
        }

        @Test
        @DisplayName("have the sizes the room is built from: 36 for a cube, 6 for the floor, a sphere's slices")
        void sizes() {
            assertEquals(36, Mesh.cube().vertexCount());
            assertEquals(6, Mesh.floor(2).vertexCount());
            assertEquals(8 * 3 * 2 * 3, Mesh.sphere(8, 4).vertexCount(), "two triangles a cell, one at each pole");
        }
    }

    @Nested
    @DisplayName("its solids")
    class Solids {

        @Test
        @DisplayName("each have their material's index as their id, once each")
        void idsAreMaterials() {
            var placed = new RoomScene().at(0);
            var ids = new HashSet<Integer>();
            for (var solid : placed) {
                assertTrue(solid.id() >= 0 && solid.id() < RoomScene.MATERIALS.length, "id " + solid.id());
                assertTrue(ids.add(solid.id()), "id " + solid.id() + " once");
            }
            assertEquals(RoomScene.MATERIALS.length, placed.size());
            var geometry = RoomScene.geometry(placed);
            assertEquals(placed.size(), geometry.instances().size());
            assertEquals(3, geometry.meshes().size(), "the floor, the cube and the sphere, each built once");
        }

        @Test
        @DisplayName("move with time: the mirror turns and the small cube circles, and the rest stand")
        void moving() {
            var scene = new RoomScene();
            var now = scene.at(0);
            var later = scene.at(1);
            for (var i = 0; i < now.size(); i++) {
                var moved = !Arrays.equals(now.get(i).model(), later.get(i).model());
                var id = now.get(i).id();
                assertEquals(id == RoomScene.MIRROR || id == RoomScene.ORBIT, moved, "solid " + id);
            }
        }
    }

    @Test
    @DisplayName("is seen through one camera: a point the rasterizer puts in a pixel lies on that pixel's traced ray")
    void oneCamera() {
        var width = 480;
        var height = 300;
        var viewProjection = RoomScene.viewProjection((double) width / height);
        var camera = RoomScene.camera();
        for (var point : new float[][] {{0, 0.7f, 0}, {-1.3f, 1.1f, 0.4f}, {2.5f, 0, 2}, {-3, 0.2f, -2}}) {
            var clip = new float[4];
            for (var row = 0; row < 4; row++) {
                clip[row] = viewProjection[row * 4] * point[0]
                        + viewProjection[row * 4 + 1] * point[1]
                        + viewProjection[row * 4 + 2] * point[2]
                        + viewProjection[row * 4 + 3];
            }
            // Clip space to pixels: x right, y up, so the row counts down.
            var x = (clip[0] / clip[3] + 1) / 2 * width;
            var y = (1 - clip[1] / clip[3]) / 2 * height;
            var ray = camera.ray((int) x, (int) y, width, height);
            var toPoint = new Vector3(point[0], point[1], point[2]).minus(ray.origin());
            var along = toPoint.dot(ray.direction());
            var off = toPoint.minus(ray.direction().times(along)).length();
            // Within the pixel the point fell in: its footprint at that distance.
            var footprint = along * 2 * (float) Math.tan(Math.toRadians(RoomScene.FIELD_OF_VIEW / 2)) / height;
            assertTrue(off < footprint * 1.5f, "the ray misses " + Arrays.toString(point) + " by " + off);
        }
    }
}
