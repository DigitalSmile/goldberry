package io.github.digitalsmile.goldberry.offscreen;

import static io.github.digitalsmile.goldberry.offscreen.Scene.panel;
import static io.github.digitalsmile.goldberry.offscreen.Scene.pixels;
import static io.github.digitalsmile.goldberry.offscreen.Scene.sheet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// Rendering off the UI thread, and several at once — ADR-0425.
///
/// The TODO entry this closes said a render "touches no window and no backend, so
/// a server thread is probably fine — 'probably' is why it is written here rather
/// than in the javadoc". This suite is what replaced the word: the javadoc on
/// [Offscreen] now promises concurrent off-thread rendering, and a promise in a doc
/// comment with no test under it is the same "probably" in a better font.
///
/// Two claims, and they pull in opposite directions:
///
/// - **A render may run anywhere, and several may run at once.** Every object on
///   the path is built inside the call on the calling thread, and nothing reaches a
///   window, a backend or the `GoldberryRuntime`.
/// - **The things that make a render cheaper may not be shared.** A [Fonts], a
///   `Font` and a [Studio] are confined to the thread that opened them, because the
///   HarfBuzz and Blend2D objects underneath them are. Those now say so by throwing.
@DisplayName("rendering off the UI thread")
class OffscreenThreadTest {

    private static final String CSS = """
            .root { width: 64px; height: 32px; background: #1c2128; padding: 6px }
            .label { color: #e6edf3 }
            """;

