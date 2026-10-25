package dev.goldberry.example.model.forms;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;
import dev.goldberry.widgets.form.Validator;
import dev.goldberry.widgets.form.form.FormController;
import dev.goldberry.widgets.markup.Named;

/// What the Forms screen's fields hold: the Red Book's name, the port, the
/// enlistment form, the one-time code and the two text areas — and the two
/// objects its document names, the form's controller and the port's rule.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image. The reader is generated.
@SuppressWarnings("UnusedVariable")
public final class FormsModel {

    /// The Red Book's own field, and the one that proves the round trip: the
    /// field reports every keystroke, the action below writes it here, and the
    /// `text` beside it reads the same path. A field that reset its caret on
    /// that round trip would be unusable, which is exactly what `TextInput`'s
    /// "has the value changed since the last build" test is there to prevent.
    @Bind("app.name")
    @SuppressWarnings("unused")
    private String name = "";

    /// A field with a filter on it, so the screen shows one refusing a keystroke
    /// rather than only describing that it would. A palantír answers on a port
    /// like anything else that talks over a distance.
    @Bind("app.port")
    @SuppressWarnings("unused")
    private String port = "8080";

    /// The enlistment form's two values. The status line is what Enlist writes,
    /// and is the smallest thing that shows a submission being **refused** — the
    /// interesting half of a form, and the half a screenshot of a happy one never
    /// shows.
    @Bind("app.signup-name")
    @SuppressWarnings("unused")
    private String signupName = "";

    @Bind("app.signup-port")
    @SuppressWarnings("unused")
    private String signupPort = "";

    @Bind("app.signup-status")
    @SuppressWarnings("unused")
    private String signupStatus = "Nobody has enlisted yet";

    /// The one-time code, and what happened to it.
    ///
    /// Two values rather than one, because `code-input` is the one field
    /// that raises **two** events: `change` on every box, and `complete` once,
    /// when the last one fills. The status line is what the second writes, and it
    /// is the only way a screenshot can show the difference between them.
    @Bind("app.code")
    @SuppressWarnings("unused")
    private String code = "";

    @Bind("app.code-status")
    @SuppressWarnings("unused")
    private String codeStatus = "Type or paste six digits";

    /// The `text-area`'s value, so the screen shows a multi-line control that a
    /// model can see — and one long enough to wrap, which is the half of it
    /// `text-input` cannot demonstrate.
    @Bind("app.bio")
    @SuppressWarnings("unused")
    private String bio = "We came down out of the pass at dusk and found the road still "
            + "under snow.\n\nPress Enter for a new line.";

    /// The gutter card's document — a `text-area` with line numbers, and something
    /// in it worth numbering.
    ///
    /// Written so that **one hard line wraps**, which is the whole of what the
    /// gutter is about: the numbers count lines somebody typed and sit at the y
    /// the wrap put them at, so the second paragraph takes one number and more
    /// than one line's height. A document of short lines would show a column of
    /// numbers and prove nothing.
    @Bind("app.notes")
    @SuppressWarnings("unused")
    private String notes = """
            # Fellowship

            The road goes ever on and on, down from the door where it began, and now far \
            ahead the road has gone, and I must follow if I can.

            - [ ] Pack the rope
            - [x] Return the ring""";

    /// The handle that submits the enlistment form, which the document names.
    ///
    /// Not a `@Bind` field, and the binding machinery is what settled that — it
    /// refuses a `final` one with "a value that cannot change is not something to
    /// subscribe to", which is exactly what a controller and a validator are. They
    /// go in a [Named] registry instead.
    private final FormController signup = new FormController();

    /// A rule the toolkit does not ship, which is the point of it being here: a
    /// port is a number **and** in range, and only the application knows the
    /// range.
    private final Validator<String> portRule =
            Validator.of(FormsModel::isPort, "A palantír answers between 1024 and 65535");

    /// Whether `value` is a port a palantír answers on, or nothing typed yet.
    static boolean isPort(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        try {
            var port = Integer.parseInt(value);
            return port >= 1024 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /// Registers what the Forms document names on `named` — see
    /// [dev.goldberry.widgets.markup.Named] — and answers it.
    public Named register(Named named) {
        return named.bind("app.signup-form", signup).bind("app.port-rule", portRule);
    }

    /// Everything that *happens* to the Forms screen's values.
    ///
    /// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
    @dev.goldberry.bind.runtime.Actions
    public record Actions(FormsModel values) {

        /// What the Red Book's field reports. Every keystroke arrives here and is
        /// written straight back to the model the field is bound to — the round
        /// trip a real form makes, and the one that would move the caret to the
        /// end on every letter if the field adopted its own echo.
        @Action("app.set-name")
        @SuppressWarnings("unused")
        void setName(String value) {
            values.name = value;
        }

        @Action("app.set-port")
        @SuppressWarnings("unused")
        void setPort(String value) {
            values.port = value;
        }

        @Action("app.set-bio")
        @SuppressWarnings("unused")
        void setBio(String value) {
            values.bio = value;
        }

        @Action("app.set-notes")
        @SuppressWarnings("unused")
        void setNotes(String value) {
            values.notes = value;
        }

        /// Every box, as it fills. The round trip a real form makes.
        @Action("app.set-code")
        @SuppressWarnings("unused")
        void setCode(String value) {
            values.code = value;
            values.codeStatus = value.isEmpty() ? "Type or paste six digits" : value.length() + " of 6";
        }

        /// And the second event, once, on the box that filled the code — what
        /// lets a form submit without a button. Nothing here presses
        /// anything, because a showcase that verified a code would have to invent
        /// one that was right.
        @Action("app.code-complete")
        @SuppressWarnings("unused")
        void codeComplete(String value) {
            values.codeStatus = "Verifying " + value + "…";
        }

        @Action("app.set-signup-name")
        @SuppressWarnings("unused")
        void setSignupName(String value) {
            values.signupName = value;
        }

        @Action("app.set-signup-port")
        @SuppressWarnings("unused")
        void setSignupPort(String value) {
            values.signupPort = value;
        }

        /// What Enlist does, and the whole of what an application writes: the form
        /// validates itself and this is told whether it may proceed.
        ///
        /// The submit event carries **nothing**, which is right — `bind=` reads
        /// *from* this model, so the values are already here and an event
        /// carrying them would hand the application its own data back.
        @Action("app.submit-signup")
        @SuppressWarnings("unused")
        void submitSignup() {
            if (values.signup.submit()) {
                values.signupStatus = values.signupName + " rides for Rivendell, hailed on " + values.signupPort;
            } else {
                var count = values.signup.errors().size();
                values.signupStatus = count + (count == 1 ? " thing" : " things") + " to put right";
            }
        }
    }
}
