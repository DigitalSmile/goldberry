package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
import java.util.ArrayList;
import java.util.List;

/// Everything the showcase *knows*. Values, and nothing that happens to them.
///
/// The state half of a view model. What changes these is [ActionRegistry], nested below
/// — the build rewrites a write to a `@Bind` field wherever it appears, so an
/// assignment in one class notifies exactly as one in the other would
/// ([ADR-0134](../../../../../../book/src/adr/0134-a-write-is-rewritten-wherever-it-is.md)).
///
/// ## Why a class and not a record
///
/// A record's components are final, and a bound value has to be assignable — that
/// is the whole mechanism. So the values are a class, and the *actions* are the
/// record, which is the half that genuinely holds nothing.
///
/// ## Why [ActionRegistry] is nested
///
/// So these fields can stay `private`. A nestmate reaches a private field and a
/// private method, so nesting the actions costs nothing in encapsulation — where
/// a sibling top-level class would have forced every field open to the package
/// ([ADR-0137](../../../../../../book/src/adr/0137-a-model-keeps-its-fields.md)).
///
/// Nesting is **scoping, not coupling**: this class holds no reference to
/// [ActionRegistry], mentions it in no signature, and would compile with it deleted. The
/// arrow still points one way.
///
/// ## Why here and not on the element
///
/// [io.github.digitalsmile.goldberry.widget.State] is for what the *UI* remembers
/// — a scroll offset, a caret, which tab is open — and it is right that those die
/// with the widget that owns them (ADR-0052). This is the other kind: the number
/// of clicks and the gain are what the application is *about*, they outlive any
/// particular screen, and a second screen showing the same gain would read this
/// object rather than a copy of it.
@Model
public final class ShowcaseModel {

    @Bind("app.clicks")
    private int clicks;

    @Bind("app.prose")
    private boolean showProse = true;

    @Bind("app.partly")
    private Checkbox.Value partly = Checkbox.Value.MIXED;

    /// A `Number` rather than a `double`, because that is genuinely what it
    /// holds: the initial value is a whole 40 and a slider reports fractions, and
    /// what a `text` widget prints is whichever it was last given. A `double`
    /// here would render the starting value as `40.0`.
    @Bind("app.gain")
    private Number gain = 40;

    /// A restyle rather than a repaint: every resolved style depends on the
    /// theme, so changing it invalidates the stylesheets and not just the pixels
    /// (ADR-0133).
    @Bind(value = "app.theme", restyle = true)
    private String themeName = "dark";

    /// The Forms screen's bound field, and the one that proves the round trip:
    /// the field reports every keystroke, the action below writes it here, and
    /// the `text` beside it reads the same path. A field that reset its caret on
    /// that round trip would be unusable, which is exactly what `TextInput`'s
    /// "has the value changed since the last build" test is there to prevent.
    @Bind("app.name")
    private String name = "";

    /// A field with a filter on it, so the screen shows one refusing a keystroke
    /// rather than only describing that it would.
    @Bind("app.port")
    private String port = "8080";

    /// The §4 form's two values. The status line is what the Save button writes,
    /// and is the smallest thing that shows a submission being **refused** — the
    /// interesting half of a form, and the half a screenshot of a happy one never
    /// shows.
    @Bind("app.signup-name")
    private String signupName = "";

    @Bind("app.signup-port")
    private String signupPort = "";

    @Bind("app.signup-status")
    private String signupStatus = "Nothing submitted yet";

    /// The `text-area`'s value, so the screen shows a multi-line control that a
    /// model can see — and one long enough to wrap, which is the half of it
    /// `text-input` cannot demonstrate.
    @Bind("app.bio")
    private String bio = "Yoga laid this out, HarfBuzz shaped it, and Blend2D drew "
            + "every glyph.\n\nPress Enter for a new line.";

    @Bind("app.status")
    private String status = "checking the environment…";

    /// The tabs, and which of them is showing. The strip reports what the user
    /// asked for and changes nothing itself — closing a tab removes it from here
    /// or it does not close (ADR-0107).
    ///
    /// **Assignment is what is observed**, which is why this is a `List` that
    /// gets replaced rather than one that gets edited. A list mutated in place
    /// changes nothing anybody can see (ADR-0109) — the same rule the weaver
    /// enforces by refusing to bind an array at all.
    @Bind("app.tabs")
    private List<String> tabs = List.of("Notes", "Log");

    @Bind("app.tab")
    private String tab = "Notes";

