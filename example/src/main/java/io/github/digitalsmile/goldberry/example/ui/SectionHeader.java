package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// One section's heading inside [Scrolling]'s list.
///
/// A plain node. It used to carry the `scrollIntoView` half of this screen and
/// could not: a header inside an `affix` is *pinned to the viewport's edge* the
/// moment its section starts scrolling away, so a reveal measured against it
/// concluded the section was already visible and moved nothing. What travels
/// with the document is the affix's hole, and the affix is what hands it out
/// (ADR-0124).
/// A screen's heading: the one widget in this application that exists so a
/// stylesheet has a **type** to select on.
///
/// A `text.screen-title` did the same job for eleven screens and stopped being
/// honest when the gallery went to seven walls: a class is something any node can
/// wear, and a heading is a *kind* of node. `section-header` is selectable as an
/// element, which is what lets `showcase.css` say "a heading inside an affixed
/// section takes a surface" without a second class travelling beside it
/// (ADR-0222).
///
/// Public because [io.github.digitalsmile.goldberry.example.ShowcaseTypographyTest]
/// asserts its size through the cascade — the gallery's golden images are drawn
/// with the single-font renderer and are blind to every typographic rank there
/// is, so the hierarchy has to be asserted where it is decided.
public record SectionHeader(String title) implements Widget.Leaf, Styled, Paints {

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
        return Box.of().style(style).children(children.toArray(Box[]::new)).direction(FlexDirection.ROW);
    }
}
