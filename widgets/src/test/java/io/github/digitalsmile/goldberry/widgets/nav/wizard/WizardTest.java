package io.github.digitalsmile.goldberry.widgets.nav.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.bind.registry.ActionRegistry;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.nav.steps.Step;
import io.github.digitalsmile.goldberry.widgets.nav.steps.StepState;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §6's `wizard` — `steps`, a page, a bar, and no policy ([ADR-0344]).
class WizardTest {

    private final List<String> log = new ArrayList<>();

    private TestHost host;

    @BeforeEach
    void setUp() {
        host = new TestHost();
    }

    private Wizard wizard(int current) {
        return new Wizard(
                        current,
                        new WizardPage("Account", new Text("one")).describe("Who you are"),
                        new WizardPage("Payment", new Button("Pay", () -> {}).id("pay")).error(false),
                        new WizardPage("Review", new Text("three")))
                .onBack(() -> log.add("back"))
                .onNext(() -> log.add("next"))
                .onFinish(() -> log.add("finish"))
                .withAttributes(Attributes.NONE.id("signup"));
    }

    private ElementTree open(Widget wizard) {
        var tree = new ElementTree(wizard, host);
        tree.flush();
        return tree;
    }

    private static Element panel(ElementTree tree) {
        return tree.root().children().getFirst();
    }

    @Nested
    @DisplayName("the column")
    class TheColumn {

        @Test
        @DisplayName("an indicator, the page, and the bar — and the composition styles nothing")
        void threeParts() {
            var tree = open(wizard(1));
            var painted = panel(tree);

            assertInstanceOf(Wizard.class, tree.root().widget());
            assertEquals("wizard", painted.type());
            assertEquals("signup", painted.id());
            // The indicator is the standalone `steps`, one level down from its
            // own composition node.
            assertEquals(
                    List.of("steps", "wizard-content", "wizard-actions"),
                    painted.children().stream()
                            .map(child -> child.type() == null
                                    ? child.children().getFirst().type()
                                    : child.type())
                            .toList());
        }

        @Test
        @DisplayName("the indicator is one step per page, with the page's words and state")
        void indicatorFromPages() {
            var steps = Described.of(open(wizard(1)), Step.class);

            assertEquals(
                    List.of("Account", "Payment", "Review"),
                    steps.stream().map(Step::label).toList());
            assertEquals("Who you are", steps.getFirst().description());
            assertEquals(StepState.DONE, steps.get(0).state());
            assertEquals(StepState.CURRENT, steps.get(1).state());
            assertTrue(steps.stream().noneMatch(Step::isFocusable), "a wizard's indicator is a picture by default");
        }

        @Test
        @DisplayName("only the current page's content is built")
        void onlyTheCurrentPage() {
            var tree = open(wizard(1));
            var texts =
                    Described.of(tree, Text.class).stream().map(Text::content).toList();

            assertEquals(List.of(), texts, "pages one and three are not built at all");
            assertEquals(
                    1,
                    Described.of(tree, Button.class).stream()
                            .filter(b -> "Pay".equals(b.label()))
                            .count());
        }

        @Test
        @DisplayName("the content area carries the wizard's content id, for the focus request")
        void contentIsNamed() {
            var content = Described.elementOf(open(wizard(1)), WizardContent.class);

            assertEquals("signup-content", content.id());
        }
    }

    @Nested
    @DisplayName("the bar")
    class TheBar {

        private List<Button> buttons(int current) {
            return Described.of(open(wizard(current)), Button.class).stream()
                    .filter(button -> !"Pay".equals(button.label()))
                    .toList();
        }

        @Test
        @DisplayName("Back then Next, and Back is disabled on the first page rather than missing")
        void backThenNext() {
            var first = buttons(0);

            assertEquals(
                    List.of("Back", "Next"), first.stream().map(Button::label).toList());
            assertTrue(first.get(0).disabled());
            assertFalse(first.get(1).disabled());
            assertTrue(first.get(1).classes().contains("primary"), "the affirmative is the primary button");
        }

        @Test
        @DisplayName("Finish stands where Next would on the last page")
        void finishOnTheLastPage() {
            assertEquals(
                    List.of("Back", "Finish"),
                    buttons(2).stream().map(Button::label).toList());
        }

