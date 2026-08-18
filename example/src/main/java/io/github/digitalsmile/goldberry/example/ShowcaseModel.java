package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Registry;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;

/// Everything the showcase *knows*, and nothing about how it looks.
///
/// The view model, in the shape a real application has one: a set of
/// [Property] values and the methods that change them, owned by the
/// [io.github.digitalsmile.goldberry.Application] and handed to the widgets that
/// read it. No widget, no window and no layout appears in this file.
///
/// ## Why here and not on the element
///
/// [io.github.digitalsmile.goldberry.widget.State] is for what the *UI* remembers
/// — a scroll offset, a caret, which tab is open — and it is right that those die
/// with the widget that owns them (ADR-0052). This is the other kind: the number
/// of clicks and the gain are what the application is *about*, they outlive any
/// particular screen, and a second screen showing the same gain would read this
/// object rather than a copy of it.
///
/// ## Why it registers itself
///
/// [#bindings()] and [#actions()] are the two registries §9's markup resolves
/// names against, and building them here is what lets a `.kdl` document say
/// `bind="app.gain"` and `change="gain.set"` without the document knowing what a
/// `Property` is. A markup file that named something this class does not register
/// fails at inflation with the position in the file, because both registries are
/// [Bindings#strict()] ([ADR-0062](../../../../../../book/src/adr/0062-bind-is-a-path-and-nothing-else.md)).
@Registry
public final class ShowcaseModel {

    @Bind("app.clicks")
    private final Property<Integer> clicks = Property.of(0);
    @Bind("app.prose")
    private final Property<Boolean> showProse = Property.of(true);
    @Bind("app.partly")
    private final Property<Checkbox.Value> partly = Property.of(Checkbox.Value.MIXED);
    @Bind("app.gain")
    private final Property<Number> gain = Property.of(40);
    @Bind("app.theme")
    private final Property<String> themeName = Property.of("dark");
    @Bind("app.status")
    private final Property<String> status = Property.of("checking the environment…");

    /// §1.3's density preference — a plain field, because nothing binds to it. No
    /// control shows which density is on, the way the theme radios show the
    /// theme; it moves every control's height and is named by no widget, which is
    /// what "token-conformant apps adapt with zero code" means (ADR-0074).
    private Density density = Density.REGULAR;

    /// Called after anything here changes, so the application can ask for a frame
    /// — and, for the two that move a stylesheet, for a restyle.
    private Runnable onChanged = () -> { };
    private Runnable onRestyle = () -> { };

    void onChanged(Runnable handler) {
        this.onChanged = handler;
    }

    void onRestyle(Runnable handler) {
        this.onRestyle = handler;
    }

    // --- what the widgets read ----------------------------------------------

    public Observable<Integer> clicks() {
        return clicks;
    }

    public Observable<Boolean> showProse() {
        return showProse;
    }

    public Observable<Checkbox.Value> partly() {
        return partly;
    }

    public Observable<Number> gain() {
        return gain;
    }

    public Observable<String> themeName() {
        return themeName;
    }

    public Observable<String> status() {
        return status;
    }

    public Theme theme() {
        return "light".equals(themeName.get()) ? Theme.NORD_LIGHT : Theme.NORD_DARK;
    }

    public Density density() {
        return density;
    }

    public boolean isProseShown() {
        return Boolean.TRUE.equals(showProse.get());
    }

    public boolean hasClicks() {
        return clicks.get() > 0;
    }

    // --- what the controls ask for -------------------------------------------

    @Action("app.click")
    public void click() {
        changed(() -> clicks.set(clicks.get() + 1));
    }

    @Action("app.undo")
    public void undo() {
        changed(() -> clicks.set(Math.max(0, clicks.get() - 1)));
    }

    @Action("app.reset")
    public void reset() {
        changed(() -> clicks.set(0));
    }

    /// The other half of ADR-0063's loop: the checkbox is handed the read-only
    /// half of `showProse` and cannot write it, so the tick moves only when this
    /// moves it. Set this aside and the control stops working — which is the
    /// behaviour, not a bug.
    @Action("app.toggle-prose")
    private void toggleProse() {
        changed(() -> showProse.set(!isProseShown()));
    }

    /// What the *switch* asks for, and the reason it is not [#toggleProse()]. A
    /// drag is a request for a **particular** state rather than for the other one
    /// — dragging right on a switch already on asks for on — so the value comes
    /// up with the event and this sets exactly it (ADR-0075).
    @Action("app.set-prose")
    private void setProse(boolean value) {
        changed(() -> showProse.set(value));
    }

    /// [Checkbox.Value#toggled()] is what an application applies: from `MIXED`
    /// this goes to `CHECKED`, and a user can never get back to mixed by clicking
    /// — only the application can put it there.
    @Action("app.toggle-partly")
    private void togglePartly() {
        changed(() -> partly.set(partly.get().toggled()));
    }

    /// Already snapped and clamped by whichever control asked. The application
    /// does no arithmetic at all, which is the point (ADR-0079).
    @Action("app.set-gain")
    private void setGain(double value) {
        changed(() -> gain.set(value));
    }

    public void setStatus(String value) {
        changed(() -> status.set(value));
    }

    @Action("app.toggle-theme")
    public void toggleTheme() {
        pickTheme("light".equals(themeName.get()) ? "dark" : "light");
    }

    /// One route for three controls — the radio group, the title bar's button and
    /// `Ctrl+T` — which is the point of the value being a property: a theme
    /// changed from the keyboard moves the tick without the shortcut knowing a
    /// radio group exists.
    @Action("app.pick-theme")
    private void pickTheme(String name) {
        themeName.set(name);
        onRestyle.run();
    }

    public void toggleDensity() {
        density = density == Density.REGULAR ? Density.COMPACT : Density.REGULAR;
        onRestyle.run();
    }

    private void changed(Runnable mutation) {
        mutation.run();
        onChanged.run();
    }

    // --- what markup resolves against ----------------------------------------
    //
    // Nothing here. `ShowcaseModelRegistry.bindings(this)` and `.actions(this)`
    // are **generated** from the annotations above, which is the same explicit
    // registration §9 requires with the copying removed -- and a typo in a path
    // is now a compile error rather than a control that renders perfectly and
    // never moves (ADR-0096).
}
