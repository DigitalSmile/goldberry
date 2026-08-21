package io.github.digitalsmile.goldberry.widgets.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.form.field.Field;
import io.github.digitalsmile.goldberry.widgets.form.form.Form;
import io.github.digitalsmile.goldberry.widgets.form.form.FormController;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §4's layout contract and its validation model.
///
/// What is worth testing here is **when** a field speaks. Whether a `Validator`
/// returns the right answer is [ValidatorTest]'s, and needs no widget at all; a
/// field is silent until the user has finished with it and live from then on, and
/// a form makes every field speak at once when it submits. Those three moments
/// are the whole design.
///
/// Focus is moved through a real [PointerRouter] rather than by calling the
/// widget, because "blur" here means `onFocusWithin` — and what that reports for
/// a field with two controls in it is exactly the thing worth pinning.
class FormTest {

    private final TestHost host = new TestHost();

    private ElementTree mounted(Widget root) {
        var tree = new ElementTree(root, host);
        render(tree);
        return tree;
    }

    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                TestFont.get()).render(tree);
    }

    private PointerRouter routed(ElementTree tree) {
        var router = new PointerRouter();
        router.focusRoot(tree.root());
        return router;
    }

    private static Field required(String label, Property<String> value) {
        return new Field(label, List.of(TextInput.of(value, value::set)), true, null,
                Attributes.NONE);
    }

    // --- reading the tree the way a stylesheet does ----------------------------

    private static Element find(Element element, String type) {
        if (element.widget() instanceof Styled styled && styled.cssType().equals(type)) {
            return element;
        }
        for (var child : element.children()) {
            var found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Element find(ElementTree tree, String type) {
        return find(tree.root(), type);
    }

    private static List<Element> findAll(Element element, String type, List<Element> into) {
        if (element.widget() instanceof Styled styled && styled.cssType().equals(type)) {
            into.add(element);
        }
        element.children().forEach(child -> findAll(child, type, into));
        return into;
    }

    /// Whether the `field` node matches `:invalid` — the pseudo-class §4 asks
    /// for, read off the node a stylesheet sees.
    private static boolean invalid(ElementTree tree) {
        return find(tree, "field").widget() instanceof Styled styled && styled.isInvalid();
    }

    @Nested
    @DisplayName("when a field speaks")
    class Timing {

        @Test
        @DisplayName("a required field says nothing before anyone has been near it")
        void silentUntilVisited() {
            var name = Property.of("");
            var tree = mounted(required("Name", name));

            // Empty, required, and saying nothing — which is the difference
            // between a form that helps and one that shouts at a blank screen.
            assertFalse(invalid(tree));
        }

        @Test
        @DisplayName("it complains when the keyboard leaves it")
        void complainsOnBlur() {
            var name = Property.of("");
            var tree = mounted(required("Name", name));
            var router = routed(tree);

            router.focus(find(tree, "text-input"), true);
            router.focus(null, true);
            render(tree);

            assertTrue(invalid(tree));
        }

        @Test
        @DisplayName("once it has complained it forgives on the next keystroke")
        void livesAfterComplaining() {
            var name = Property.of("");
            var tree = mounted(required("Name", name));
            var router = routed(tree);
            router.focus(find(tree, "text-input"), true);
            router.focus(null, true);
            render(tree);
            assertTrue(invalid(tree));

            name.set("Jane");
            render(tree);

            // Not at the next blur: a field you have to leave and come back to
            // before it stops being red is a field that feels broken.
            assertFalse(invalid(tree));
        }

        @Test
        @DisplayName("moving between two controls in one field is not leaving it")
        void movingInsideIsNotBlur() {
            var first = Property.of("");
            var field = new Field("Range",
                    List.of(TextInput.of(first, first::set), new TextInput("", null)),
                    true, null, Attributes.NONE);
            var tree = mounted(field);
            var router = routed(tree);
            var inputs = findAll(tree.root(), "text-input", new ArrayList<>());

            router.focus(inputs.get(0), true);
            router.focus(inputs.get(1), true);
            render(tree);

            // `onFocusWithin` reports the subtree, so a field with two controls
            // behaves like a field with one — which is the whole reason blur is
            // defined that way.
            assertFalse(invalid(tree));
        }

        @Test
        @DisplayName("a field with no bound control validates nothing")
        void unboundIsLayoutOnly() {
            var tree = mounted(new Field("Name", List.of(new TextInput("", null)), true, null,
                    Attributes.NONE));
            var router = routed(tree);

            router.focus(find(tree, "text-input"), true);
            router.focus(null, true);
            render(tree);

            // `field label="…"` around something unbound is a perfectly good way
            // to lay out a read-only row, and a toolkit that refused it would be
            // refusing a use it has no argument against.
            assertFalse(invalid(tree));
        }
    }

    @Nested
    @DisplayName("what a form does")
    class Submitting {

        private final FormController controller = new FormController();

        private Form twoFields(Property<String> name, Property<String> port, Runnable onSave) {
            return new Form(List.of(required("Name", name), required("Port", port)),
                    onSave, controller, Attributes.NONE);
        }

        @Test
        @DisplayName("a controller with no form yet does nothing and reports nothing wrong")
        void detachedIsInert() {
            var loose = new FormController();

            assertFalse(loose.isAttached());
            assertFalse(loose.submit());
            assertTrue(loose.isValid(),
                    "or a Save button would disable itself on the first frame of every window");
            assertEquals(List.of(), loose.errors());
        }

        @Test
        @DisplayName("the fields find the form rather than the other way round")
        void fieldsRegister() {
            mounted(twoFields(Property.of(""), Property.of(""), null));

            assertTrue(controller.isAttached());
            assertEquals(2, controller.fieldCount());
        }

        @Test
        @DisplayName("a field nested in rows and columns still finds it")
        void findsThroughNesting() {
            var name = Property.of("");
            mounted(new Form(List.of(
                    new Column(List.of(
                            new Column(List.of(required("Name", name)), Attributes.NONE)),
                            Attributes.NONE)),
                    null, controller, Attributes.NONE));

            // `findAncestorState` walks to the root, so how deep a document
            // nested its fields is not the form's problem.
            assertEquals(1, controller.fieldCount());
        }

        @Test
        @DisplayName("submitting refuses while anything is wrong, and lists all of it")
        void gatesOnValidity() {
            var saved = new boolean[1];
            var tree = mounted(twoFields(Property.of(""), Property.of(""), () -> saved[0] = true));

            var submitted = controller.submit();
            render(tree);

            assertFalse(submitted);
            assertFalse(saved[0]);
            // Every field, not just the first: a summary with one entry when two
            // things are wrong sends somebody round the form twice.
            assertEquals(List.of(Field.REQUIRED_MESSAGE, Field.REQUIRED_MESSAGE),
                    controller.errors());
        }

        @Test
        @DisplayName("submitting makes an untouched field speak")
        void checksTheUnvisited() {
            var tree = mounted(twoFields(Property.of(""), Property.of(""), null));

            controller.submit();
            render(tree);

            // The one moment a field complains without having been left first.
            // Otherwise a form submits with an untouched required field empty.
            assertTrue(invalid(tree));
        }

        @Test
        @DisplayName("it runs the handler once everything passes")
        void submitsWhenValid() {
            var saved = new boolean[1];
            mounted(twoFields(Property.of("Jane"), Property.of("8080"), () -> saved[0] = true));

            assertTrue(controller.submit());
            assertTrue(saved[0]);
            assertEquals(List.of(), controller.errors());
        }

        @Test
        @DisplayName("asking whether it is valid reddens nothing")
        void isValidIsSilent() {
            var tree = mounted(twoFields(Property.of(""), Property.of(""), null));

            assertFalse(controller.isValid());
            render(tree);

            // A Save button asks this to decide whether it is available, and a
            // form that reddened itself to answer would redden before anyone had
            // typed a character.
            assertFalse(invalid(tree));
        }

        @Test
        @DisplayName("resetting forgets the complaints")
        void resets() {
            var tree = mounted(twoFields(Property.of(""), Property.of(""), null));
            controller.submit();
            render(tree);
            assertTrue(invalid(tree));

            controller.reset();
            render(tree);

            assertFalse(invalid(tree));
            assertEquals(List.of(), controller.errors());
        }

        @Test
        @DisplayName("a form going away leaves its controller inert")
        void detachesOnUnmount() {
            var tree = mounted(twoFields(Property.of(""), Property.of(""), null));

            tree.unmount();

            // A controller outlives its form — an application holds it — so one
            // still pointing at a dead tree would submit something nobody can
            // see.
            assertFalse(controller.isAttached());
            assertFalse(controller.submit());
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a document writes what §4 spells")
        void inflates() {
            var widgets = Widgets.inflater().inflateAll(KdlParser.parse("""
                    form {
                        field label="Name" required=#true {
                            text-input placeholder="Jane Doe"
                        }
                    }
                    """));
            var form = (Form) widgets.getFirst();
            var field = (Field) form.children().getFirst();

            assertEquals("Name", field.label());
            assertTrue(field.required());
            assertEquals(1, field.children().size());
        }

        @Test
        @DisplayName("there is no validator= property, because markup is data")
        void noValidatorInMarkup() {
            var field = (Field) Widgets.inflater()
                    .inflateAll(KdlParser.parse("field label=\"Name\" required=#true")).getFirst();

            // `required` is the one rule that *is* data. Anything else is a
            // function, exactly as `disabled` is a constant in markup and a
            // predicate in Java.
            assertTrue(field.validator() == null);
            assertFalse(field.rule().check("").isValid(), "and required still applies");
        }

        @Test
        @DisplayName("the node a stylesheet sees is `field`, once")
        void oneNode() {
            var tree = mounted(required("Name", Property.of("")));

            // The stateful widget styles nothing, or every rule would apply
            // twice — `scroll`'s and `tabs`' arrangement.
            assertEquals("field", ((Styled) find(tree, "field").widget()).cssType());
            assertFalse(tree.root().widget() instanceof Styled);
        }
    }
}
