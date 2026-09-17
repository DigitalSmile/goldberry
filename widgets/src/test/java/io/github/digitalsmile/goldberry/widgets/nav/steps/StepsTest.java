package io.github.digitalsmile.goldberry.widgets.nav.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.bind.registry.ActionRegistry;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// §6's `steps` — where a process is, as a list ([ADR-0344]).
///
/// The list is a composition, so what is asserted is read off the **built**
/// tree: which step is current, which connectors are filled, and which steps
/// take a press.
class StepsTest {

    private final List<Integer> pressed = new ArrayList<>();

    private static Element listNode(Steps steps) {
        return new ElementTree(steps).root().children().getFirst();
    }

    private static List<Widget> row(Steps steps) {
        return listNode(steps).children().stream().map(Element::widget).toList();
    }

    private static Steps three(int current) {
        return new Steps(current, new Step("Account", "Who you are"), new Step("Payment"), new Step("Review"));
    }

    private static List<String> words(List<Widget> row) {
        return row.stream()
                .map(widget -> switch (widget) {
                    case Step step -> step.label() + ":" + step.state().word();
                    case StepConnector connector -> connector.done() ? "==" : "--";
                    default -> widget.getClass().getSimpleName();
                })
                .toList();
    }

    @Nested
    @DisplayName("the row")
    class TheRow {

        @Test
        @DisplayName("a connector goes between every pair, and is filled behind a done step")
        void connectorsAreInterleaved() {
            assertEquals(
                    List.of("Account:done", "==", "Payment:current", "--", "Review:upcoming"), words(row(three(1))));
        }

        @Test
        @DisplayName("the row is the styled node, and the composition styles nothing")
        void theListIsWhatCarriesTheCssType() {
            var root = new ElementTree(three(0).id("progress")).root();
            var painted = root.children().getFirst();

            assertInstanceOf(Steps.class, root.widget());
            assertInstanceOf(StepList.class, painted.widget());
            assertEquals("steps", painted.type());
            assertEquals("progress", painted.id(), "the id travels down to the node a stylesheet sees");
        }

        @Test
        @DisplayName("a vertical list says so with a class")
        void verticalIsAClass() {
            var vertical = listNode(three(0).direction(Steps.Direction.VERTICAL));
            var horizontal = listNode(three(0));

            assertTrue(vertical.classes().contains("vertical"));
            assertFalse(horizontal.classes().contains("vertical"));
        }

        @Test
        @DisplayName("a step is a marker beside its words")
        void aStepIsAMarkerAndABody() {
            var step = listNode(three(1)).children().get(2);
            var parts = step.children().stream().map(Element::type).toList();

            assertEquals(List.of("step-marker", "step-body"), parts);
            var body = step.children().get(1);
            assertEquals(
                    List.of("step-label"),
                    body.children().stream().map(Element::type).toList());
        }

        @Test
        @DisplayName("a description is a second line under the label")
        void aDescriptionIsASecondLine() {
            var step = listNode(three(0)).children().getFirst();
            var body = step.children().get(1);

            assertEquals(
                    List.of("step-label", "step-description"),
                    body.children().stream().map(Element::type).toList());
        }
    }

    @Nested
    @DisplayName("where each step stands")
    class States {

        @Test
        @DisplayName("before is done, at is current, after is upcoming")
        void fromTheIndex() {
            var steps = row(three(1)).stream()
                    .filter(Step.class::isInstance)
                    .map(Step.class::cast)
                    .toList();

            assertEquals(StepState.DONE, steps.get(0).state());
            assertEquals(StepState.CURRENT, steps.get(1).state());
            assertEquals(StepState.UPCOMING, steps.get(2).state());
            assertTrue(steps.get(1).isChecked(), "the current step is :checked");
            assertTrue(steps.get(1).classes().contains("current"), "and its state is a class");
        }

        @Test
        @DisplayName("an index off the end is a process that has not started")
        void nothingCurrent() {
            var steps = row(three(-1)).stream()
                    .filter(Step.class::isInstance)
                    .map(Step.class::cast)
                    .toList();

            assertTrue(steps.stream().allMatch(step -> step.state() == StepState.UPCOMING));
        }