    /// Text and a nested box: the point is to touch the shaping cache, the cascade,
    /// Yoga and the rasterizer on every thread rather than to fill a rectangle.
    private record Label(Attributes attributes, String words, List<Widget> children)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.text(context.paragraph(style, words), style.color())
                    .style(style)
                    .children(boxes.toArray(Box[]::new));
        }
    }

    private static Widget scene() {
        return new Label(Scene.classed("root"), "off thread", List.of());
    }

    private static int[] render() {
        return pixels(Offscreen.of(96, 48).stylesheets(sheet(CSS)).render(scene()));
    }

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Nested
    @DisplayName("what is allowed")
    class Allowed {

        @Test
        @DisplayName("a render on a thread that is not the main one draws the same picture")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void rendersOnAnotherThread() throws Exception {
            var here = render();

            var elsewhere = new AtomicReference<int[]>();
            var raised = new AtomicReference<Throwable>();
            var worker = new Thread(
                    () -> {
                        try {
                            elsewhere.set(render());
                        } catch (Throwable t) {
                            raised.set(t);
                        }
                    },
                    "not-the-ui-thread");
            worker.start();
            worker.join(Duration.ofSeconds(30).toMillis());

            assertNull(raised.get(), String.valueOf(raised.get()));
            assertArrayEquals(here, elsewhere.get(), "the thread is not an input to the picture");
        }

        @Test
        @DisplayName("eight threads rendering at once agree with one thread rendering alone")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        void rendersConcurrently() throws Exception {
            // The assertion the javadoc promise rests on, and the reason it is
            // pixels rather than "it did not throw": the failure mode of sharing
            // something that should not be shared is usually a *wrong picture* --
            // a paragraph shaped against a half-written cache, a Yoga layout from
            // another thread's tree -- long before it is a crash.
            var alone = render();

            var workers = 8;
            var start = new CountDownLatch(1);
            var results = new ArrayList<int[]>();
            try (var pool = Executors.newFixedThreadPool(workers)) {
                var tasks = new ArrayList<Callable<int[]>>();
                for (var i = 0; i < workers; i++) {
                    tasks.add(() -> {
                        // All eight inside the render at the same time, which is
                        // what makes this a test of contention rather than of eight
                        // renders that happened to be on different threads.
                        start.await(30, TimeUnit.SECONDS);
                        return render();
                    });
                }
                var futures = new ArrayList<Future<int[]>>();
                for (var task : tasks) {
                    futures.add(pool.submit(task));
                }
                start.countDown();
                for (var future : futures) {
                    try {
                        results.add(future.get(60, TimeUnit.SECONDS));
                    } catch (ExecutionException e) {
                        throw new AssertionError("a concurrent render failed", e.getCause());
                    }
                }
            }

            assertEquals(workers, results.size());
            for (var i = 0; i < results.size(); i++) {
                assertArrayEquals(alone, results.get(i), "worker " + i + " drew a different picture");
            }
        }

        @Test
        @DisplayName("one studio per thread is the supported way to render in a pool")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        void aStudioPerThreadIsFine() throws Exception {
            var alone = render();
            var workers = 4;
            var results = new ArrayList<int[]>();
            try (var pool = Executors.newFixedThreadPool(workers)) {
                var futures = new ArrayList<Future<int[]>>();
                for (var i = 0; i < workers; i++) {
                    futures.add(pool.submit(() -> {
                        // Opened *on* the worker, which is the whole rule. Two
                        // renders through it, so the reuse path is the one under
                        // test rather than a cold studio.
                        try (var studio = Studio.of(sheet(CSS))) {
                            studio.picture(96, 48).render(scene());
                            return pixels(studio.picture(96, 48).render(scene()));
                        }
                    }));
                }
                for (var future : futures) {
                    try {
                        results.add(future.get(60, TimeUnit.SECONDS));
                    } catch (ExecutionException e) {
                        throw new AssertionError("a studio render failed on its own thread", e.getCause());
                    }
                }
            }

            for (var picture : results) {
                assertArrayEquals(alone, picture, "a warm studio on a worker thread draws the plain picture");
            }
        }

        @Test
        @DisplayName("a strip on another thread is a strip")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void stripsRunOffThread() throws Exception {
            var raised = new AtomicReference<Throwable>();
            var frames = new AtomicReference<Integer>();
            var worker = new Thread(() -> {
                try (var strip = Offscreen.of(32, 32)
                        .stylesheets(sheet(".root { width: 32px; height: 32px; background: #ff0000 }"))
                        .strip(panel("root"))) {
                    strip.frame();
                    strip.advance(16).frame();
                    frames.set(strip.frames());
                } catch (Throwable t) {
                    raised.set(t);
                }
            });
            worker.start();
            worker.join(Duration.ofSeconds(30).toMillis());

            assertNull(raised.get(), String.valueOf(raised.get()));
            assertEquals(2, frames.get());
        }
    }

    @Nested
    @DisplayName("what is refused, loudly")
    class Refused {

        @Test
        @DisplayName("a font book used from a thread that did not open it")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void refusesASharedBook() throws Exception {
            // The configuration this toolkit's own javadoc used to recommend: "a
            // server rendering many previews should hand over one `Fonts` and keep
            // it". True for serial renders and corruption in a pool -- two plain
            // `LinkedHashMap`s and a native face opened twice. Now it throws.
            try (var book = Fonts.bundled(List.of())) {
                var raised = new AtomicReference<Throwable>();
                var worker = new Thread(() -> {
                    try {
                        Offscreen.of(96, 48).stylesheets(sheet(CSS)).fonts(book).render(scene());
                    } catch (Throwable t) {
                        raised.set(t);
                    }
                });
                worker.start();
                worker.join(Duration.ofSeconds(30).toMillis());

                assertNotNull(raised.get(), "a shared book is refused rather than silently corrupted");
                var refusal = assertInstanceOf(IllegalStateException.class, raised.get());
                assertTrue(refusal.getMessage().contains("thread that opened it"), refusal.getMessage());

                // And the book the caller still holds is undamaged: the refusal
                // happened before anything was written to it.
                assertNotNull(book.of(BundledFont.UI, 12));
            }
        }

        @Test
        @DisplayName("a font book closed from a thread that did not open it")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void refusesAForeignClose() throws Exception {
            // Not a try-with-resources: the point of the test is a `close` on
            // another thread, and the owner closes it by hand at the end.
            var book = Fonts.bundled(List.of());
            try {
                book.of(BundledFont.UI, 12);
                var raised = new AtomicReference<Throwable>();
                var worker = new Thread(() -> {
                    try {
                        book.close();
                    } catch (Throwable t) {
                        raised.set(t);
                    }
                });
                worker.start();
                worker.join(Duration.ofSeconds(30).toMillis());

                assertInstanceOf(IllegalStateException.class, raised.get());
                // Checked *before* the closed flag goes up, so the owner can still
                // use and close the book it never lost.
                assertNotNull(book.of(BundledFont.UI, 14), "a refused close did not half-close the book");
            } finally {
                book.close();
            }
        }
    }
}
