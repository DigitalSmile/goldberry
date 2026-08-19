package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Set;

/// One section's heading inside [Scrolling]'s list.
///
/// A plain node. It used to carry the `scrollIntoView` half of this screen and
/// could not: a header inside an `affix` is *pinned to the viewport's edge* the
/// moment its section starts scrolling away, so a reveal measured against it
/// concluded the section was already visible and moved nothing. What travels
/// with the document is the affix's hole, and the affix is what hands it out
/// ([ADR-0124](../../../../../../../book/src/adr/0124-a-pinned-affix-is-revealed-by-its-hole.md)).
record SectionHeader(String title) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "section-header";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(new Text(title));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new))
                .direction(FlexDirection.ROW);
    }
}
