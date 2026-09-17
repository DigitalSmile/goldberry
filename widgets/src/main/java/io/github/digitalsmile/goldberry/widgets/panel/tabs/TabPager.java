package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A chevron at one end of a tab strip that is wider than its window — a **part**,
/// `tab-pager`, with `start` or `end` as its class.
///
/// Present only while the headers overflow, and `:disabled` at the edge it points
/// past. A press pages the strip by most of its width, through the strip's own
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController], so the
/// move glides like any programmatic scroll (ADR-0363, ADR-0365).
///
/// **Not a Tab stop.** The strip is one stop with the arrows roving inside it,
/// and a selected tab already scrolls itself into view, so a keyboard reaches
/// every tab without these; a stop that only pages would be a stop a keyboard
/// user has to pass on the way in and out.
///
/// @param end      whether this pages towards the end of the strip
/// @param enabled  whether there is anything further that way
/// @param onPage   asked to page
record TabPager(boolean end, boolean enabled, Runnable onPage)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    @Override
    public String cssType() {
        return "tab-pager";
    }

    @Override
    public Set<String> classes() {
        return Set.of(end ? "end" : "start");
    }

    @Override
    public boolean isDisabled() {
        return !enabled;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED && enabled) {
            onPage.run();
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of()
                .style(style)
                .mark(new Box.Mark(end ? Box.Mark.Kind.CHEVRON_END : Box.Mark.Kind.CHEVRON_START, style.color(), 1.5));
    }

    @Override
    public Role role() {
        return Role.BUTTON;
    }

    @Override
    public String accessibleName() {
        return end ? "Later tabs" : "Earlier tabs";
    }
}
