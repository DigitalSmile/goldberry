package dev.goldberry.example.ui.drawing;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.vector.VectorCapabilities;
import dev.goldberry.vector.view.SvgSource;
import dev.goldberry.vector.view.SvgView;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// Two original SVG drawings on the Drawing screen.
final class SvgCard {

    private static final SvgSource HILLS = SvgSource.text("""
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">
              <rect width="48" height="48" rx="6" fill="#34536d"/>
              <circle cx="33" cy="13" r="6" fill="#ffcf76"/>
              <path d="M2 40L18 15l9 14 5-7 14 18Z" fill="#a5d7c7"/>
            </svg>
            """);
    private static final SvgSource LEAF = SvgSource.text("""
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">
              <path d="M6 41Q5 9 41 6Q42 40 11 37L7 43Z" fill="currentColor"/>
            </svg>
            """);

    private SvgCard() {}

    static Widget card() {
        var card = new ShowcaseCard(
                "svg-card",
                "SVG at three sizes",
                "The hills keep their own colours and the leaf takes its colour from the theme. "
                        + "Each drawing appears at 24, 48 and 72 logical pixels. A broken document shows its alt text.",
                DocLink.to("components/drawing", "image"));
        if (!VectorCapabilities.isAvailable()) {
            return card.of(new Text(
                    "Install the goldberry-vector natives for this platform to see the drawings.",
                    Attributes.NONE.id("svg-unavailable")));
        }
        return card.of(drawings());
    }

    static List<Widget> drawings() {
        return List.of(
                sizes("svg-hills", HILLS, "Sun over two hills"),
                sizes("svg-leaf", LEAF, "Leaf"),
                new SvgView(SvgSource.text("<drawing/>"), "Broken SVG")
                        .withAttributes(Attributes.NONE.id("svg-broken")));
    }

    private static Widget sizes(String id, SvgSource source, String alt) {
        return new Row(
                List.of(
                        new SvgView(source, alt).styled("svg-small"),
                        SvgView.decorative(source).styled("svg-medium"),
                        SvgView.decorative(source).styled("svg-large")),
                Attributes.NONE.id(id).classes("svg-sizes"));
    }
}
