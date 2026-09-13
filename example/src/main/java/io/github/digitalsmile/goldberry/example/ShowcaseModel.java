package io.github.digitalsmile.goldberry.example;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.example.ui.DocumentAssets;
import io.github.digitalsmile.goldberry.example.ui.HtmlSample;
import io.github.digitalsmile.goldberry.example.ui.MarkdownSample;
import io.github.digitalsmile.goldberry.markdown.Markdown;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;

/// Everything the showcase *knows*. Values, and nothing that happens to them.
///
/// The state half of a view model. What changes these is [Actions], nested below
/// — the build rewrites a write to a `@Bind` field wherever it appears, so an
/// assignment in one class notifies exactly as one in the other would
/// (ADR-0134).
///
/// ## Why the content is Middle-earth
///
/// Because a gallery whose every label reads "Item 1" is a gallery you cannot
/// read. Real prose wraps at awkward places, real names are of wildly different
/// lengths, and a table of six companions with a `Kindred` column shows a
/// sortable header doing something a column of `Row 1`… cannot
/// (ADR-0222).
///
/// ## Why a class and not a record
///
/// A record's components are final, and a bound value has to be assignable — that
/// is the whole mechanism. So the values are a class, and the *actions* are the
/// record, which is the half that genuinely holds nothing.
///
/// ## Why [Actions] is nested
///
/// So these fields can stay `private`. A nestmate reaches a private field and a
/// private method, so nesting the actions costs nothing in encapsulation — where
/// a sibling top-level class would have forced every field open to the package
/// (ADR-0137).
///
/// Nesting is **scoping, not coupling**: this class holds no reference to
/// [Actions], mentions it in no signature, and would compile with it deleted. The
/// arrow still points one way.
///
/// ## Why here and not on the element
///
/// [io.github.digitalsmile.goldberry.widget.State] is for what the *UI* remembers
/// — a scroll offset, a caret, which tab is open — and it is right that those die
/// with the widget that owns them (ADR-0052). This is the other kind: the leagues
/// walked and the provisions left are what the application is *about*, they
/// outlive any particular screen, and a second screen showing the same number
/// would read this object rather than a copy of it.
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image (ADR-0125, ADR-0155). Error Prone sees a private field with
// no reader in this compilation unit and is right about what it can see; the
// reader is generated. This is the one annotation in the project that means
// "assume the build wired it up".
@SuppressWarnings("UnusedVariable")
public final class ShowcaseModel {

    /// Leagues walked from Bag End. The counter, in the one unit a hobbit
    /// measures anything in.
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

    /// The same fact as [#themeName], spelled the way a **switch** can read it.
    ///
    /// Two fields and one writer. A `radio-group`, a `segmented` and a `select`
    /// all pick from a list, so their value is a *name*; the bar's `toggle` is a
    /// two-state control and a switch's value is a `boolean` by definition — it
    /// reads `source.get() instanceof Boolean` and falls back to its own flag
    /// otherwise, so binding it to `"light"` would give a switch that never
    /// moves.
    ///
    /// The two can only disagree inside [Actions#pickTheme], which is four lines
    /// long and is the single route every theme control in the window goes
    /// through — the strip, the switch, the menu, the tray and `Ctrl+T`.
    @Bind(value = "app.light", restyle = true)
    private boolean light;

    /// The Red Book's own field, and the one that proves the round trip: the
    /// field reports every keystroke, the action below writes it here, and the
    /// `text` beside it reads the same path. A field that reset its caret on
    /// that round trip would be unusable, which is exactly what `TextInput`'s
    /// "has the value changed since the last build" test is there to prevent.
    @Bind("app.name")
    private String name = "";

    /// A field with a filter on it, so the screen shows one refusing a keystroke
    /// rather than only describing that it would. A palantír answers on a port
    /// like anything else that talks over a distance.
    @Bind("app.port")
    private String port = "8080";

    /// The enlistment form's two values. The status line is what Enlist writes,
    /// and is the smallest thing that shows a submission being **refused** — the
    /// interesting half of a form, and the half a screenshot of a happy one never
    /// shows.
    @Bind("app.signup-name")
    private String signupName = "";

    @Bind("app.signup-port")
    private String signupPort = "";

    @Bind("app.signup-status")
    private String signupStatus = "Nobody has enlisted yet";

