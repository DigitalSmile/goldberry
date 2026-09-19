package io.github.digitalsmile.goldberry.widgets.controls.selectlist;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The open half of a `select`: the panel of options, in a popup window of its
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
/// ## Two callers, and a package of its own
///
/// §4's autocomplete is the second: "attaches a `popover` of suggestions to the
/// field", which is this panel with this keyboard and this drawing. It is shared
/// rather than copied for the reason
/// [io.github.digitalsmile.goldberry.widgets.controls.option.Option] was moved
/// into a package of its own the day *it* had two callers.
///
/// This lives here rather than in `…controls.select` because a part belongs to
/// whatever owns it, and two things own this one. The move was filed and not
/// taken for years on the grounds that the CSS type it carries is `select-list`
/// and renaming that would touch every stylesheet and every golden — which was
/// simply **wrong**: the CSS type is the string [#cssType()] returns and a Java
/// package is not part of it. Nothing outside this file moved
/// ([ADR-0417], and ADR-0182 is where the mistaken cost was written down).
///
/// The package is **not exported**, which is the other half of the move and the
/// part that was a real change. ADR-0065's rule is that a part is styleable and
/// not constructible, and this type said so in its own first paragraph while
/// sitting public in an exported package — so an application could build a
/// dropdown's panel with no dropdown around it. `…form.parts` already holds two
/// widgets' shared parts this way: public to the module, invisible outside it.
///
/// `Option.inAList()` is what makes it right for both: the arrows move the
/// focus and `Enter` commits, so a suggestion list never rewrites the field
/// under a user who is only looking (§4).
///
/// @param children the rows — the options, already told what they are
public record SelectList(List<Widget> children, Consumer<String> onTypeahead)
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
    public void onTextCapture(TextEvent event) {
        if (onTypeahead == null || event.text().isBlank()) {
            return;
        }
        onTypeahead.accept(event.text());
        event.consume();
    }

    /// `select-list`, and it did not change when this file did.
    ///
    /// The string is the whole of what a stylesheet, a golden and a selector see.
    /// It is written here and nowhere else, which is why moving the class cost one
    /// `package` line and a handful of imports ([ADR-0417]).
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