        @Test
        @DisplayName("the buttons raise events and move nothing themselves")
        void raisesEvents() {
            var middle = buttons(1);
            middle.get(0).onPress().run();
            middle.get(1).onPress().run();
            buttons(2).get(1).onPress().run();

            assertEquals(List.of("back", "next", "finish"), log);
        }

        @Test
        @DisplayName("a wizard given no Back has no Back button")
        void noBack() {
            var buttons = Described.of(open(wizard(1).onBack(null)), Button.class).stream()
                    .map(Button::label)
                    .toList();

            assertEquals(List.of("Pay", "Next"), buttons);
        }

        @Test
        @DisplayName("the words are the application's to change")
        void relabelled() {
            var wizard = wizard(2).labels(new Wizard.Labels("Zurück", "Weiter", "Bezahlen"));

            assertEquals(
                    List.of("Zurück", "Bezahlen"),
                    Described.of(open(wizard), Button.class).stream()
                            .map(Button::label)
                            .filter(label -> !"Pay".equals(label))
                            .toList());
        }
    }

    @Nested
    @DisplayName("moving")
    class Moving {

        @Test
        @DisplayName("a bound number is the page, clamped into the pages")
        void boundPage() {
            var current = Property.of(7);

            var wizard = wizard(0).bound(current);

            assertEquals(2, wizard.resolvedCurrent());
            current.set(-3);
            assertEquals(0, wizard.resolvedCurrent());
        }

        @Test
        @DisplayName("advancing asks the window to focus the new page, and opening does not")
        void advancingMovesTheKeyboard() {
            var tree = open(wizard(0));
            assertFalse(host.hasPendingTimer(), "opening on page one must not take the keyboard");

            tree.update(wizard(1));
            tree.flush();

            assertTrue(host.hasPendingTimer(), "the request is scheduled, not made mid-build");
            host.tick();
            assertEquals(List.of("signup-content"), host.focusRequests());

            tree.flush();
            assertEquals(1, host.focusRequests().size(), "a rebuild on the same page asks again");
        }

        @Test
        @DisplayName("the accessible name is the page and the position")
        void accessibleName() {
            var panel = (WizardPanel) panel(open(wizard(1))).widget();

            assertEquals("Payment, step 2 of 3", panel.accessibleName());
        }

        @Test
        @DisplayName("a clickable indicator reports a reachable step's index")
        void goTo() {
            var pressed = new ArrayList<Integer>();
            var wizard = new Wizard(
                            2, new WizardPage("One").reachable(true), new WizardPage("Two"), new WizardPage("Three"))
                    .goTo(pressed::add);
            var steps = Described.of(open(wizard), Step.class);

            assertTrue(steps.get(0).isFocusable());
            assertFalse(steps.get(1).isFocusable());
            steps.get(0).onPress().run();
            assertEquals(List.of(0), pressed);
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a wizard and its pages inflate with what the document may say")
        void inflates() {
            var actions = ActionRegistry.strict()
                    .bind("app.back", () -> log.add("back"))
                    .bind("app.next", () -> log.add("next"))
                    .bind("app.finish", () -> log.add("finish"));
            var wizard = (Wizard)
                    Widgets.inflater(actions).inflateAll(KdlParser.parse("""
                            wizard current=1 back="app.back" next="app.next" finish="app.finish" finish-label="Pay" id="w" {
                                page "Account" description="Who you are" { text "one" }
                                page error=#true "Payment" { text "two" }
                                page reachable=#true "Review" { text "three" }
                            }
                            """)).getFirst();

            assertEquals(1, wizard.current());
            assertEquals(3, wizard.pages().size());
            assertEquals("Who you are", wizard.pages().getFirst().description());
            assertTrue(wizard.pages().get(1).error());
            assertTrue(wizard.pages().get(2).reachable());
            assertEquals("Pay", wizard.labels().finish());
            assertNull(wizard.goTo(), "no go-to= is an indicator that is a picture");

            var buttons = Described.of(open(wizard), Button.class);
            buttons.get(1).onPress().run();
            assertEquals(List.of("next"), log);
            assertEquals(
                    StepState.ERROR,
                    Described.of(open(wizard), Step.class).get(1).state());
        }

        @Test
        @DisplayName("a wizard with no actions written has no buttons")
        void noActions() {
            var wizard = (Wizard) Widgets.inflater()
                    .inflateAll(KdlParser.parse("wizard { page \"One\"; page \"Two\" }"))
                    .getFirst();

            assertEquals(List.of(), Described.of(open(wizard), Button.class));
        }
    }
}
