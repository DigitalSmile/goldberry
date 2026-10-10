package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.image.Image;
import dev.goldberry.input.cursor.CursorImage;
import dev.goldberry.paint.Box;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.cursor.CursorPicture;
import dev.goldberry.render.cursor.CursorPictures;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// [Application#cursors()] reaching the backend, every size of every shape,
/// before `start()` runs.
///
/// Headless, so there is no pointer: the backend records what it was handed.
/// Which size SDL is given is `Sdl3CursorsTest`.
class ApplicationCursorsTest {

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

    private static Image square(int size) {
        var pixels = new int[size * size];
        Arrays.fill(pixels, 0xFFC8A04A);
        return Image.ofArgb(size, size, pixels);
    }

    private final class CursorApp implements Application {

        List<CursorPictures> seenAtStart = List.of();

        @Override
        public Widget root() {
            return new Plate(Attributes.NONE);
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(120, 80);
        }

        @Override
        public List<CursorImage> cursors() {
            return List.of(
                    new CursorImage(Cursor.GRAB, square(32), 12, 4),
                    new CursorImage(Cursor.GRAB, square(64), 24, 8),
                    new CursorImage(Cursor.GRABBING, square(32), 12, 6));
        }

        @Override
        public void start(Host host) {
            seenAtStart = backend.cursorPictures();
            host.window().close();
        }
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
    @DisplayName("an application's cursors are with the backend before start() runs")
    void setBeforeStart() {
        var app = new CursorApp();

        Goldberry.launch(app);

        assertEquals(
                List.of(Cursor.GRAB, Cursor.GRABBING),
                app.seenAtStart.stream().map(CursorPictures::shape).toList());
        assertEquals(
                List.of(32, 64),
                app.seenAtStart.getFirst().sizes().stream()
                        .map(CursorPicture::width)
                        .toList());
        assertEquals(6, app.seenAtStart.getLast().base().hotY());
    }

    @Test
    @Timeout(10)
    @DisplayName("an application with no cursors asks the backend for nothing")
    void noneAsksNothing() {
        var app = new Application() {
            @Override
            public Widget root() {
                return new Plate(Attributes.NONE);
            }

            @Override
            public void start(Host host) {
                host.window().close();
            }
        };

        Goldberry.launch(app);

        assertEquals(List.of(), backend.cursorPictures());
    }
}
