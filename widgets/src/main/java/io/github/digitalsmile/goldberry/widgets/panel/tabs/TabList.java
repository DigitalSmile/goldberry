package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import java.util.List;
import java.util.Set;

/// The row of headers in a [Tabs] — a **part**, and the thing the bottom rule is
/// drawn on.
///
/// It exists for `segmented-track`'s reason: two boxes were doing one job. The
/// strip's own box carries the whole control, headers *and* panel; the rule under
/// the headers has to stop where the headers do, and a border on the outer box
/// would be under the panel as well.
///
/// The rule it carries is a [TabRule] rather than a `border-bottom`, because §8's
/// subset has no per-edge borders — see [TabIndicator].
///
/// @param headers the tabs, already told which of them is selected
record TabList(List<Widget> headers) implements Widget.Leaf, Styled, Paints {

    TabList {
        headers = List.copyOf(headers == null ? List.of() : headers);
    }

    @Override
    public String cssType() {
        return "tab-list";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// The rule **first**, so the headers and their indicators are painted over
    /// it — a box tree has no z-order beyond document order (ADR-0053).
    ///
    /// ## The headers scroll and the rule does not
    ///
    /// Enough tabs and the row runs off the side of its window, which was one of
    /// the three things `scroll` was written for
    /// ([ADR-0116](../../../../../../../../book/src/adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).
    /// The viewport goes around the **headers only**: the rule is pinned across
    /// the bottom of the whole strip and would otherwise scroll out of the left
    /// edge, leaving the underline of a strip that has been scrolled sitting
    /// somewhere it does not belong.
    ///
    /// A strip whose tabs fit gets a viewport that draws no thumb and takes no
    /// input, so this costs an element and nothing else — and a strip that fits
    /// at one window width does not at another, which is why it is not
    /// conditional.
    @Override
    public List<Widget> children() {
        return List.of(
                new TabRule(),
                new Scroll(List.copyOf(headers), ScrollAxis.HORIZONTAL,
                        Attributes.NONE.classes("tab-viewport")));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
