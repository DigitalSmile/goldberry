package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.window.IconImage;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// [Application#icon()] reaching the window — `docs/gaps.md` G40 ([ADR-0351]).
///
/// Headless, so there is no taskbar: the backend records what it was handed. The
/// ordering SDL is given is `WindowIconOrderTest`, and the binding is
/// `SdlWindowIconTest` in `:natives`.
class WindowIconTest {

    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "plate";
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

    /// Three sizes of a mark whose one corner pixel is half-transparent red, which
    /// is the pixel a premultiplied buffer would get wrong.
    private static final class IconApp implements Application {

        List<IconImage> seenAtStart = List.of();

        @Override
        public Widget root() {
            return new Plate(Attributes.NONE);
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(120, 80);
        }

        @Override
        public List<Image> icon() {
            return List.of(mark(16), mark(32), mark(48));
        }

        @Override
        public void start(Host host) {
            seenAtStart = ((HeadlessWindow) host.window().backendWindow()).icon();
            host.window().close();
        }
    }

    private static Image mark(int size) {
        var pixels = new int[size * size];
        Arrays.fill(pixels, 0xFF3B4252);
        pixels[0] = 0x80FF0000;
        return Image.ofArgb(size, size, pixels);
    }

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("an application's icon is on the window before start() runs")
    void setBeforeStart() {
        var app = new IconApp();

        Goldberry.launch(app);

        assertEquals(
                List.of(16, 32, 48),
                app.seenAtStart.stream().map(i -> i.size().width()).toList());
    }

    @Test
    @Timeout(10)
    @DisplayName("the pixels leave in straight alpha, so a soft edge is not dimmed by its own coverage")
    void straightAlpha() {
        var app = new IconApp();

        Goldberry.launch(app);

        var corner = app.seenAtStart.getFirst().argb(0, 0);
        assertEquals(0x80, corner >>> 24);
        assertTrue(
                (corner >> 16 & 0xFF) >= 0xFE, "the red is full red, not half of it: " + Integer.toHexString(corner));
        assertEquals(0xFF3B4252, app.seenAtStart.getFirst().argb(1, 1));
    }

    @Test
    @DisplayName("no sizes is no request, and the platform keeps its own icon")
    void emptyAsksNothing() {
        var window = Window.open(WindowSpec.of("icon", LogicalSize.of(40, 40)));
        try {
            assertFalse(window.icon(List.of()));
            assertEquals(List.of(), ((HeadlessWindow) window.backendWindow()).icon());
        } finally {
            window.close();
        }
    }
}