    /// Which gallery screen is showing — the tab strip across the top of the
    /// window (ADR-0110). `Ctrl+1`, a menu item and the strip itself are three
    /// ways to set one value rather than three copies of a selection.
    @Bind("app.screen")
    private String screen = "controls";

    /// §1.3's density preference. It moves every control's height, which is what
    /// "token-conformant apps adapt with zero code" means (ADR-0074) — and which
    /// is why it restyles rather than repaints.
    @Bind(value = "app.density", restyle = true)
    private Density density = Density.REGULAR;

    /// How many tabs have been added, so a new one gets a name nobody has used.
    ///
    /// Bound, and declared `repaint = false`: it is genuinely part of what this
    /// model knows, and nothing on screen shows it, so a change to it has no
    /// frame to ask for (ADR-0135). It is also why [ShowcaseModel.Actions] can be a
    /// record — this counter is state, and it lives with the rest of the state
    /// rather than in the thing that increments it.
    @Bind(value = "app.tabs-added", repaint = false)
    private int added;

    // --- what these values mean ---------------------------------------------
    //
    // Projections, not logic: each one is a question about the fields above with
    // exactly one answer, and moving them into `ShowcaseModel.Actions` would put a read
    // in a class named for writes.

    public Theme theme() {
        return "light".equals(themeName) ? Theme.NORD_LIGHT : Theme.NORD_DARK;
    }

    public Density density() {
        return density;
    }

    public boolean isProseShown() {
        return showProse;
    }

    public boolean hasClicks() {
        return clicks > 0;
    }

    /// Everything that *happens* to these values. One method per thing a control
    /// can ask for, and no state of its own.
    ///
    /// ```java
    /// var model = new ShowcaseModel();
    /// var actions = new ShowcaseModel.Actions(model);
    /// ```
    ///
    /// Each method assigns to a field of the model, and that assignment notifies:
    /// the build rewrites a write to a `@Bind` field wherever it appears, not
    /// only inside the class that declares it (ADR-0134). So `clicks++` reads
    /// here exactly as it read when it lived beside the field.
    ///
    /// **A record**, because it holds one thing and holds it immutably: one
    /// dependency, no state, and a constructor nobody writes. The counter behind
    /// "Untitled 3" lives on the values for the same reason — it is state, and
    /// this type has none.
    ///
    /// **Nested**, because a nestmate reaches a private field. A sibling
    /// top-level class would have forced every value above open to the package
    /// (ADR-0137).
    /// The objects the Forms document **names**: the handle that submits its form
    /// and the rule one of its fields applies.
    ///
    /// Not `@Bind` fields, and the binding machinery is what settled that — it
    /// refuses a `final` one with "a value that cannot change is not something to
    /// subscribe to", which is exactly what a controller and a validator are. They
    /// go in a `Named` registry instead (ADR-0170).
    private final io.github.digitalsmile.goldberry.widgets.form.form.FormController signup =
            new io.github.digitalsmile.goldberry.widgets.form.form.FormController();

