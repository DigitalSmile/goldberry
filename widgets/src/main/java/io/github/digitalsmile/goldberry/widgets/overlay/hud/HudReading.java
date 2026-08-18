package io.github.digitalsmile.goldberry.widgets.overlay.hud;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// One number in a [Hud] — a **part**, so it is CSS-selectable and not
/// constructible from a document (ADR-0065).
///
/// It reads the frame statistics in [#render] rather than being handed a string,
/// and that is the only interesting thing about it: a widget's children are
/// described before the render pass runs, so a part whose text came from its
/// constructor would be showing the frame before last. Reading it here means the
/// number on screen is the one from the frame it is drawn in.
///
/// @param reading which number this is
record HudReading(Reading reading) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "hud-reading";
    }

    /// The reading's own name, so `hud-reading.paint` is a selector.
    @Override
    public Set<String> classes() {
        return Set.of(reading.cssClass());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, reading.render(context.frames())), style.color())
                .style(style);
    }
}
