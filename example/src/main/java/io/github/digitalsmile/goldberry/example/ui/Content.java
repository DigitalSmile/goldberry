package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Text** screen: §2's wrapped paragraph and the three buttons that act on
/// the model.
///
/// **One of the two screens that is not a document**, and the reason is the
/// instructive one: Undo and Reset are `disabled` when the click count is zero,
/// and §8's markup has no expressions — `disabled=#true` is a constant, and a
/// document cannot say "disabled when this property is zero". A document that
/// could evaluate `clicks == 0` would be code in a data file, with no stack trace
/// when it went wrong ([ADR-0062], [ADR-0110]).
///
/// The prose is also *in the tree* only when the checkbox on the Controls screen
/// says so, which is a structural change rather than a value one — the other
/// half of why this pane stays in Java.
///
/// @param model what it reads and what its buttons ask of
/// @param plus  the icon on the primary button — handed in, because a widget is
///              a value that is rebuilt and thrown away and an `Icon` owns native
///              memory that must be closed exactly once (ADR-0043)
public record Content(ShowcaseModel model, Icon plus) implements Widget.Stateless {

    private static final String PROSE = """
            Yoga proposes a width and this paragraph answers with a height, which is the only \
            thing a flexbox algorithm needs to know about text. The answer comes back through a \
            Java method called from C returning a struct by value — the fiddliest thing the \
            toolkit asks of the Foreign Function & Memory API, and the reason ADR-0017 exists.

            Drag the window's edge and the text re-wraps without being shaped again. Click a \
            button, or press Tab until one has the focus and then Space. Ctrl+T switches the \
            theme from the keyboard, and Ctrl+D switches the density — every control gets 4px \
            shorter, and nothing in this file mentions a height.

            The theme radios are one Tab stop, not two: Tab reaches the group and the arrow keys \
            move inside it. Tab away and back and you land on whichever is selected — including \
            after Ctrl+T has changed it from outside the group, because the selection is the \
            position rather than something remembered beside it.""";

    @Override
    public Widget build(BuildContext context) {
        var actions = new Row(
                new Button("Click me", model::click)
                        .withIcon(plus).id("click").styled("primary"),
                new Button("Undo", model::undo).disabled(!model.hasClicks()).id("undo"),
                new Button("Reset", model::reset).disabled(!model.hasClicks())
                        .id("reset").styled("danger"))
                .id("actions");

        return model.isProseShown()
                ? new Column(new Text(PROSE).id("prose"), actions).id("screen-text")
                : new Column(actions).id("screen-text");
    }
}
