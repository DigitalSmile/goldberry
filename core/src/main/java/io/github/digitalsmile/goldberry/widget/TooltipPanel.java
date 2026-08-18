package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The little plate a `tooltip="…"` shows — `docs/core-widgets.md` §7's
/// `tooltip`, "plain text v1".
///
/// ## Why it is in `:core` when the catalog is in `:widgets`
///
/// Because nothing asks for it. A tooltip is attached by **attribute** to any
/// widget and opened by the toolkit when the pointer rests on that widget, so
/// there is no call site an application could pass a widget to — and the thing
/// doing the opening is the launcher, which is `:core`'s and cannot see the
/// catalog ([ADR-0092](../../../../../../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md)
/// is the record of `:core` not shipping widgets, and this is [WindowRoot]'s
/// exception rather than a hole in it).
///
/// `tooltip` is therefore **CSS-selectable and not KDL-constructible**, like
/// `window-root` and like a part: a document does not write one, it writes the
/// attribute that produces one. Its rules live in `controls.css` with everything
/// else that has an appearance, because where a type is declared and where it is
/// styled are different questions ([ADR-0105]).
///
/// @param text what to show
public record TooltipPanel(String text) implements Widget.Leaf, Styled, Paints {

    public TooltipPanel {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public String cssType() {
        return "tooltip";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// One text box, sized by the text.
    ///
    /// Not growing, for [io.github.digitalsmile.goldberry.layout.RenderTree#measure]'s
    /// reason: this is measured with nothing definite to find out how big its
    /// window should be, and a root that grows fills whatever it was measured
    /// against.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, text), style.color()).style(style);
    }
}
