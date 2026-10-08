package dev.goldberry.widgets.core.image;

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

import dev.goldberry.image.Image;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widgets.Widgets;

/// `image` as a tree: what it builds while loading, loaded and failed, and what
/// a document may write.
///
/// Read more: [Image](https://goldberry.dev/docs/components/drawing.html#image).
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

        @Test
        @DisplayName("one broken source shown four ways is said once, not four times")
        void oneFailureIsSaidOnce(@TempDir Path directory) {
            // The Canvas screen draws one sample at four `fit` values, so a
            // source that cannot be read produced four identical lines about one
            // file.
            ImageState.forgetReported();
            var source = ImageSource.file(directory.resolve("nothing.png"));

            for (var fit = 0; fit < 4; fit++) {
                var _ = new ElementTree(new ImageView(source, "Nothing " + fit).loader(ImageLoader.immediate()));
            }
            assertEquals(1, ImageState.reportedCount(), "four views, one source, one line");

            // A *different* source is a different thing to say.
            var _ = new ElementTree(new ImageView(ImageSource.file(directory.resolve("other.png")), "Other")
                    .loader(ImageLoader.immediate()));
            assertEquals(2, ImageState.reportedCount());
            ImageState.forgetReported();
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
        @DisplayName("a #xywh= fragment is a region of the sheet, in src and in srcset")
        void region() {
            var view = assertInstanceOf(
                    ImageView.class,
                    Widgets.inflater().inflate(KdlParser.parse("""
                    image src="classpath:/ui/kit.png#xywh=29,36,718,306" decorative=#true
                    """).getFirst()));

            var region = assertInstanceOf(
                    ImageSource.Region.class, view.variants().getFirst().source());
            assertEquals(PhysicalRect.of(29, 36, 718, 306), region.rect());
            var sheet = assertInstanceOf(ImageSource.Resource.class, region.sheet());
            assertEquals("ui/kit.png", sheet.name());

            var both = assertInstanceOf(
                    ImageView.class,
                    Widgets.inflater().inflate(KdlParser.parse("""
                    image srcset="kit.png#xywh=1,2,3,4 1x, kit@2x.png#xywh=2,4,6,8 2x" decorative=#true
                    """).getFirst()));
            assertEquals(
                    List.of(PhysicalRect.of(1, 2, 3, 4), PhysicalRect.of(2, 4, 6, 8)),
                    both.variants().stream()
                            .map(variant -> ((ImageSource.Region) variant.source()).rect())
                            .toList());
        }

        @Test
        @DisplayName("a fragment whose numbers cannot be read fails the document")
        void malformedRegion() {
            assertThrows(
                    RuntimeException.class,
                    () -> Widgets.inflater()
                            .inflate(KdlParser.parse("image src=\"kit.png#xywh=1,2,3\" decorative=#true")
                                    .getFirst()));
            assertThrows(
                    RuntimeException.class,
                    () -> Widgets.inflater()
                            .inflate(KdlParser.parse("image src=\"kit.png#xywh=1,2,0,4\" decorative=#true")
                                    .getFirst()));
        }

        @Test
        @DisplayName("a region of a picture in hand is cut and drawn on the first frame")
        void regionOfDecoded() {
            var sheet = Image.ofArgb(8, 4, new int[32]);
            var tree = new ElementTree(
                    ImageView.decorative(ImageSource.region(ImageSource.of(sheet), PhysicalRect.of(4, 0, 4, 4))));

            var box = assertInstanceOf(ImageBox.class, part(tree).widget());
            var ready = assertInstanceOf(ImageLoad.Ready.class, box.load());
            assertEquals(4, ready.image().width());
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
