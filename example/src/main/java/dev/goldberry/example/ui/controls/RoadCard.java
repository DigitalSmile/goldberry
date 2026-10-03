package dev.goldberry.example.ui.controls;

import dev.goldberry.bind.Subscription;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.icon.Icon;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// A counter, and the card that shows what a button's **state** does: Turn back
/// and Begin again are disabled while there is nothing to undo.
///
/// A disabled flag that follows a value is a question a document cannot ask, so
/// the card rebuilds when the count moves and asks it here.
///
/// Read more: [`button`](https://goldberry.dev/docs/components/buttons.html#button).
///
/// @param model   where the count is kept
/// @param actions what the buttons ask for
/// @param plus    the icon on March a league and on the bare `+`
public record RoadCard(ShowcaseModel model, ShowcaseModel.Actions actions, Icon plus) implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new RoadState();
    }

    /// The subscription to the count.
    static final class RoadState extends State<RoadCard> {

        @SuppressWarnings("NullAway.Init") // subscribed in initState()
        private Subscription watching;

        @Override
        protected void initState() {
            watching = Models.observable(widget().model(), "app.clicks").subscribe(_ -> setState(() -> {}));
        }

        /// A property outlives the screen, so a listener left behind would keep
        /// this card alive and rebuild something nobody can see.
        @Override
        protected void dispose() {
            watching.close();
        }

        @Override
        public Widget build(BuildContext context) {
            var card = widget();
            var walked = card.model().clicks();
            var nothingToUndo = !card.model().hasClicks();
            var march = Swept.after(card.model(), card.actions()::click);
            return new ShowcaseCard(
                            "road-card",
                            "A disabled button",
                            "A disabled button still lays out and paints, but refuses every route to its action and"
                                    + " leaves the Tab order. Turn back and Begin again stay disabled until there is"
                                    + " a league to undo.",
                            DocLink.to("components/buttons", "button"))
                    .of(
                            new Text(
                                            walked == 0
                                                    ? "Nobody has left Bag End yet."
                                                    : walked + (walked == 1 ? " league" : " leagues") + " walked.")
                                    .id("leagues"),
                            new Row(
                                            new Button("March a league", march)
                                                    .withIcon(card.plus())
                                                    .id("click")
                                                    .styled("primary"),
                                            new Button(
                                                    "",
                                                    card.plus(),
                                                    march,
                                                    false,
                                                    Attributes.NONE
                                                            .id("click-icon")
                                                            .name("March a league")),
                                            new Button("Turn back", Swept.after(card.model(), card.actions()::undo))
                                                    .disabled(nothingToUndo)
                                                    .id("undo"),
                                            new Button("Begin again", Swept.after(card.model(), card.actions()::reset))
                                                    .disabled(nothingToUndo)
                                                    .id("reset")
                                                    .styled("danger"))
                                    .id("actions"));
        }
    }
}
