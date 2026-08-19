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

    /// The reading's own name, so `hud-reading.paint` is a selector, plus how it
    /// is doing against its budget — `ok`, `near` or `over`.
    ///
    /// **Classes and not a colour written here.** A widget that picked the red
    /// itself would be a widget that cannot be themed, and §10's whole mechanism
    /// is that a colour comes from a token. What this node knows is which of the
    /// three states it is in; what that looks like is `controls.css`'s
    /// ([ADR-0150](../../../../../../../../book/src/adr/0150-a-hud-reads-itself-against-a-budget.md)).
    ///
    /// **The level cannot be in [#classes()]**, and that is what
    /// [Styled#classes(io.github.digitalsmile.goldberry.FrameStats)] exists for:
    /// the cascade reads a node's classes before that node's `render` runs, and
    /// the frame statistics only arrive in `render`. A widget is a value, so it
    /// cannot hold the answer between the two either.
    @Override
    public Set<String> classes() {
        return Set.of(reading.cssClass());
    }

    @Override
    public Set<String> classes(io.github.digitalsmile.goldberry.FrameStats frames) {
        return Set.of(reading.level(frames).cssClass());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, reading.render(context.frames())), style.color())
                .style(style);
    }
}
