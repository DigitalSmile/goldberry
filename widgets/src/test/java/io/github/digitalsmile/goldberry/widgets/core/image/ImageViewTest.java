package io.github.digitalsmile.goldberry.widgets.core.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// §1's `image` as a tree: what it builds while loading, loaded and failed, and
/// what a document may write ([ADR-0358]).
class ImageViewTest {

    private static final Image PIXEL = Image.ofArgb(4, 2, new int[8]);

    /// A loader whose answers the test gives by hand.
    private final List<CompletableFuture<Image>> asked = new ArrayList<>();
    private final ImageLoader manual = source -> {
        var future = new CompletableFuture<Image>();
        asked.add(future);
        return future;
    };

    private static Element part(ElementTree tree) {
        return tree.root().children().getFirst();
    }

    private static ImageSource file(String name) {
        return ImageSource.file(Path.of(name));
    }

    @Nested
    @DisplayName("loading")
    class Loading {

        @Test
        @DisplayName("says loading until the pixels arrive, then draws them")
        void loadingThenReady() {
            var tree = new ElementTree(new ImageView(file("harbour.jpg"), "The harbour").loader(manual));

            assertEquals(Set.of("loading"), part(tree).classes());
            assertEquals(1, asked.size());

            asked.getFirst().complete(PIXEL);
            tree.flush();

            assertEquals(Set.of(), part(tree).classes());
            var figure = assertInstanceOf(ImageFigure.class, part(tree).widget());
            assertEquals(new ImageLoad.Ready(PIXEL, 1), figure.load());
        }

        @Test
        @DisplayName("a picture already in hand is drawn on the first frame, with no loading state")
        void decodedIsImmediate() {
            var tree = new ElementTree(new ImageView(ImageSource.of(PIXEL), "Dots"));

            assertInstanceOf(ImageLoad.Ready.class, ((ImageFigure) part(tree).widget()).load());
        }

        @Test
        @DisplayName("a failed load says error, and shows the icon and the alt text")
        void failed() {
            var tree = new ElementTree(new ImageView(file("gone.png"), "A missing chart").loader(manual));

            asked.getFirst().completeExceptionally(new IllegalStateException("no such file"));
            tree.flush();

            assertEquals(Set.of("error"), part(tree).classes());
            assertEquals(
                    List.of("image-icon", "image-alt"),
                    part(tree).children().stream().map(Element::type).toList());
        }

        @Test
        @DisplayName("an answer to a question the view no longer asks is dropped")
        void staleAnswerIsDropped() {
            var tree = new ElementTree(new ImageView(file("old.png"), "Chart").loader(manual));

            tree.update(new ImageView(file("new.png"), "Chart").loader(manual));
            tree.flush();
            assertEquals(2, asked.size());
            asked.get(1).complete(PIXEL);
            tree.flush();
            asked.get(0).completeExceptionally(new IllegalStateException("old file went away"));
            tree.flush();

            assertEquals(Set.of(), part(tree).classes(), "the new picture stays; the old failure is not drawn");
        }

        @Test
        @DisplayName("a file that is not there fails through the real loader too")
        void realFileFailure(@TempDir Path directory) {
            var tree = new ElementTree(new ImageView(ImageSource.file(directory.resolve("nothing.png")), "Nothing")
                    .loader(ImageLoader.immediate()));

            assertEquals(Set.of("error"), part(tree).classes());
        }
    }

    @Nested
    @DisplayName("semantics")
    class TheSemantics {

        @Test
        @DisplayName("a meaningful picture is a figure named by its alt text")
        void figure() {
            var tree = new ElementTree(new ImageView(ImageSource.of(PIXEL), "Sales by quarter"));
            var node = assertInstanceOf(Semantics.class, part(tree).widget());

            assertEquals(Role.FIGURE, node.role());
            assertEquals("Sales by quarter", node.accessibleName());
        }

        @Test
        @DisplayName("a decorative picture has no semantics at all")
        void decorative() {
            var tree = new ElementTree(ImageView.decorative(ImageSource.of(PIXEL)));

            assertFalse(part(tree).widget() instanceof Semantics);
            assertEquals("image", part(tree).type());
        }

        @Test
        @DisplayName("alt text is required unless the picture is decoration")
        void altIsRequired() {
            assertThrows(IllegalArgumentException.class, () -> new ImageView(ImageSource.of(PIXEL), " "));
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("src, alt, fit and the document's attributes")
        void src() {
            var view = assertInstanceOf(
                    ImageView.class,
                    Widgets.inflater().inflate(KdlParser.parse("""
                    image src="photos/harbour.jpg" alt="The harbour" fit="cover" id="hero" class="rounded"
                    """).getFirst()));

            assertEquals(Fit.COVER, view.fit());
            assertEquals("The harbour", view.alt());
            assertEquals("hero", view.attributes().id());
            assertTrue(view.attributes().classes().contains("rounded"));
            assertEquals(
                    ImageSource.file(Path.of("photos/harbour.jpg")),
                    view.variants().getFirst().source());
        }

        @Test
        @DisplayName("srcset gives the variants, and decorative needs no alt")
        void srcsetAndDecorative() {
            var view = assertInstanceOf(
                    ImageView.class,
                    Widgets.inflater().inflate(KdlParser.parse("""
                    image srcset="classpath:logo.png 1x, classpath:logo@2x.png 2x" decorative=#true
                    """).getFirst()));

            assertTrue(view.decorative());
            assertEquals(
                    List.of(1.0, 2.0),
                    view.variants().stream().map(Variant::scale).toList());
        }

        @Test
        @DisplayName("a document names exactly one of src and srcset, and alt unless it is decoration")
        void refusals() {
            assertThrows(
                    RuntimeException.class,
                    () -> Widgets.inflater()
                            .inflate(KdlParser.parse("image alt=\"x\"").getFirst()));
            assertThrows(
                    RuntimeException.class,
                    () -> Widgets.inflater()
                            .inflate(KdlParser.parse("image src=\"a.png\" srcset=\"a.png 1x\" alt=\"x\"")
                                    .getFirst()));
            assertThrows(
                    RuntimeException.class,
                    () -> Widgets.inflater()
                            .inflate(KdlParser.parse("image src=\"a.png\"").getFirst()));
        }
    }
}
