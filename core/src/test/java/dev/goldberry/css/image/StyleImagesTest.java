package dev.goldberry.css.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.background.Background;
import dev.goldberry.css.background.BackgroundPosition;
import dev.goldberry.image.Image;
import dev.goldberry.image.ImageAddress;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;

/// The pictures a stylesheet names: asked for by the frame that draws them,
/// drawn by the frame after they arrive.
class StyleImagesTest {

    /// Loads answered by the test.
    private final Map<String, CompletableFuture<Image>> pending = new HashMap<>();
    private final List<String> asked = new ArrayList<>();
    private final StyleImages manual = address -> {
        asked.add(address.toString());
        return pending.computeIfAbsent(address.toString(), _ -> new CompletableFuture<>());
    };

    @AfterEach
    void tearDown() {
        Registry.use(null);
    }

    private static Image square(int side) {
        return Image.ofArgb(side, side, new int[side * side]);
    }

    @Test
    @DisplayName("null while it loads; then the arrival is told and the picture is there")
    void arrives() {
        Registry.use(manual);
        var url = new CssImage.Url("classpath:/ui/leather.png");
        var told = new int[1];
        var before = StyleImages.generation();

        try (var _ = StyleImages.onArrival(() -> told[0]++)) {
            assertNull(StyleImages.resolve(url, 1));
            assertNull(StyleImages.resolve(url, 1));
            pending.get("classpath:/ui/leather.png").complete(square(4));

            assertEquals(1, told[0], "watched once however many frames asked");
            assertTrue(StyleImages.generation() > before);
            var resolved = StyleImages.resolve(url, 1);
            assertNotNull(resolved);
            assertEquals(1, resolved.density());
            assertEquals(4, resolved.naturalWidth());
        }
    }

    @Test
    @DisplayName("above 100% the @2x variant is drawn, and the 1x one when there is none")
    void variants() {
        Registry.use(manual);
        var url = new CssImage.Url("ui/panel.png#xywh=1,2,3,4");

        assertNull(StyleImages.resolve(url, 2));
        assertEquals(List.of("ui/panel@2x.png#xywh=2,4,6,8"), asked);
        pending.get("ui/panel@2x.png#xywh=2,4,6,8").complete(square(6));
        var doubled = StyleImages.resolve(url, 2);
        assertNotNull(doubled);
        assertEquals(2, doubled.density());
        assertEquals(3, doubled.naturalWidth());

        var plain = new CssImage.Url("ui/plain.png");
        assertNull(StyleImages.resolve(plain, 1.5));
        pending.get("ui/plain@2x.png").completeExceptionally(new IllegalStateException("no variant"));
        assertNull(StyleImages.resolve(plain, 1.5), "the 1x picture is asked for now");
        pending.get("ui/plain.png").complete(square(5));
        var fallback = StyleImages.resolve(plain, 1.5);
        assertNotNull(fallback);
        assertEquals(1, fallback.density());
    }

    @Test
    @DisplayName("a picture that cannot be read is asked for once")
    void failureRemembered() {
        Registry.use(manual);
        var url = new CssImage.Url("gone.png");

        assertNull(StyleImages.resolve(url, 1));
        pending.get("gone.png").completeExceptionally(new IllegalStateException("no such file"));
        assertNull(StyleImages.resolve(url, 1));
        assertNull(StyleImages.resolve(url, 1));

        assertEquals(List.of("gone.png"), asked);
    }

    @Test
    @DisplayName("the provider this module carries reads a file, and a region of it from one decode")
    void direct(@TempDir Path directory) throws Exception {
        RendererRequirement.enforce();
        var file = directory.resolve("sheet.png");
        Files.write(file, Image.ofArgb(8, 4, new int[32]).encodePng());
        var direct = new DirectStyleImages();

        var whole = direct.load(ImageAddress.parse(file.toString())).join();
        var region = direct.load(ImageAddress.parse(file + "#xywh=4,0,4,4")).join();

        assertEquals(8, whole.width());
        assertEquals(4, region.width());
        assertSame(whole, direct.load(ImageAddress.parse(file.toString())).join());
    }

    @Test
    @DisplayName("a box that names a picture is damaged again when the picture arrives")
    void damagedOnArrival() {
        RendererRequirement.enforce();
        Registry.use(manual);
        var target = TestFrames.of(100, 100, 1.0f, 0);
        try (var render = RenderTree.create()) {
            var box = Box.filled(0xFF000000)
                    .size(Length.points(100), Length.points(100))
                    .fill(Background.of(
                            0xFF000000, List.of(new CssImage.Url("ui/leather.png")), BackgroundPosition.ZERO));
            render.update(target.frame(), box);
            render.damage(target.frame());
            render.paint(target.frame());
            render.update(target.frame(), box);
            assertEquals(List.of(), render.damage(target.frame()), "nothing has changed yet");

            pending.get("ui/leather.png").complete(square(4));
            render.update(target.frame(), box);

            assertEquals(1, render.damage(target.frame()).size(), "the same box, with its picture now");
        } finally {
            target.end();
        }
    }
}
