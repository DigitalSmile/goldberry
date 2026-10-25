package dev.goldberry.example.gpu.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.golden.Tolerance;
import dev.goldberry.gpu.raytrace.RayTracer;
import dev.goldberry.gpu.raytrace.RayTracing;
import dev.goldberry.gpu.raytrace.RayTracingMode;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.image.Image;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.GpuSurface;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;

/// The ray-traced room drawn on a real device and read back, on the GPU lane:
/// lit by the sun's angle on every driver, traced where there is a ray
/// tracer, and lit by the angle again, with the reason said, where tracing is
/// asked for and there is none.
///
/// **The goldens are lavapipe's.** The untraced room is a rasterized, lit
/// scene, held to [#ROOM] on any driver. The traced one is held to its
/// golden on lavapipe alone: there every occlusion ray is the same as when it
/// was blessed, and on another device a ray that grazes an edge may go the
/// other way and move a pixel by a twelfth of its light. Elsewhere the traced
/// room is checked for being lit by the tracer and darker where it shadows.
///
/// Read more: [The renderer](https://goldberry.dev/docs/components/gpu.html#the-renderer).
@Tag(GpuTestLauncher.TAG)
@DisplayName("the ray-traced room, on the GPU")
class RoomRendererTest {

    /// [Tolerance#GPU], a level wider: the floor's checks are mipmapped and
    /// read trilinearly, and drivers filter between levels at different
    /// precisions. An NVIDIA GPU is three levels from lavapipe on 175 texels
    /// of the floor, scattered, and nowhere else further than two.
    private static final Tolerance ROOM = new Tolerance(3, 1, 0.001);

    private static final int WIDTH = 480;
    private static final int HEIGHT = 300;

    private static SdlGpuDevice device;
    private static HeadlessBackend backend;
    private static GpuSurface surface;
    private static List<Stylesheet> sheets;

    @BeforeAll
    static void open() {
        device = GpuDeviceRequirement.enforce();
        backend = new HeadlessBackend();
        var window = backend.createWindow(WindowSpec.of("room", LogicalSize.of(WIDTH, HEIGHT)));
        surface = window.gpuSurface().orElseThrow(() -> new AssertionError("no GPU surface with :gpu here"));
        var all = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        all.add(Stylesheet.parse(CascadeLayer.APPLICATION, "canvas3d { width: 100%; height: 100%; }"));
        sheets = List.copyOf(all);
    }

    @AfterAll
    static void close() {
        // Skipped before SDL was reached: nothing to give back.
        if (device == null) {
            return;
        }
        backend.close();
        device.close();
        Sdl.get().quit();
    }

    /// The room under `settings`, at its first frame, read back.
    private static Image picture(RoomRenderer renderer, int width, int height, float scale) {
        return Offscreen.of(width, height)
                .scale(scale)
                .stylesheets(sheets)
                .gpu(surface)
                .render(new Canvas3d(renderer).depth(Canvas3d.Depth.D32).withAttributes(Attributes.NONE));
    }

    private static RoomRenderer renderer(SceneSettings settings, List<String> refusals) {
        return new RoomRenderer(() -> settings, refusals::add);
    }

    @Test
    @DisplayName("draws the room lit by the sun's angle alone, the same on every driver")
    void untraced() {
        var refusals = new ArrayList<String>();
        GoldenImage.assertMatchesAtOneScale("gpu-room", WIDTH, HEIGHT, 1f, ROOM, (at, scale) -> {
            var renderer = renderer(SceneSettings.DEFAULT, refusals);
            var image = picture(renderer, at.width(), at.height(), scale.factor());
            assertFalse(renderer.tracedLastFrame());
            return image;
        });
        assertEquals(List.of(), refusals, "nothing asked of the tracer");
    }

    @Test
    @DisplayName("traces the room's shadows, occlusion and reflections where there is a ray tracer")
    void traced() {
        var availability = RayTracer.availability();
        assumeTrue(availability.isAvailable(), () -> "no ray tracing here: " + availability);
        var device = ((RayTracing.Hardware) availability).device();
        var refusals = new ArrayList<String>();
        var renderer = renderer(SceneSettings.DEFAULT.withRayTracing(true), refusals);
        var traced = picture(renderer, WIDTH, HEIGHT, 1f);
        assertTrue(renderer.tracedLastFrame(), "lit by the tracer");
        assertEquals(List.of(), refusals);
        var untraced = picture(renderer(SceneSettings.DEFAULT, refusals), WIDTH, HEIGHT, 1f);
        assertTrue(
                brightness(traced) < brightness(untraced) * 0.97,
                "shadows and occlusion darken the room: " + brightness(traced) + " against " + brightness(untraced));
        if (device.toLowerCase(Locale.ROOT).contains("llvmpipe")) {
            GoldenImage.assertMatchesAtOneScale(
                    "gpu-room-traced",
                    WIDTH,
                    HEIGHT,
                    1f,
                    ROOM,
                    (at, scale) -> picture(
                            renderer(SceneSettings.DEFAULT.withRayTracing(true), refusals),
                            at.width(),
                            at.height(),
                            scale.factor()));
        }
    }

    @Test
    @DisplayName("says once why there is no tracing when goldberry.raytracing is off, and draws the room untraced")
    void unavailable() {
        var previous = System.getProperty(RayTracingMode.PROPERTY);
        System.setProperty(RayTracingMode.PROPERTY, "off");
        try {
            var refusals = new ArrayList<String>();
            var renderer = renderer(SceneSettings.DEFAULT.withRayTracing(true), refusals);
            picture(renderer, 96, 60, 1f);
            assertFalse(renderer.tracedLastFrame());
            assertEquals(List.of(RayTracingMode.PROPERTY + " is off"), refusals);
            GoldenImage.assertMatchesAtOneScale(
                    "gpu-room",
                    WIDTH,
                    HEIGHT,
                    1f,
                    ROOM,
                    (at, scale) -> picture(
                            renderer(SceneSettings.DEFAULT.withRayTracing(true), new ArrayList<>()),
                            at.width(),
                            at.height(),
                            scale.factor()));
        } finally {
            if (previous == null) {
                System.clearProperty(RayTracingMode.PROPERTY);
            } else {
                System.setProperty(RayTracingMode.PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("draws with the most samples the device has up to the count asked, and smooths the silhouettes")
    void antiAliasing() {
        var refusals = new ArrayList<String>();
        var plain = picture(renderer(SceneSettings.DEFAULT, refusals), WIDTH, HEIGHT, 1f);
        var renderer = renderer(SceneSettings.DEFAULT.withSamples(4), refusals);
        var smooth = picture(renderer, WIDTH, HEIGHT, 1f);
        assertEquals(4, renderer.samplesInUse(), "every driver the lane runs has 4");
        var eight = renderer(SceneSettings.DEFAULT.withSamples(8), refusals);
        picture(eight, 64, 40, 1f);
        assertTrue(eight.samplesInUse() >= 4 && eight.samplesInUse() <= 8, "8 or the most below it");
        assertTrue(differing(plain, smooth) > 200, "edges blended: " + differing(plain, smooth) + " pixels differ");
    }

    /// The mean of every pixel's channels.
    private static double brightness(Image image) {
        var sum = 0L;
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                var argb = image.argb(x, y);
                sum += ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
            }
        }
        return sum / (3.0 * image.width() * image.height());
    }

    private static int differing(Image a, Image b) {
        var count = 0;
        for (var y = 0; y < a.height(); y++) {
            for (var x = 0; x < a.width(); x++) {
                if (a.argb(x, y) != b.argb(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
