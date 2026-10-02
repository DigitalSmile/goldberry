package dev.goldberry.offscreen;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.image.Image;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The scaffolding the offscreen tests were each about to write.
///
/// `:core` has no widget catalog to render, so every test here builds its own
/// styled box — [OffscreenTest] did, and the strip, studio and threading suites
/// wanted the same one. The fixture is shared rather than copied four times,
/// which is the whole of why this class exists.
final class Scene {

    static final int RED = 0xFFFF0000;
    static final int BLUE = 0xFF0000FF;
    static final int GREEN = 0xFF00FF00;

    private Scene() {}

    /// A styled box with children — the smallest thing that exercises the cascade,
    /// layout and paint together.
    record Panel(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style);
        }
    }

    static Panel panel(String... classes) {
        return new Panel(List.of(), classed(classes));
    }

    static Attributes classed(String... names) {
        return new Attributes(null, Set.of(names), null);
    }

    static List<Stylesheet> sheet(String css) {
        return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css));
    }

    /// Every pixel of `image`, in one array, for an equality assertion.
    ///
    /// Premultiplied and read straight out of the buffer: two renders of one scene
    /// only have to agree with each other, and unpremultiplying both would round
    /// twice for nothing — the same judgement `GoldenImage.toImage` makes.
    static int[] pixels(Image image) {
        var argb = new int[image.width() * image.height()];
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                argb[y * image.width() + x] = image.argb(x, y);
            }
        }
        return argb;
    }
}
