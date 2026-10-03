package dev.goldberry.example.ui.navigation;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.checkbox.Checkbox;
import dev.goldberry.widgets.nav.steps.Step;
import dev.goldberry.widgets.nav.steps.Steps;
import dev.goldberry.widgets.nav.wizard.Wizard;
import dev.goldberry.widgets.nav.wizard.WizardPage;
import dev.goldberry.widgets.text.Text;

/// A `wizard`, with a standalone `steps` above it reading the same index.
///
/// The wizard owns no policy: Back, Next and Finish ask, and this state moves
/// the index. Only the pages already reached are reachable from the list above.
///
/// Read more: [`wizard`](https://goldberry.dev/docs/components/navigation.html#wizard).
record WizardCard() implements Widget.Stateful {

    private static final List<String> PAGES = List.of("Provisions", "Company", "Road");

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "wizard-card",
            "One page at a time",
            "A wizard puts steps over pages, with Back, Next and Finish under them. The buttons only ask; the"
                    + " application moves the index, and the list above reads the same one. Click a step you have"
                    + " reached to go back to it.",
            DocLink.to("components/navigation", "wizard"));

    @Override
    public State<?> createState() {
        return new WizardState();
    }

    /// The page shown and the furthest page reached.
    static final class WizardState extends State<WizardCard> {

        private int current;

        /// The furthest page reached, which is what makes a step reachable.
        private int reached;

        private void goTo(int index) {
            setState(() -> {
                current = Math.clamp(index, 0, PAGES.size() - 1);
                reached = Math.max(reached, current);
            });
        }

        @Override
        public Widget build(BuildContext context) {
            var indicator = new Steps(
                            current,
                            new Step("Provisions").reachable(reached >= 0),
                            new Step("Company").reachable(reached >= 1),
                            new Step("Road").reachable(reached >= 2))
                    .clickable(this::goTo)
                    .id("journey-steps");
            var wizard = new Wizard(
                            current,
                            new WizardPage(
                                    "Provisions",
                                    new Text("Lembas, a rope, and a spare cloak.", Attributes.NONE.classes("caption")),
                                    new Checkbox("Pack the rope", Checkbox.Value.CHECKED)),
                            new WizardPage(
                                    "Company",
                                    new Text(
                                            "Four hobbits, and whoever else turns up.",
                                            Attributes.NONE.classes("caption")),
                                    new Checkbox("Wait for Gandalf", Checkbox.Value.UNCHECKED)),
                            new WizardPage(
                                    "Road", new Text("Through the Old Forest.", Attributes.NONE.classes("caption"))))
                    .onBack(() -> goTo(current - 1))
                    .onNext(() -> goTo(current + 1))
                    .onFinish(() -> goTo(0))
                    .id("journey");
            return CARD.of(indicator, wizard);
        }
    }
}
