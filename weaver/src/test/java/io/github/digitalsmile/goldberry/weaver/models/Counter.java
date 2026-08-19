package io.github.digitalsmile.goldberry.weaver.models;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;
import io.github.digitalsmile.goldberry.bind.Property;

/// A model written the way an application writes one: plain fields, plain
/// methods, and not one mention of a binding.
///
/// Compiled unwoven — `:weaver` deliberately does not apply `goldberry.weave` to
/// itself — so a test weaves it and watches what changed.
@Model
public final class Counter {

    @Bind("app.clicks")
    private int clicks;
    @Bind("app.label")
    private String label = "idle";
    @Bind("app.on")
    private boolean on;
    @Bind("app.gain")
    private double gain = 1.0;

    /// The one value here a *rule* depends on rather than a widget, so changing
    /// it invalidates the stylesheets rather than the pixels.
    @Bind(value = "app.theme", restyle = true)
    private String theme = "dark";
    /// Nothing on screen shows this, so a change to it has no frame to ask for.
    @Bind(value = "app.ticks", repaint = false)
    private int ticks;

    @Bind("app.owned")
    private final Property<String> owned = Property.of("outside");

    /// Written through a lambda, which javac compiles into a synthetic method of
    /// this same class — so the rewrite has to reach it too.
    @Action("app.click")
    private void click() {
        Runnable bump = () -> clicks++;
        bump.run();
    }

    @Action("app.reset")
    void reset() {
        clicks = 0;
        label = "idle";
    }

    @Action("app.set-gain")
    private void setGain(double value) {
        gain = value;
    }

    @Action("app.set-clicks")
    private void setClicks(int value) {
        clicks = value;
    }

    @Action("app.set-on")
    private void setOn(boolean value) {
        on = value;
    }

    @Action("app.say")
    public void say(String text) {
        label = text;
    }

    @Action("app.tick")
    private void tick() {
        ticks++;
    }

    @Action("app.pick-theme")
    private void pickTheme(String name) {
        theme = name;
    }

    /// Returns something, which an action may do — the value is dropped.
    @Action("app.bump")
    private int bump() {
        clicks++;
        return clicks;
    }

    // Read straight off the field, to prove the field is still the cell.
    public int rawClicks() {
        return clicks;
    }

    public String rawLabel() {
        return label;
    }

    public Property<String> owned() {
        return owned;
    }
}
