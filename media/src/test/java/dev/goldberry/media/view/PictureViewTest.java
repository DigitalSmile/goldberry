package dev.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.picture.PictureSource;
import dev.goldberry.media.picture.PushedPictures;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.core.image.Fit;

/// Pictures an application pushes, shown by a `picture-view` as a `video-view`
/// shows a player's.
@DisplayName("pushed pictures")
class PictureViewTest {

    /// A picture of one colour, `0xAARRGGBB`.
    private static VideoPicture solid(int width, int height, int argb, long pts) {
        var pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (var i = 0; i < width * height; i++) {
            pixels.putInt(i * 4, argb);
        }
        return new VideoPicture(width, height, width * 4, pixels, pts);
    }

    private static List<Element> walk(Element from) {
        var all = new ArrayList<Element>();
        all.add(from);
        for (var child : from.children()) {
            all.addAll(walk(child));
        }
        return all;
    }

    private static VideoSurface surface(Widget widget) {
        var tree = new ElementTree(widget);
        tree.flush();
        return walk(tree.root()).stream()
                .map(Element::widget)
                .filter(VideoSurface.class::isInstance)
                .map(VideoSurface.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("the source")
    class Source {

        @Test
        @DisplayName("shows the newest picture, and nothing before the first or after a clear")
        void newest() {
            var source = new PushedPictures();
            assertTrue(source.shownPicture().isEmpty());
            var first = solid(2, 2, 0xFFFF0000, 0);
            var second = solid(2, 2, 0xFF0000FF, 1);
            source.push(first);
            source.push(second);
            assertSame(second, source.shownPicture().orElseThrow());
            source.clear();
            assertTrue(source.shownPicture().isEmpty());
        }

        @Test
        @DisplayName("tells each listener of every push, from the pushing thread, until it is removed")
        void listeners() throws Exception {
            var source = new PushedPictures();
            var heard = new AtomicInteger();
            var subscription = source.onPicture(heard::incrementAndGet);
            var pusher = Thread.ofPlatform().start(() -> source.push(solid(1, 1, 0xFF00FF00, 0)));
            pusher.join();
            assertEquals(1, heard.get());
            subscription.close();
            source.push(solid(1, 1, 0xFF00FF00, 1));
            assertEquals(1, heard.get(), "not after it was removed");
        }

        @Test
        @DisplayName("a player is a source, which knows when its pictures fall due")
        void playerIsASource() {
            try (var player = MediaPlayer.builder().build()) {
                PictureSource source = player;
                assertTrue(source.shownPicture().isEmpty(), "nothing is open");
                assertTrue(source.untilNextPicture().isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("the view")
    class View {

        @Test
        @DisplayName("is a video-view, and draws whatever the source shows when it renders")
        void draws() {
            // Pushed before the view listens: a push to a mounted view hands a
            // redraw to the UI thread, and there is no runtime here to take it.
            var empty = surface(new PictureView(new PushedPictures(), Fit.COVER));
            assertEquals("video-view", empty.cssType());
            assertNull(empty.render(ComputedStyle.INITIAL, List.of(), null).painting(), "nothing yet");

            var source = new PushedPictures();
            source.push(solid(4, 4, 0xFFFF0000, 0));
            var surface = surface(new PictureView(source, Fit.COVER));
            assertNotNull(surface.render(ComputedStyle.INITIAL, List.of(), null).painting());
        }

        @Test
        @DisplayName("paints the pushed picture's pixels")
        void pixels() {
            RendererRequirement.enforce();
            var source = new PushedPictures();
            source.push(solid(8, 8, 0xFFFF0000, 0));
            var picture = Offscreen.of(32, 32)
                    .paint(VideoSurface.painter(source.shownPicture().orElseThrow(), Fit.FILL, null));
            assertEquals(0xFFFF0000, picture.argb(16, 16));
        }

        @Test
        @DisplayName("hands one redraw to the UI thread however many pictures arrive before it runs")
        void coalesces() {
            var pending = new AtomicBoolean();
            var posted = new ArrayList<Runnable>();
            var refreshed = new AtomicInteger();
            var listener = PictureView.PictureViewState.coalesced(pending, posted::add, refreshed::incrementAndGet);
            for (var i = 0; i < 100; i++) {
                listener.run();
            }
            assertEquals(1, posted.size(), "one task for a hundred pictures");
            posted.getFirst().run();
            assertEquals(1, refreshed.get());
            listener.run();
            assertEquals(2, posted.size(), "and the next picture after it posts again");
        }
    }
}
