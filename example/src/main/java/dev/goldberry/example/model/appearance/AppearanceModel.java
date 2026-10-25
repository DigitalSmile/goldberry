package dev.goldberry.example.model.appearance;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;
import dev.goldberry.css.Theme;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.Scrollbars;

/// How the whole window is drawn: the light, the density and the scroll bars.
///
/// Every value here is a stylesheet, so every one of them is declared
/// `restyle = true`: a change invalidates the sheets and not just the pixels.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image. The reader is generated.
@SuppressWarnings("UnusedVariable")
public final class AppearanceModel {

    /// A restyle rather than a repaint: every resolved style depends on the
    /// theme, so changing it invalidates the stylesheets and not just the pixels.
    @Bind(value = "app.theme", restyle = true)
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    private boolean light;

    /// The density preference. It moves every control's height through the
    /// design tokens alone, with no code in the application — and that is why
    /// it restyles rather than repaints.
    @Bind(value = "app.density", restyle = true)
    @SuppressWarnings("unused")
    private Density density = Density.REGULAR;

    /// Whether scroll bars are always shown, which is density's shape: a token
    /// stylesheet, so it restyles.
    @Bind(value = "app.scrollbars", restyle = true)
    @SuppressWarnings("unused")
    private Scrollbars scrollbars = Scrollbars.OVERLAY;

    public Theme theme() {
        return light ? Theme.NORD_LIGHT : Theme.NORD_DARK;
    }

    public Density density() {
        return density;
    }

    public Scrollbars scrollbars() {
        return scrollbars;
    }

    /// Everything that *happens* to how the window is drawn.
    ///
    /// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
    @dev.goldberry.bind.runtime.Actions
    public record Actions(AppearanceModel values) {

        @Action("app.toggle-theme")
        @SuppressWarnings("unused")
        public void toggleTheme() {
            pickTheme(values.light ? "dark" : "light");
        }

        /// What the **switch** in the bar asks for, and the reason it is not
        /// [#toggleTheme()]: a switch reports the state it was dragged to rather
        /// than "the other one", exactly as `app.set-prose` does.
        @Action("app.set-light")
        @SuppressWarnings("unused")
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
        @SuppressWarnings("unused")
        public void pickTheme(String name) {
            values.themeName = name;
            values.light = "light".equals(name);
        }

        @Action("app.toggle-density")
        @SuppressWarnings("unused")
        public void toggleDensity() {
            values.density = values.density == Density.REGULAR ? Density.COMPACT : Density.REGULAR;
        }

        @Action("app.toggle-scrollbars")
        @SuppressWarnings("unused")
        public void toggleScrollbars() {
            values.scrollbars = values.scrollbars == Scrollbars.OVERLAY ? Scrollbars.ALWAYS : Scrollbars.OVERLAY;
        }
    }
}