    /// The departure date, and what the picker made of it.
    ///
    /// A **`LocalDate`**, which is what §4 gives this control and what a model
    /// that has parsed its values holds. The picker formats it into the field
    /// rather than stringifying it, so the screen shows the application's own
    /// spelling of a date it owns.
    @Bind("trip.date")
    private LocalDate tripDate = LocalDate.of(2026, 9, 14);

    @Bind("trip.status")
    private String tripStatus = "Type a date, or press the chevron";

    /// When the departure leaves, and what the wheels made of it.
    ///
    /// A **`LocalTime`**, for `trip.date`'s reason: §4 gives the control a typed
    /// value and a model that has parsed its own is what a picker formats from.
    @Bind("trip.time")
    private LocalTime tripTime = LocalTime.of(9, 30);

    @Bind("trip.time-status")
    private String tripTimeStatus = "Type it, or turn the wheels";

    /// A colour, held as one — §4 gives `color-picker` a `CssColor` value, which
    /// is an `0xAARRGGBB` int, and a model that keeps colours as colours is what
    /// the picker formats its hex from.
    @Bind("paint.colour")
    private Integer paintColour = 0xFF88C0D0;

    @Bind("paint.status")
    private String paintStatus = "Nord frost";

    /// The one-time code, and what happened to it.
    ///
    /// Two values rather than one, because `code-input` is the one field in §4
    /// that raises **two** events: `change` on every box, and `complete` once,
    /// when the last one fills. The status line is what the second writes, and it
    /// is the only way a screenshot can show the difference between them.
    @Bind("app.code")
    private String code = "";

    @Bind("app.code-status")
    private String codeStatus = "Type or paste six digits";

    /// The `text-area`'s value, so the screen shows a multi-line control that a
    /// model can see — and one long enough to wrap, which is the half of it
    /// `text-input` cannot demonstrate.
    @Bind("app.bio")
    private String bio = "We came down out of the pass at dusk and found the road still "
            + "under snow.\n\nPress Enter for a new line.";

    /// The Markdown screen's document, which is **one property read twice**: the
    /// editor on the left writes it through an action and the preview on the right
    /// follows it with `bind=`.
    ///
    /// That is the whole of "live". Nothing in this application watches the editor,
    /// diffs the text or schedules a re-render — the element holding the
    /// `markdown-view` is subscribed to this property, a keystroke marks it for
    /// rebuild, and the next frame is the parsed document (ADR-0296).
    ///
    /// Loaded from `markdown-sample.md` beside the screens' documents rather than
    /// written as a text block here: it is prose with backslashes, backticks and
    /// trailing spaces in it, and every one of those is something a Java string
    /// literal would eat.
    @Bind("md.source")
    private String markdownSource = MarkdownSample.text();

    /// The HTML screen's page, which is the same arrangement one tab along: the
    /// editor writes it, `html-view` reads it, and nothing in this application
    /// connects the two (ADR-0296, ADR-0298).
    ///
    /// Loaded from `html-sample.html` for the same reason the Markdown one is loaded
    /// from a file, and one more: it is markup, so it is quotation marks all the way
    /// down.
    @Bind("html.source")
    private String htmlSource = HtmlSample.text();

    /// What the Markdown screen's last pressed link or ticked box reported.
    ///
    /// The same line the HTML screen has, and it is the honest demonstration of
    /// ADR-0300: the toolkit hands over a destination or an ordinal and stops.
    @Bind("md.followed")
    private String markdownFollowed = "Press a link, or tick a box — both are edits this application makes.";

    /// What the last link a reader pressed handed over.
    ///
    /// The showcase's whole answer to "what does following a link do", and it is
    /// deliberately the smallest honest one: the toolkit hands over an `href` and
    /// stops, because whether a link may be followed, what a relative path is relative
    /// to and what opening one costs are the application's (ADR-0291's division,
    /// ADR-0298's restatement). This application prints it.
    @Bind("html.followed")
    private String htmlFollowed = "Press a link in the page — nothing here opens a browser.";

    /// What the bar says about how this window came up. Written once, from a
    /// background job's continuation — see [io.github.digitalsmile.goldberry.example.Showcase].
    @Bind("app.status")
    private String status = "reading the map…";

