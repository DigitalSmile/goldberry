package dev.goldberry.example.model.forms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.bind.runtime.Models;
import dev.goldberry.widgets.form.Validator;
import dev.goldberry.widgets.form.form.FormController;
import dev.goldberry.widgets.markup.Named;

/// The Forms screen's fields: the port's rule, the code's two events, and what
/// the document may name.
@DisplayName("the forms slice")
class FormsModelTest {

    private final FormsModel model = new FormsModel();
    private final FormsModel.Actions actions = new FormsModel.Actions(model);

    @ParameterizedTest
    @ValueSource(strings = {"1024", "8080", "65535"})
    @DisplayName("a port a palantír answers on passes")
    void portsInRange(String port) {
        assertTrue(FormsModel.isPort(port));
    }

    @ParameterizedTest
    @ValueSource(strings = {"80", "1023", "65536", "eight", "80 80"})
    @DisplayName("anything else is refused")
    void portsOutOfRange(String port) {
        assertFalse(FormsModel.isPort(port));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("nothing typed yet is not an error, so the rule is quiet until a reader types")
    void nothingTypedPasses(String port) {
        assertTrue(FormsModel.isPort(port));
    }

    @Test
    @DisplayName("the code counts its boxes, and the second event says it is verifying")
    void theCodesTwoEvents() {
        var status = Models.observable(model, "app.code-status");

        actions.setCode("123");
        assertEquals("3 of 6", status.get());

        actions.codeComplete("123456");
        assertEquals("Verifying 123456…", status.get());

        actions.setCode("");
        assertEquals("Type or paste six digits", status.get());
    }

    @Test
    @DisplayName("the document names the form's controller and the port's rule")
    void namedObjects() {
        var named = model.register(Named.strict());

        assertInstanceOf(FormController.class, named.bound().get("app.signup-form"));
        assertInstanceOf(Validator.class, named.bound().get("app.port-rule"));
    }

    @Test
    @DisplayName("each field's action writes the path its field reads")
    void fieldsRoundTrip() {
        actions.setName("Samwise");
        actions.setPort("4242");
        actions.setBio("A gardener.");
        actions.setNotes("- [ ] Rope");
        actions.setSignupName("Merry");
        actions.setSignupPort("9000");

        assertEquals("Samwise", Models.observable(model, "app.name").get());
        assertEquals("4242", Models.observable(model, "app.port").get());
        assertEquals("A gardener.", Models.observable(model, "app.bio").get());
        assertEquals("- [ ] Rope", Models.observable(model, "app.notes").get());
        assertEquals("Merry", Models.observable(model, "app.signup-name").get());
        assertEquals("9000", Models.observable(model, "app.signup-port").get());
    }

    @Test
    @DisplayName("the gutter's sample has one hard line long enough to wrap")
    void theNotesWrap() {
        var notes = String.valueOf(Models.observable(model, "app.notes").get());
        var lines = notes.lines().toList();

        assertEquals("# Fellowship", lines.getFirst());
        assertEquals("- [x] Return the ring", lines.getLast(), "no trailing newline after the last task");
        assertTrue(lines.stream().anyMatch(line -> line.length() > 120), "one paragraph is a single long line");
    }
}