    /// A rule the toolkit does not ship, which is the point of it being here: a
    /// port is a number **and** in range, and only the application knows the
    /// range.
    private final io.github.digitalsmile.goldberry.widgets.form.Validator<String> portRule =
            io.github.digitalsmile.goldberry.widgets.form.Validator.of(
                    value -> {
                        if (value == null || value.isEmpty()) {
                            return true;
                        }
                        try {
                            var port = Integer.parseInt(value);
                            return port >= 1024 && port <= 65535;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    },
                    "Ports run from 1024 to 65535");

    /// What the Forms document may name — see [io.github.digitalsmile.goldberry.widgets.Named].
    public io.github.digitalsmile.goldberry.widgets.Named named() {
        return io.github.digitalsmile.goldberry.widgets.Named.strict()
                .bind("app.signup-form", signup)
                .bind("app.port-rule", portRule);
    }

    @io.github.digitalsmile.goldberry.bind.Actions
    public record Actions(ShowcaseModel values) {

        // --- the gallery ---------------------------------------------------------

        /// Shows a screen. What the gallery's strip asks for and this decides.
        @Action("app.pick-screen")
        public void pickScreen(String name) {
            values.screen = name;
        }

        // --- the counter ---------------------------------------------------------

        @Action("app.click")
        public void click() {
            values.clicks++;
        }

        @Action("app.undo")
        public void undo() {
            values.clicks = Math.max(0, values.clicks - 1);
        }

        @Action("app.reset")
        public void reset() {
            values.clicks = 0;
        }

        // --- the controls --------------------------------------------------------

        /// The other half of ADR-0063's loop: the checkbox is handed the read-only
        /// half of `showProse` and cannot write it, so the tick moves only when this
        /// moves it. Delete this method and the control stops working — which is the
        /// behaviour, not a bug.
        @Action("app.toggle-prose")
        void toggleProse() {
            values.showProse = !values.showProse;
        }

        /// What the *switch* asks for, and the reason it is not [#toggleProse()]. A
        /// drag is a request for a **particular** state rather than for the other one
        /// — dragging right on a switch already on asks for on — so the value comes up
        /// with the event and this sets exactly it (ADR-0075).
        @Action("app.set-prose")
        void setProse(boolean value) {
            values.showProse = value;
        }

        /// `Checkbox.Value.toggled()` is what an application applies: from `MIXED`
        /// this goes to `CHECKED`, and a user can never get back to mixed by clicking
        /// — only the application can put it there.
        @Action("app.toggle-partly")
        void togglePartly() {
            values.partly = values.partly.toggled();
        }

        /// Already snapped and clamped by whichever control asked. The application
        /// does no arithmetic at all, which is the point (ADR-0079).
        @Action("app.set-gain")
        void setGain(double value) {
            values.gain = value;
        }

        /// What the Name field reports. Every keystroke arrives here and is
        /// written straight back to the model the field is bound to — the round
        /// trip a real form makes, and the one that would move the caret to the
        /// end on every letter if the field adopted its own echo.
        @Action("app.set-name")
        void setName(String value) {
            values.name = value;
        }

        @Action("app.set-port")
        void setPort(String value) {
            values.port = value;
        }

        @Action("app.set-bio")
        void setBio(String value) {
            values.bio = value;
        }

        @Action("app.set-signup-name")
        void setSignupName(String value) {
            values.signupName = value;
        }

        @Action("app.set-signup-port")
        void setSignupPort(String value) {
            values.signupPort = value;
        }

        /// What Save does, and the whole of what an application writes: the form
        /// validates itself and this is told whether it may proceed.
        ///
        /// The submit event carries **nothing**, which is right — `bind=` reads
        /// *from* this model, so the values are already here and an event
        /// carrying them would hand the application its own data back
        /// (ADR-0169).
        @Action("app.submit-signup")
        void submitSignup() {
            if (values.signup.submit()) {
                values.signupStatus = "Saved " + values.signupName + " on port "
                        + values.signupPort;
            } else {
                var count = values.signup.errors().size();
                values.signupStatus = count + (count == 1 ? " thing" : " things") + " to fix";
            }
        }

        public void setStatus(String value) {
            values.status = value;
        }

        // --- the theme -----------------------------------------------------------

        @Action("app.toggle-theme")
        public void toggleTheme() {
            pickTheme("light".equals(values.themeName) ? "dark" : "light");
        }

        /// One route for three controls — the radio group, the title bar's button and
        /// `Ctrl+T`. Which is the point of the value being bound: a theme changed from
        /// the keyboard moves the tick without the shortcut knowing a radio group
        /// exists.
        @Action("app.pick-theme")
        void pickTheme(String name) {
            values.themeName = name;
        }

        public void toggleDensity() {
            values.density = values.density == Density.REGULAR ? Density.COMPACT : Density.REGULAR;
        }

        // --- the tabs ------------------------------------------------------------

        /// Shows a tab. What the strip asks for and this decides, like every other
        /// value here.
        public void pickTab(String value) {
            values.tab = value;
        }

        /// Closes one — and picks a neighbour when it was the one being shown, because
        /// a strip whose selection has been removed shows nothing at all.
        public void closeTab(String value) {
            var current = new ArrayList<>(values.tabs);
            var index = current.indexOf(value);
            if (index < 0) {
                return;
            }
            current.remove(index);
            // A new list rather than a mutation: a subscriber is subscribed to the
            // *value*, and a list changed in place is the same value.
            values.tabs = List.copyOf(current);
            if (value.equals(values.tab)) {
                values.tab = current.isEmpty() ? null : current.get(Math.min(index, current.size() - 1));
            }
        }

        /// Adds one, and shows it — which is what every editor does with a new tab.
        public void newTab() {
            var name = "Untitled " + (++values.added);
            var current = new ArrayList<>(values.tabs);
            current.add(name);
            values.tabs = List.copyOf(current);
            values.tab = name;
        }
    }
}
