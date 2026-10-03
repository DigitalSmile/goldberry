package dev.goldberry.example.ui.drawing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.example.ui.Screen;
import dev.goldberry.image.Image;
import dev.goldberry.image.ImageFormat;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.image.ImageSource;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The pictures the Drawing screen draws: one sample, the same sample in five
/// formats, and a widget tree rendered with no window.
///
/// Each is decoded the first time a painter asks for it, in a holder class, and
/// not when a screen is built: the tests that only read the gallery's shape
/// inflate every screen with no rasterizer under them, and a decode in a
/// constructor would need one. A missing resource is a build that did not
/// package what its source names, so it fails rather than drawing a
/// placeholder.
///
/// The files live beside [Screen], in the one package the module opens for
/// resources.
///
/// Read more: [Images](https://goldberry.dev/docs/guide/text.html#images).
final class Samples {

    /// The sample as an `image` widget loads it: off the frame, on a virtual
    /// thread, through the shared loader.
    static final ImageSource JPEG = ImageSource.resource(Screen.class, "canvas-sample.jpg");

    private Samples() {}

    /// The 96 by 64 sample, decoded from its PNG.
    static Image image() {
        return Sample.IMAGE;
    }

    /// The same picture decoded from each of the five formats.
    static List<Coded> codecs() {
        return Codecs.ALL;
    }

    /// A card, two lines and a button, rendered offscreen at twice the detail.
    static Image rendered() {
        return Rendered.IMAGE;
    }

    /// One tile of the codecs card.
    ///
    /// @param format what [ImageFormat#of] made of the file's first bytes, never
    ///               its name
    /// @param bytes  the file's size
    /// @param image  the decoded picture: a value, with nothing to close
    record Coded(ImageFormat format, int bytes, Image image) {}

    private static byte[] read(String file) {
        try (var bytes = Screen.class.getResourceAsStream(file)) {
            if (bytes == null) {
                throw new IllegalStateException(file + " is not on the classpath beside Screen");
            }
            return bytes.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static final class Sample {

        private static final Image IMAGE = Image.decode(read("canvas-sample.png"));

        private Sample() {}
    }

    private static final class Codecs {

        /// The lossless three first, then the two that lose something, so the
        /// differences read left to right.
        private static final List<Coded> ALL = load();

        private Codecs() {}

        private static List<Coded> load() {
            var out = new ArrayList<Coded>();
            for (var extension : List.of("png", "qoi", "webp", "gif", "jpg")) {
                var bytes = read("canvas-sample." + extension);
                // The bytes decide: the same file under another name decodes the
                // same and is labelled the same.
                out.add(new Coded(ImageFormat.of(ByteBuffer.wrap(bytes)), bytes.length, Image.decode(bytes)));
            }
            return List.copyOf(out);
        }
    }

    /// Through `encodePng` and back on purpose: what a server would send is what
    /// the card draws.
    private static final class Rendered {

        private static final Image IMAGE = render();

        private Rendered() {}

        private static Image render() {
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
            sheets.addAll(ShowcaseStyles.sheets());
            var tree = new Card(
                    List.of(
                            new Text("Rendered with no window", Attributes.NONE.classes("card-title")),
                            new Text(
                                    "A widget tree, a buffer and a PNG. No display, no SDL, no compositor.",
                                    Attributes.NONE.classes("caption")),
                            new Button("Begin again")),
                    Attributes.NONE.classes("offscreen-card"));
            return Image.decode(Offscreen.of(560, 240)
                    .scale(2f)
                    .stylesheets(sheets)
                    .render(tree)
                    .encodePng());
        }
    }
}
