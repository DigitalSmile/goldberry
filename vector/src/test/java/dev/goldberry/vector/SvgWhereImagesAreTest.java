package dev.goldberry.vector;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.image.Image;
import dev.goldberry.image.scalable.ScalableRasters;
import dev.goldberry.image.scalable.VectorDecoders;
import dev.goldberry.markdown.view.MarkdownView;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.image.ImageSource;
import dev.goldberry.widgets.core.image.ImageView;

/// SVG in every place an image is accepted, drawn sharp at the device pixels it
/// covers: an `image`, a CSS background, and a picture in a Markdown view.
///
/// The document is a black bar 4.5 units wide on an 8 unit square. Its natural
/// raster has a half-covered column at its edge, so stretched to twice the size
/// that edge is two grey columns; drawn at twice the size, it is one black
/// column against one white, which is what is asserted.
@DisplayName("SVG where images are")
class SvgWhereImagesAreTest {

    static final String EDGE = """
            <svg xmlns="http://www.w3.org/2000/svg" width="8" height="8" viewBox="0 0 8 8">
              <rect x="0" y="0" width="4.5" height="8" fill="#000000"/>
            </svg>
            """;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
        RendererRequirement.enforce();
    }

    @BeforeEach
    @AfterEach
    void fresh() {
        VectorDecoders.use(null);
        ScalableRasters.shared().clear();
    }

    @Test
    @DisplayName("an image at 1x and at 2x, its edge on a device pixel at both")
    void imageView() {
        var source = ImageSource.bytes(EDGE.getBytes(StandardCharsets.UTF_8));
        var sheet = sheet("image { width: 8px; height: 8px }");
        var once =
                Offscreen.of(8, 8).background(WHITE).stylesheets(List.of(sheet)).render(ImageView.decorative(source));
        var twice = Offscreen.of(16, 16)
                .scale(2f)
                .background(WHITE)
                .stylesheets(List.of(sheet))
                .render(ImageView.decorative(source));
        assertAll(
                () -> assertEquals(BLACK, once.argb(3, 4)),
                () -> assertEquals(WHITE, once.argb(5, 4)),
                () -> assertEquals(BLACK, twice.argb(8, 8), "the edge at 2x is black to its last column"),
                () -> assertEquals(WHITE, twice.argb(9, 8), "and white straight after it"));
    }

    @Test
    @DisplayName("a CSS background, at its natural size on a 2x display and stretched to a box twice as wide")
    void background(@TempDir Path directory) throws IOException {
        var file = directory.resolve("edge.svg");
        Files.writeString(file, EDGE);
        var box = new Column(List.of(), new Attributes("pic", Set.of(), null));
        var url = file.toAbsolutePath().toString().replace("\\", "/");
        var natural = Offscreen.of(16, 16)
                .scale(2f)
                .background(WHITE)
                .stylesheets(List.of(sheet("#pic { width: 8px; height: 8px; background-image: url(\"" + url
                        + "\"); background-repeat: no-repeat }")))
                .render(box);
        var stretched = Offscreen.of(16, 16)
                .background(WHITE)
                .stylesheets(List.of(sheet("#pic { width: 16px; height: 16px; background-image: url(\"" + url
                        + "\"); background-size: 100% 100%; background-repeat: no-repeat }")))
                .render(box);
        assertAll(
                () -> assertEquals(BLACK, natural.argb(8, 8)),
                () -> assertEquals(WHITE, natural.argb(9, 8)),
                () -> assertEquals(BLACK, stretched.argb(8, 8)),
                () -> assertEquals(WHITE, stretched.argb(9, 8)));
    }

    @Test
    @DisplayName("a picture in a Markdown view, as a mail's inline SVG would be")
    void markdown() {
        var image = Image.decode(EDGE.getBytes(StandardCharsets.UTF_8));
        var view = MarkdownView.of("![edge](edge.svg)").images(src -> src.equals("edge.svg") ? image : null);
        var frame = Offscreen.of(64, 32).scale(2f).background(WHITE).render(view);
        assertTrue(hasSharpEdge(frame), "a black device pixel beside a white one, with no grey between");
    }

    /// Whether some row of `frame` has a black pixel followed at once by a white
    /// one: an edge drawn at the device's pixels, where a stretched raster would
    /// put grey between.
    private static boolean hasSharpEdge(Image frame) {
        for (var y = 0; y < frame.height(); y++) {
            for (var x = 0; x + 1 < frame.width(); x++) {
                if (frame.argb(x, y) == BLACK && frame.argb(x + 1, y) == WHITE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Stylesheet sheet(String css) {
        return Stylesheet.parse(CascadeLayer.APPLICATION, css);
    }
}