    /// How long this process took to get a window on screen, as the bar prints
    /// it. Separate from [#status] because they answer different questions and
    /// arrive at different times.
    @Bind("app.startup")
    private String startup = "…";

    /// Whether the frame-rate readout is up.
    ///
    /// On the model and not in the window, although the overlay is the window's,
    /// because the **menu** has to draw a tick beside it — and a menu row's
    /// `checked` is a constant resolved when the bar is built. A window that kept
    /// this to itself would give a bar that had to be told to rebuild by whoever
    /// toggled the HUD, which is exactly the arrangement bound values exist to
    /// replace (ADR-0063, ADR-0135).
    @Bind("app.hud")
    private boolean hud;

    /// The chapters open in the Navigation screen's strip, and which of them is
    /// showing. The strip reports what the user asked for and changes nothing
    /// itself — closing a chapter removes it from here or it does not close
    /// (ADR-0107).
    ///
    /// **Assignment is what is observed**, which is why this is a `List` that
    /// gets replaced rather than one that gets edited. A list mutated in place
    /// changes nothing anybody can see (ADR-0109) — the same rule the weaver
    /// enforces by refusing to bind an array at all.
    @Bind("app.tabs")
    private List<String> tabs = List.of("Rivendell", "Moria");

    @Bind("app.tab")
    private String tab = "Rivendell";

    /// Which gallery screen is showing — the tab strip under the bar (ADR-0110).
    /// `Ctrl+1`, a menu item and the strip itself are three ways to set one value
    /// rather than three copies of a selection.
    @Bind("app.screen")
    private String screen = "basic";

    /// §1.3's density preference. It moves every control's height, which is what
    /// "token-conformant apps adapt with zero code" means (ADR-0074) — and which
    /// is why it restyles rather than repaints.
    @Bind(value = "app.density", restyle = true)
    private Density density = Density.REGULAR;

    /// How many chapters have been opened, so a new one gets a name nobody has
    /// used.
    ///
    /// Bound, and declared `repaint = false`: it is genuinely part of what this
    /// model knows, and nothing on screen shows it, so a change to it has no
    /// frame to ask for (ADR-0135). It is also why [Actions] can be a record —
    /// this counter is state, and it lives with the rest of the state rather than
    /// in the thing that increments it.
    @Bind(value = "app.tabs-added", repaint = false)
    private int added;

    /// The places a new chapter is named after, in order. Ten of them, which is
    /// more than anybody will open — and a name that repeats would be two tabs
    /// with one identity, which a strip keyed by name cannot tell apart.
    private static final List<String> STAGES = List.of(
            "Bree",
            "Weathertop",
            "Lothlórien",
            "Anduin",
            "Amon Hen",
            "Fangorn",
            "Edoras",
            "Helm's Deep",
            "Osgiliath",
            "Cirith Ungol");

    // --- what these values mean ---------------------------------------------
    //
    // Projections, not logic: each one is a question about the fields above with
    // exactly one answer, and moving them into `Actions` would put a read in a
    // class named for writes.

