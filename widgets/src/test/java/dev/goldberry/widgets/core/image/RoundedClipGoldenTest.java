package dev.goldberry.widgets.core.image;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.image.Image;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;

/// A box that clips its children to its `border-radius` — a picture in a round
/// face, held as golden images.
///
/// The picture is four coloured quarters, so the corners a clip leaves square
/// are the corners where a quarter's colour reaches the edge of the face. From
/// the left: a 34-pixel face, `border-radius: 17px; overflow: hidden`, which is
/// a circle; the same face with a 2px border, whose picture is cut to the curve
/// inside the border; a square box clipping the same picture, which must look
/// as it always did; and two rounded clips nested, the inner one pushed past the
/// outer's corner so that both curves cut it.
///
/// At 1x and at 2x, because a curve drawn at one scale and blitted at another
/// is the bug a layer invites.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class RoundedClipGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Image quarters() {
        var size = 64;
        var argb = new int[size * size];
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                var quarter = (x < size / 2 ? 0 : 1) + (y < size / 2 ? 0 : 2);
                argb[y * size + x] = switch (quarter) {
                    case 0 -> 0xFFBF616A;
                    case 1 -> 0xFFA3BE8C;
                    case 2 -> 0xFF5E81AC;
                    default -> 0xFFEBCB8B;
                };
            }
        }
        return Image.ofArgb(size, size, argb);
    }

    private static Attributes classes(String... names) {
        return new Attributes(null, Set.of(names), null);
    }

    private static Widget picture(ImageSource source) {
        return ImageView.decorative(source).fit(Fit.COVER);
    }

    private static Widget page() {
        var source = ImageSource.of(quarters());
        return new Row(
                List.of(
                        new Column(List.of(picture(source)), classes("face")),
                        new Column(List.of(picture(source)), classes("face", "ringed")),
                        new Column(List.of(picture(source)), classes("square")),
                        new Column(List.of(new Column(List.of(picture(source)), classes("inner"))), classes("outer"))),
                new Attributes("page", Set.of(), "page"));
    }

    private static final String SCENE = """
            #page { gap: 12px; padding: 12px; background: var(--gb-bg); align-items: center }
            .face, .square { width: 34px; height: 34px; overflow: hidden }
            .face { border-radius: 17px }
            .ringed { border: 2px solid var(--gb-text) }
            .face image, .square image { width: 34px; height: 34px; flex-shrink: 0 }
            .outer { width: 64px; height: 52px; border-radius: 18px; overflow: hidden; background: var(--gb-border) }
            .inner {
              width: 60px;
              height: 48px;
              margin: -6px 0 0 -6px;
              border-radius: 12px;
              overflow: hidden;
              flex-shrink: 0;
            }
            .inner image { width: 60px; height: 48px; flex-shrink: 0 }
            """;

    private void paint(String name, float scale) {
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());
        var width = 240;
        var height = 76;
        GoldenImage.assertMatches(
                name,
                Math.round(width * scale),
                Math.round(height * scale),
                scale,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(page()))));
    }

    @Test
    @DisplayName("a round face, a ringed one, a square box and two nested clips, at 1x")
    void oneX() {
        paint("rounded-clip", 1.0f);
    }

    @Test
    @DisplayName("and the same at 2x, where the curve is drawn at the scale it is shown at")
    void twoX() {
        paint("rounded-clip-2x", 2.0f);
    }
}
