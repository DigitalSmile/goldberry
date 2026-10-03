package dev.goldberry.example.ui.navigation;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.nav.steps.Step;
import dev.goldberry.widgets.nav.steps.Steps;
import dev.goldberry.widgets.text.Text;

/// The `steps`: every state a step can be in, and a vertical list whose reachable
/// steps take a click.
///
/// Read more: [`steps`](https://goldberry.dev/docs/components/navigation.html#steps).
record StepsCard() implements Widget.Stateful {

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "navigation-steps",
            "How far along",
            "A row of steps: done, current and upcoming, one in error and one passed but not done. In the"
                    + " vertical list, click a step you have reached; the application moves the index.",
            DocLink.to("components/navigation", "steps"));

    @Override
    public State<?> createState() {
        return new StepsState();
    }

    /// The vertical list's index, which a press asks the application to move.
    static final class StepsState extends State<StepsCard> {

        private int current = 1;

        private void goTo(int index) {
            setState(() -> current = index);
        }

        @Override
        public Widget build(BuildContext context) {
            // Two short rows rather than one long one, so each fits a card.
            var states = new Steps(1, new Step("Account", "Who you are"), new Step("Payment"), new Step("Review"))
                    .id("steps-states");
            var exceptions = new Steps(
                            2,
                            new Step("Account"),
                            new Step("Grafana", "Not signed in").complete(false),
                            new Step("Verify").error(true))
                    .id("steps-exceptions");
            var vertical = new Steps(
                            current,
                            new Step("Pack", "Rope and lembas").reachable(true),
                            new Step("Set out", "Through the Old Forest").reachable(true),
                            new Step("Arrive", "Bree, by nightfall").reachable(true))
                    .direction(Steps.Direction.VERTICAL)
                    .clickable(this::goTo)
                    .id("steps-vertical");
            return CARD.of(
                    states,
                    exceptions,
                    vertical,
                    new Text(
                            "At step " + (current + 1) + " of 3",
                            Attributes.NONE.id("steps-at").classes("caption")));
        }
    }
}
