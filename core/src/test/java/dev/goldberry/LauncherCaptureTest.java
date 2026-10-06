package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.image.Image;
import dev.goldberry.paint.Box;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// `--capture=PATH`: the last frame of a `--frames=N` run, written as a PNG
/// of what the window showed. On the headless backend, which keeps every
/// presented frame.
class LauncherCaptureTest {

    /// Something for the window to paint: one box filling it.
    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Plate() {
            this(new Attributes("content", Set.of(), "content"));
        }

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    private static final class Plain implements Application {

        @Override
        public Widget root() {
            return new Plate();
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(320, 200);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "plate { background: #204060 }"));
        }
    }

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        GoldberryRuntime.install(new HeadlessBackend());
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    @Test
    @Timeout(20)
    @DisplayName("writes the last frame as a PNG of the window's size, in the plate's colour")
    void writesTheLastFrame(@TempDir Path directory) throws IOException {
        var path = directory.resolve("shots").resolve("last.png");
        Goldberry.launch(new Plain(), new String[] {"--frames=2", "--capture=" + path});

        assertTrue(Files.isRegularFile(path), "written on exit, directories made");
        var image = Image.decode(Files.readAllBytes(path));
        assertEquals(320, image.width());
        assertEquals(200, image.height());
        var pixels = image.pixels();
        var pixel = pixels.pixels().getInt(100 * pixels.stride() + 160 * 4);
        assertEquals(0xFF204060, pixel, "the plate's colour, opaque");
    }
}
