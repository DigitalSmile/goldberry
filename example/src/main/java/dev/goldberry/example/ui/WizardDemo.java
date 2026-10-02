package dev.goldberry.example.ui;

import java.util.List;

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

/// The `steps` and the `wizard`, on one card and one index.
///
/// The wizard owns no policy — Back, Next and Finish are requests the state
/// answers by moving the index — and the standalone `steps` above it reads the
/// same index, so the two cannot drift. The list is `clickable`, and only the
/// pages already reached are `reachable`: a press on a step ahead of where you
/// have been is refused by the application, not by the widget, which is the
/// whole of what reachability means.
///
/// **In Java** for [TabsDemo]'s reason: the index changes while the window is
/// open, and markup is data.
///
/// Read more: [Wizard](https://goldberry.dev/docs/components/navigation.html#wizard).
public record WizardDemo() implements Widget.Stateful {

    private static final List<String> PAGES = List.of("Provisions", "Company", "Road");

    @Override
    public State<?> createState() {
        return new DemoState();
    }

    static final class DemoState extends State<WizardDemo> {

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
                            new Step("Provisions", "What to carry").reachable(reached >= 0),
                            new Step("Company", "Who comes along").reachable(reached >= 1),
                            new Step("Road", "Which way").reachable(reached >= 2))
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
            return Notifications.card(
                    "wizard-card",
                    "Where a process is",
                    List.of(
                            indicator,
                            new Text(
                                    "The list above and the wizard below read one index. Back, Next and"
                                            + " Finish ask; the application moves. A step you have reached"
                                            + " is reachable and takes a click; one ahead of you is not,"
                                            + " because only the application knows whether page three is"
                                            + " valid yet.",
                                    Attributes.NONE.classes("caption")),
                            wizard));
        }
    }
}
