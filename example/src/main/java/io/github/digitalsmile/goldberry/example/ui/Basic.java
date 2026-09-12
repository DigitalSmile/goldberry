package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Basic** screen: §1's type scale, §2's wrapped paragraph and every §3
/// control whose value is a state or a number.
///
/// ## Why it is a document *and* a class
///
/// Six of its nine cards are `basic.kdl` and three of them cannot be, and every
/// reason is an interesting one rather than incidental:
///
/// - **The prose card is in the tree only while the checkbox says so.** That is a
///   structural change and not a value one — the paragraph is *absent*, not
///   hidden — and §8's markup has no way to say it.
/// - **The road card's Turn back and Begin again are disabled while the count is
///   zero.** `disabled=#true` is a constant; a document cannot say "disabled when
///   this property is zero" and is not going to grow a way to, because a document
///   that could evaluate `clicks == 0` would be code in a data file with no stack
///   trace when it went wrong ([ADR-0062], [ADR-0110]).
/// - **The dialogs card holds what a dialog answered**, and a dialog answers
///   *later* — a person is inside the call. A document can name an action; it has
///   nowhere to put a result that arrives on a callback ([FileDialogsCard]).
///
/// So this class takes the wall the document built and appends three cards to it —
/// to the *same* masonry, because a masonry places by column height and a second
/// wall underneath would be laid out against different columns
/// (ADR-0222).
///
/// @param cards what `basic.kdl` built, inflated once by [Screen]
/// @param plus  the icon on the primary button — handed in, because a widget is a
///              value that is rebuilt and thrown away and an `Icon` owns native
///              memory that must be closed exactly once (ADR-0043)
public record Basic(ShowcaseModel model, ShowcaseModel.Actions actions, Masonry cards, Icon plus)
        implements Widget.Stateless {

    private static final String NOTE = "§1's type scale, §2's paragraph and every §3 control. Six of these cards are"
            + " basic.kdl and three of them cannot be — the verse is in the tree only"
            + " while the box beside it is ticked, Turn back is disabled while the"
            + " count is zero, and a file dialog answers long after the click that"
            + " opened it.";

    /// The paragraph, which is here to be **re-wrapped** rather than to be read.
    ///
    /// Long enough to break over several lines at any window width, and written
    /// in sentences of very different lengths so that dragging the window's edge
    /// visibly moves where the breaks fall. A block of `Lorem ipsum` would do the
    /// same thing and tell a reader nothing about whether it had done it *well*.
    private static final String PROSE = """
            Yoga proposes a width and this paragraph answers with a height, which is the only \
            thing a flexbox algorithm needs to know about text. The answer comes back through a \
            Java method called from C returning a struct by value — the fiddliest thing the \
            toolkit asks of the Foreign Function & Memory API, and the reason ADR-0017 exists.

            Drag the window's edge and the text re-wraps without being shaped again. Click a \
            button, or press Tab until one has the focus and then Space. Ctrl+T changes the \
            light from the keyboard, and Ctrl+D changes the density — every control gets 4px \
            shorter, and nothing in this file mentions a height.

            The two theme radios are one Tab stop, not two: Tab reaches the group and the arrow \
            keys move inside it. Tab away and back and you land on whichever is selected — \
            including after Ctrl+T has changed it from outside the group, because the selection \
            is the position rather than something remembered beside it.""";

    @Override
    public Widget build(BuildContext context) {
        var extra = new ArrayList<Widget>(3);
        if (model.isProseShown()) {
            extra.add(prose());
        }
        extra.add(road());
        // A value, like every other widget here: the state that remembers what
        // the last dialog answered is the card's own, made once when it is
        // mounted and kept across the rebuilds this screen does for everything
        // else.
        extra.add(new FileDialogsCard());
        return Wall.of("basic", "Basic", NOTE, cards, extra.toArray(Widget[]::new));
    }

    /// The paragraph card. Absent from the returned list rather than emptied,
    /// which is what "structural" means here: the element and every style it had
    /// resolved go with it.
    private Widget prose() {
        return new Card(
                List.of(
                        new Text("A paragraph, and a window edge to drag", Attributes.NONE.classes("card-title")),
                        new Text(PROSE).id("prose")),
                Attributes.NONE.id("prose-card").classes("wall-card"));
    }

    /// The counter, and the card that is in Java because two of its buttons
    /// answer a question about a value rather than carry a constant.
    private Widget road() {
        var walked = model.clicks();
        return new Card(
                List.of(
                        new Text("The road, in leagues", Attributes.NONE.classes("card-title")),
                        new Text(
                                        walked == 0
                                                ? "Nobody has left Bag End yet."
                                                : walked + (walked == 1 ? " league" : " leagues") + " walked.")
                                .id("leagues"),
                        new Row(
                                        new Button("March a league", actions::click)
                                                .withIcon(plus)
                                                .id("click")
                                                .styled("primary"),
                                        new Button("Turn back", actions::undo)
                                                .disabled(!model.hasClicks())
                                                .id("undo"),
                                        new Button("Begin again", actions::reset)
                                                .disabled(!model.hasClicks())
                                                .id("reset")
                                                .styled("danger"))
                                .id("actions"),
                        new Text(
                                "Two of these three are disabled until there is something to undo,"
                                        + " which is the whole reason this card is not in basic.kdl.",
                                Attributes.NONE.classes("caption"))),
                Attributes.NONE.id("road-card").classes("wall-card"));
    }
}
