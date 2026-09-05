package io.github.digitalsmile.goldberry.widgets.controls.select;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The open half of a [Select]: the panel of options, in a popup window of its
/// own.
///
/// A **part** — CSS-selectable and not constructible
/// (ADR-0065)
/// — and a sibling of `menu` rather than a use of it: the two are the same
/// drawing and different meanings, and §3's list is a set of *values* where §8's
/// is a set of *commands*. Sharing the type would mean a stylesheet could only
/// tell a dropdown from a menu by its ancestor, and there is no ancestor —
/// each is the root of its own tree ([ADR-0103]).
///
/// [FocusScope#VERTICAL], which is the whole of its keyboard: `Up` and `Down`
/// rove between the rows, and an [io.github.digitalsmile.goldberry.widgets.controls.option.Option]
/// selects when the keyboard lands on it, exactly as it does inside a
/// `radio-group` or a `segmented`. So arrows move the *value* rather than a
/// highlight that has to be committed — the behaviour a GTK or macOS dropdown
/// has, and the one that needs no second notion of "pending" anywhere in the
/// toolkit ([ADR-0141]).
///
/// Horizontal roving is absent for `menu`'s reason: a list is one column, and
/// `Left` and `Right` are not its to take.
///
/// ## Two callers now
///
/// §4's autocomplete is the second: "attaches a `popover` of suggestions to the
/// field", which is this panel with this keyboard and this drawing. It is public
/// rather than copied for the reason
/// [io.github.digitalsmile.goldberry.widgets.controls.option.Option] was moved
/// into a package of its own the day *it* had two callers — and moving this one
/// the same way is the follow-up that has not been taken, because the CSS type
/// it carries is `select-list` and renaming that is a change to every stylesheet
/// and every golden rather than to this file ([ADR-0182]).
///
/// `Option.inAList()` is what makes it right for both: the arrows move the
/// focus and `Enter` commits, so a suggestion list never rewrites the field
/// under a user who is only looking (§4).
///
/// @param children the rows — the options, already told what they are
public record SelectList(List<Widget> children, java.util.function.Consumer<String> onTypeahead)
        implements Widget.Leaf, Styled, Paints, Handles {

    public SelectList {
        children = List.copyOf(children == null ? List.of() : children);
    }

    /// A list with no typeahead, which is what a golden wants: the panel is the
    /// same drawing either way and a picture has nobody to report to.
    public SelectList(List<Widget> children) {
        this(children, null);
    }

    /// §3's typeahead, on the **open** control ([ADR-0246]).
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
    public void onTextCapture(io.github.digitalsmile.goldberry.input.event.TextEvent event) {
        if (onTypeahead == null || event.text().isBlank()) {
            return;
        }
        onTypeahead.accept(event.text());
        event.consume();
    }

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
