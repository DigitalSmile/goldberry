package dev.goldberry.widgets.controls.selectlist;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.FocusScope;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The open half of a `select`: the panel of options, in a popup window of its
/// own, styleable as `select-list`.
///
/// A part — CSS-selectable and not constructible from outside the module — and
/// a sibling of `menu` rather than a use of it: the two are the same drawing
/// and different meanings, a set of *values* where a menu is a set of
/// *commands*. Sharing the type would mean a stylesheet could only tell a
/// dropdown from a menu by its ancestor, and there is no ancestor — each is the
/// root of its own tree in its own window.
///
/// [FocusScope#VERTICAL], which is the whole of its keyboard: `Up` and `Down`
/// rove between the rows, and an [dev.goldberry.widgets.controls.option.Option]
/// told it is in a list moves the focus without choosing, so `Enter` commits
/// and a suggestion list never rewrites the field under a user who is only
/// looking. Horizontal roving is absent for `menu`'s reason: a list is one
/// column, and `Left` and `Right` are not its to take.
///
/// ## Two callers, and a package of its own
///
/// `text-input`'s autocomplete is the second: it attaches a panel of
/// suggestions to the field, which is this panel with this keyboard and this
/// drawing. A part belongs to whatever owns it, and two things own this one, so
/// it lives here rather than in `…controls.select`. The CSS type is the string
/// [#cssType()] returns and a Java package is not part of it, so nothing a
/// stylesheet or a golden sees depends on where the class sits.
///
/// The package is **not exported**: a part is styleable and not constructible,
/// and a public type in an exported package would let an application build a
/// dropdown's panel with no dropdown around it. `…form.parts` already holds two
/// widgets' shared parts this way: public to the module, invisible outside it.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#select).
///
/// @param children the rows — the options, already told what they are
public record SelectList(List<Widget> children, @Nullable Consumer<String> onTypeahead)
        implements Widget.Leaf, Styled, Paints, Handles {

    /// The canonical constructor, written out so that the parameters taking null for a default can say so.
    public SelectList(@Nullable List<Widget> children, @Nullable Consumer<String> onTypeahead) {
        children = List.copyOf(children == null ? List.of() : children);
        this.children = children;
        this.onTypeahead = onTypeahead;
    }

    /// A list with no typeahead, which is what a golden wants: the panel is the
    /// same drawing either way and a picture has nobody to report to.
    public SelectList(List<Widget> children) {
        this(children, null);
    }

    /// The typeahead, on the **open** control.
    ///
    /// On the **capture** phase, because the focused node inside an open popup is
    /// an `option` — a row that does not know what typing means and would
    /// otherwise be where the letters stopped. The list is the only thing in that
    /// tree with the whole set to search, so it has to read the text before its
    /// children do.
    ///
    /// It calls the same method the closed control does, so typing `n`, `n`, `n`
    /// cycles the same options in the same order whether the list is showing or
    /// not — which is the point of fixing this rather than writing a second
    /// typeahead for the open case.
    @Override
    public void onTextCapture(TextEvent event) {
        if (onTypeahead == null || event.text().isBlank()) {
            return;
        }
        onTypeahead.accept(event.text());
        event.consume();
    }

    /// `select-list`.
    ///
    /// The string is the whole of what a stylesheet, a golden and a selector see,
    /// and it is written here and nowhere else — the Java package is not part of
    /// it.
    @Override
    public String cssType() {
        return "select-list";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public FocusScope focusScope() {
        return FocusScope.VERTICAL;
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
