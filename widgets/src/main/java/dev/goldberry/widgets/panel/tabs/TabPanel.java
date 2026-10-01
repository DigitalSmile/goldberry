package dev.goldberry.widgets.panel.tabs;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// What the selected tab shows — a **part**, holding one tab's content and
/// nothing else.
///
/// This is where §5's "lazy content instantiation" happens, and it happens by
/// *omission*: [Tabs] puts only the selected tab's content in here, so an
/// unselected tab's widgets are never built into elements at all. Nine background
/// tabs cost nine headers.
///
/// The consequence is the one worth knowing: a tab's content is **rebuilt when it
/// is selected again**, so anything it must not lose — a scroll position, a
/// caret, a half-typed form — belongs in the application's model rather than in
/// the subtree. That is `collapse`'s trade in §5 and the same argument
/// (ADR-0004): cheap to rebuild is what the widget tree is for.
///
/// @param content the selected tab's widgets, or empty when nothing is selected
record TabPanel(List<Widget> content) implements Widget.Leaf, Styled, Paints {

    /// Written out so that the parameters taking null for a default can say so (ADR-0497).
    TabPanel(@Nullable List<Widget> content) {
        content = List.copyOf(content == null ? List.of() : content);
        this.content = content;
    }

    @Override
    public String cssType() {
        return "tab-panel";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return content;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
