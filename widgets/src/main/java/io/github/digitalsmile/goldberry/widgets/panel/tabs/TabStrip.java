package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// What a [Tabs] actually draws: a [TabList] and a [TabPanel].
///
/// [Tabs] became stateful when arrivals and departures needed remembering
/// ([ADR-0109](../../../../../../../../book/src/adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)),
/// and a stateful widget builds other widgets rather than a box. This is the box
/// half, split off unchanged — which is why it keeps `tabs` as its CSS type: the
/// split is an implementation detail of where state lives, and a stylesheet
/// should not have to hear about it.
///
/// @param headers    the tab headers, already wired and phased
/// @param content    the selected tab's content
/// @param attributes the strip's own, so `#views` still selects it
record TabStrip(List<Widget> headers, List<Widget> content,
        io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController controller,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints,
        io.github.digitalsmile.goldberry.input.Handles {

    TabStrip {
        headers = List.copyOf(headers == null ? List.of() : headers);
        content = List.copyOf(content == null ? List.of() : content);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public String cssType() {
        return "tabs";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    /// One Tab stop with the arrows roving inside it (§7.2), and the axis is the
    /// strip's own: a top-placed strip is a row, so `Up` and `Down` belong to
    /// whatever is above it (ADR-0078).
    ///
    /// Here rather than on [Tabs] because the scope has to be the node the headers
    /// are *inside*, and that is this one.
    @Override
    public io.github.digitalsmile.goldberry.input.FocusScope focusScope() {
        return io.github.digitalsmile.goldberry.input.FocusScope.HORIZONTAL;
    }

    @Override
    public List<Widget> children() {
        return List.of(new TabList(headers, controller), new TabPanel(content));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