        @Test
        @DisplayName("error is the step's own word and overrides the index")
        void errorOverrides() {
            var list = new Steps(2, new Step("One"), new Step("Two").error(true), new Step("Three"));

            assertEquals(List.of("One:done", "==", "Two:error", "--", "Three:current"), words(row(list)));
        }

        @Test
        @DisplayName("a bound number is the index")
        void boundIndex() {
            var current = Property.of(2);
            var list = three(0).bound(current);

            assertEquals(2, list.resolvedCurrent());
            assertEquals(StepState.CURRENT, ((Step) row(list).get(4)).state());
        }

        @Test
        @DisplayName("the accessible name carries the position and the state")
        void accessibleName() {
            var payment = (Step) row(three(1)).get(2);

            assertEquals("Payment, step 2 of 3, current", payment.accessibleName());
            assertEquals(Role.ROW, payment.role());
        }
    }

    @Nested
    @DisplayName("pressing")
    class Pressing {

        private Steps clickable() {
            return new Steps(
                            2,
                            new Step("Account").reachable(true),
                            new Step("Payment"),
                            new Step("Review").reachable(true))
                    .clickable(pressed::add);
        }

        @Test
        @DisplayName("a read-only list takes no focus and no press")
        void readOnly() {
            var steps = row(three(1)).stream()
                    .filter(Step.class::isInstance)
                    .map(Step.class::cast)
                    .toList();

            assertTrue(steps.stream().noneMatch(Step::isFocusable));
        }

        @Test
        @DisplayName("only a reachable step in a clickable list takes a press, and reports its index")
        void reachableOnly() {
            var steps = row(clickable()).stream()
                    .filter(Step.class::isInstance)
                    .map(Step.class::cast)
                    .toList();

            assertTrue(steps.get(0).isFocusable());
            assertFalse(steps.get(1).isFocusable(), "the application did not say Payment is reachable");
            assertTrue(steps.get(2).isFocusable());

            click(steps.get(0));
            click(steps.get(1));
            assertEquals(List.of(0), pressed);
        }

        @Test
        @DisplayName("Space and Enter press it too")
        void keyboard() {
            var account = (Step) row(clickable()).getFirst();

            account.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, null));
            account.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, null));
            account.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.A, Modifiers.NONE, false, null));

            assertEquals(List.of(0, 0), pressed);
        }

        private static void click(Step step) {
            step.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, new ElementTree(step).root()));
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a list and its steps inflate with what the document may say")
        void inflates() {
            // A numeric change crosses the registry as the string a document
            // would have written (ADR-0073), so the handler parses it.
            var actions = ActionRegistry.strict()
                    .bind("app.go-step", (String index) -> pressed.add((int) Double.parseDouble(index)));
            var list = (Steps)
                    Widgets.inflater(actions).inflateAll(KdlParser.parse("""
                            steps current=1 direction="vertical" clickable=#true change="app.go-step" id="p" {
                                step reachable=#true "Account" description="Who you are"
                                step error=#true "Payment"
                                step "Review"
                            }
                            """)).getFirst();

            assertEquals(1, list.current());
            assertEquals(Steps.Direction.VERTICAL, list.direction());
            assertTrue(list.clickable());
            assertEquals("p", list.attributes().id());
            assertEquals(List.of("Account:done", "==", "Payment:error", "--", "Review:upcoming"), words(row(list)));

            var account = (Step) row(list).getFirst();
            assertEquals("Who you are", account.description());
            click(account);
            assertEquals(List.of(0), pressed);
        }

        @Test
        @DisplayName("a document cannot write a step's state")
        void stateIsNotAnAttribute() {
            var written = (Step) Widgets.inflater()
                    .inflateAll(KdlParser.parse("step current=#true \"Home\""))
                    .getFirst();

            assertEquals(StepState.UPCOMING, written.state());
            assertFalse(written.isChecked());
        }

        @Test
        @DisplayName("a step needs a word")
        void aStepNeedsALabel() {
            assertThrows(IllegalArgumentException.class, () -> new Step(""));
        }

        private static void click(Step step) {
            step.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, new ElementTree(step).root()));
        }
    }
}
