package io.github.digitalsmile.goldberry.widgets.form.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.form.form.Form;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a `field` actually looks like, in both of §4's layouts and with something
/// wrong with it.
///
/// These images exist because of two reports, and neither was reachable by any
/// assertion the tests already had:
///
/// - **"validation text appeared after the text input"** — in a label column a
///   field is a *row*, so a message that was a flat third child landed **beside**
///   the control instead of under it. §4 asks for a label column and a message
///   below in the same sentence, and they are a row and a column.
/// - **"the save button is not aligned with the labels"** — an action row beside
///   the form starts at the form's left edge, under the labels, where every form
///   ever designed puts it under the *controls*.
///
/// Both are geometry, both looked obviously wrong the moment somebody put a form
/// on a screen, and both passed every test. The fix for the first is structural —
/// a field is two boxes ([FieldBody]) — and these are the images that say so.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class FieldGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Attributes id(String id, String... classes) {
        return new Attributes(id, Set.of(classes), id);
    }

    private static final String SCENE = """
            #form  { padding: 12px; background: var(--gb-surface) }
            """;

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var tree = new ElementTree(content);
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    /// A field with something to say, built directly — the state that a form
    /// only reaches after somebody has left a control empty.
    private static Widget field(String label, boolean required, String message, String value) {
        return new FieldBox(label, List.of(new TextInput(value, null).placeholder("Jane Doe")),
                required, message, Attributes.NONE, () -> {
                });
    }

    /// A real `form`, because the label column is a rule on **the form** — a form
    /// is where somebody decides that this form has one, and writing the class on
    /// every field would be the same decision repeated once per row.
    private static Widget form(String... classes) {
        return new Form(List.of(
                field("Name", true, "", "Jane"),
                field("Port", true, "Ports run from 1024 to 65535", "80"),
                actions()),
                null, null, id("form", classes));
    }

    /// The action row: a field with **no label**, which is what lines a Save
    /// button up with the controls rather than with the labels.
    private static Widget actions() {
        return new FieldBox("", List.of(new Button("Save").styled("primary")),
                false, "", id("save", "actions"), () -> {
                });
    }

    @Test
    @DisplayName("a label column, with the message under the control and Save beside it")
    void horizontal() {
        paint("field-horizontal", Theme.NORD_DARK, 360, 200,
                form("horizontal"));
    }

    @Test
    @DisplayName("stacked labels, which is the default")
    void stacked() {
        paint("field-stacked", Theme.NORD_DARK, 360, 220,
                form());
    }

    @Test
    @DisplayName("the same, on the light theme")
    void horizontalLight() {
        paint("field-horizontal-light", Theme.NORD_LIGHT, 360, 200,
                form("horizontal"));
    }

    @Test
    @DisplayName("a field is two boxes, and the message is in the second")
    void twoBoxes() {
        var parts = ((FieldBox) field("Port", true, "Too big", "80")).children();

        // The structure the layout depends on: a label column is a row and a
        // message below is a column, so a flat field can only be one of them.
        assertEquals(2, parts.size());
        assertTrue(parts.get(0) instanceof FieldLabel);
        assertTrue(parts.get(1) instanceof FieldBody);

        var body = ((FieldBody) parts.get(1)).children();
        assertTrue(body.get(0) instanceof TextInput, "the control comes first");
        assertTrue(body.get(body.size() - 1) instanceof FieldMessage,
                "and the message is under it, not beside it");
    }
}