    public Theme theme() {
        return light ? Theme.NORD_LIGHT : Theme.NORD_DARK;
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

    public boolean isHudShown() {
        return hud;
    }

    public int clicks() {
        return clicks;
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
    /// "Bree" lives on the values for the same reason — it is state, and this
    /// type has none.
    ///
    /// **Nested**, because a nestmate reaches a private field. A sibling
    /// top-level class would have forced every value above open to the package
    /// (ADR-0137).
    @io.github.digitalsmile.goldberry.bind.runtime.Actions
    public record Actions(ShowcaseModel values) {

        // --- the gallery -----------------------------------------------------

        /// Shows a screen. What the gallery's strip asks for and this decides.
        @Action("app.pick-screen")
        public void pickScreen(String name) {
            values.screen = name;
        }

        // --- the road --------------------------------------------------------

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

        // --- the controls ----------------------------------------------------

        /// The other half of ADR-0063's loop: the checkbox is handed the read-only
        /// half of `showProse` and cannot write it, so the tick moves only when this
        /// moves it. Delete this method and the control stops working — which is the
        /// behaviour, not a bug.
        @Action("app.toggle-prose")
        public void toggleProse() {
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

        /// What the Red Book's field reports. Every keystroke arrives here and is
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

        /// Every keystroke in the Markdown editor.
        ///
        /// The preview is not mentioned here and does not need to be: it is bound to
        /// the field this writes, so the assignment *is* the notification (ADR-0296).
        @Action("md.set-source")
        public void setMarkdownSource(String value) {
            values.markdownSource = value;
        }

        /// A link in the rendered Markdown, pressed.
        @Action("md.follow")
        public void followMarkdownLink(String href) {
            values.markdownFollowed = "Followed: " + href;
        }

        /// A `[[wiki link]]`, pressed — a **target** rather than an href, which is why
        /// it is a second action: what `[[Meeting]]` names is this application's
        /// business and not a URL (ADR-0295).
        @Action("md.open-wiki")
        public void openWikiLink(String target) {
            values.markdownFollowed = "Wiki link: " + target;
        }

        /// A task box, ticked.
        ///
        /// **The round trip in three lines**, and it is the whole of ADR-0300: the
        /// view reports *which* box — the nth task in the document — `toggleTask`
        /// rewrites that one character of the source, and the preview re-parses
        /// because it is bound to the property this assigns. The editor on the left
        /// shows the edit too, for the same reason.
        @Action("md.toggle-task")
        public void toggleTask(String index) {
            var task = Integer.parseInt(index.trim());
            values.markdownSource = Markdown.toggleTask(values.markdownSource, task);
            values.markdownFollowed = "Ticked task " + task + " — the source on the left changed with it.";
        }

        /// Every keystroke in the HTML editor. The preview is not mentioned here and
        /// does not need to be, for the reason above it.
        @Action("html.set-source")
        public void setHtmlSource(String value) {
            values.htmlSource = value;
        }

        /// What an anchor in the page hands over when it is pressed.
        ///
        /// A **valued** action, because §9's valued shape is how a control reports
        /// *what* it should become and a link reports where it points. This is the
        /// application deciding what following a link means; deciding to print it is a
        /// showcase's answer, and a real one would navigate, open the desktop's browser
        /// or refuse an `href` it does not trust.
        @Action("html.follow")
        public void followLink(String href) {
            values.htmlFollowed = "Followed: " + href;
        }

        /// What the picker committed — either from the grid or from the field,
        /// which is the point: §4 makes the typed field the source of truth and
        /// the grid writes into it, so there is one handler and not two.
        ///
        /// A **`String`**, and it has to be: §9's valued actions cross as text,
        /// so a document is handed the *formatted* date and Java is handed the
        /// `DateSelection`. Parsing it back here is the application doing what §4
        /// says it does — "the toolkit does not invent a date syntax", so the
        /// application that chose the format is the one that can read it.
        @Action("trip.set-date")
        void setTripDate(String value) {
            if (value.isBlank()) {
                values.tripDate = null;
                values.tripStatus = "Nothing chosen";
                return;
            }
            var date = LocalDate.parse(value, DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT));
            values.tripDate = date;
            values.tripStatus = "Leaving on " + date;
        }

        /// What the wheels or the field committed. A `String` for
        /// `trip.set-date`'s reason: §9's valued actions cross as text, so the
        /// application that chose the format is the one that reads it back.
        @Action("trip.set-time")
        void setTripTime(String value) {
            if (value.isBlank()) {
                values.tripTime = null;
                values.tripTimeStatus = "No time chosen";
                return;
            }
            var time = LocalTime.parse(value, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
            values.tripTime = time;
            values.tripTimeStatus = "Boarding at " + time;
        }

        /// What the plane, the ramps, a preset or the hex field committed — one
        /// handler, because §4 makes the hex field the source of truth and every
        /// other part writes into it.
        ///
        /// A `String` for `trip.set-date`'s reason: §9's valued actions cross as
        /// text. Here the text is the value's own spelling rather than a
        /// formatting choice, so parsing it back is exact.
        @Action("paint.set-colour")
        void setPaintColour(String hex) {
            var argb = CssColor.parse(hex);
            if (argb == null) {
                return;
            }
            values.paintColour = argb;
            values.paintStatus = hex;
        }

        /// Every box, as it fills. The round trip a real form makes.
        @Action("app.set-code")
        void setCode(String value) {
            values.code = value;
            values.codeStatus = value.isEmpty() ? "Type or paste six digits" : value.length() + " of 6";
        }

        /// And the second event, once, on the box that filled the code — §4's
        /// "what lets a form submit without a button". Nothing here presses
        /// anything, because a showcase that verified a code would have to invent
        /// one that was right.
        @Action("app.code-complete")
        void codeComplete(String value) {
            values.codeStatus = "Verifying " + value + "…";
        }

        @Action("app.set-signup-name")
        void setSignupName(String value) {
            values.signupName = value;
        }

        @Action("app.set-signup-port")
        void setSignupPort(String value) {
            values.signupPort = value;
        }

        /// What Enlist does, and the whole of what an application writes: the form
        /// validates itself and this is told whether it may proceed.
        ///
        /// The submit event carries **nothing**, which is right — `bind=` reads
        /// *from* this model, so the values are already here and an event
        /// carrying them would hand the application its own data back
        /// (ADR-0169).
        @Action("app.submit-signup")
        void submitSignup() {
            if (values.signup.submit()) {
                values.signupStatus = values.signupName + " rides for Rivendell, hailed on " + values.signupPort;
            } else {
                var count = values.signup.errors().size();
                values.signupStatus = count + (count == 1 ? " thing" : " things") + " to put right";
            }
        }

        public void setStatus(String value) {
            values.status = value;
        }

        public void setStartup(String value) {
            values.startup = value;
        }

        /// What the window reports after it has floated a HUD or taken one away.
        /// Not an `@Action`: no document names it, and the only caller is the
        /// application itself.
        public void setHud(boolean value) {
            values.hud = value;
        }

        // --- the theme -------------------------------------------------------

        @Action("app.toggle-theme")
        public void toggleTheme() {
            pickTheme(values.light ? "dark" : "light");
        }

        /// What the **switch** in the bar asks for, and the reason it is not
        /// [#toggleTheme()]: a switch reports the state it was dragged to rather
        /// than "the other one", exactly as `app.set-prose` does (ADR-0075).
        @Action("app.set-light")
        public void setLight(boolean value) {
            pickTheme(value ? "light" : "dark");
        }

        /// One route for every theme control there is — the radios, the segmented
        /// bar, the select, the bar's switch, the File menu, the tray and
        /// `Ctrl+T`. Which is the point of the value being bound: a theme changed
        /// from the keyboard moves the switch without the shortcut knowing a bar
        /// exists.
        ///
        /// The two spellings are written here and nowhere else, which is what
        /// keeps them from disagreeing.
        @Action("app.pick-theme")
        public void pickTheme(String name) {
            values.themeName = name;
            values.light = "light".equals(name);
        }

        @Action("app.toggle-density")
        public void toggleDensity() {
            values.density = values.density == Density.REGULAR ? Density.COMPACT : Density.REGULAR;
        }

        // --- the chapters ----------------------------------------------------

        /// Shows a chapter. What the strip asks for and this decides, like every
        /// other value here.
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

        /// Opens one, and shows it — which is what every editor does with a new
        /// tab. The name is the next place on the road, so a strip of chapters
        /// reads as a journey rather than as `Untitled 3`.
        public void newTab() {
            var name = STAGES.get(values.added++ % STAGES.size());
            var current = new ArrayList<>(values.tabs);
            // A repeat would be two tabs with one identity, and a strip keyed by
            // name cannot tell those apart -- so the road is walked twice rather
            // than the same stage being opened twice.
            while (current.contains(name)) {
                name = name + " again";
            }
            current.add(name);
            values.tabs = List.copyOf(current);
            values.tab = name;
        }
    }

    // --- what the documents name --------------------------------------------

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
                    "A palantír answers between 1024 and 65535");

    /// What the Forms document may name — see
    /// [io.github.digitalsmile.goldberry.widgets.markup.Named].
    public io.github.digitalsmile.goldberry.widgets.markup.Named named() {
        return io.github.digitalsmile.goldberry.widgets.markup.Named.strict()
                .bind("app.signup-form", signup)
                .bind("app.port-rule", portRule)
                // Where both document screens' pictures come from. A **named object**
                // rather than an action or a binding, because an image source is
                // neither a method nor a value that changes -- which is the third
                // registry's whole job (ADR-0130, ADR-0300).
                .bind("app.assets", assets);
    }

    /// The one picture this application ships, for whichever document names it.
    private final DocumentAssets assets = new DocumentAssets();
}
