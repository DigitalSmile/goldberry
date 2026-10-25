package dev.goldberry.example.model.shell;

import java.util.List;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.example.ui.shell.NavHistory;
import dev.goldberry.example.ui.shell.ReadingSize;

/// What the window around the screens knows: which screen is showing and how
/// it got there, how large the text is set, whether the sidebar is a rail, and
/// the readings the bar prints.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image. The reader is generated.
@SuppressWarnings("UnusedVariable")
public final class ShellModel {

    /// How many screens Home offers to go back to.
    private static final int RECENT = 4;

    /// Which screen is showing: Home, or one of the gallery's. The sidebar,
    /// `Ctrl+1`, a menu row, the search palette and Back are five ways to set
    /// one value rather than five copies of a selection.
    @Bind("app.screen")
    @SuppressWarnings("unused")
    private String screen = HomeScreen.NAME;

    /// Where Back and Forward go. Not bound: nothing draws it, and the actions
    /// that read it are the only way it changes.
    private final NavHistory history = new NavHistory();

    /// How large the text is set. A sheet of type tokens, so a change restyles.
    @Bind(value = "app.reading", restyle = true)
    @SuppressWarnings("unused")
    private ReadingSize reading = ReadingSize.MEDIUM;

    /// Whether the sidebar shows its labels, or only a rail of icons.
    @Bind("app.sidebar")
    @SuppressWarnings("unused")
    private boolean sidebarWide = true;

    /// What the bar says about how this window came up. Written once, from a
    /// background job's continuation — see [dev.goldberry.example.Showcase].
    @Bind("app.status")
    @SuppressWarnings("unused")
    private String status = "reading the map…";

    /// How long this process took to get a window on screen, as the bar prints
    /// it. Separate from [#status] because they answer different questions and
    /// arrive at different times.
    @Bind("app.startup")
    @SuppressWarnings("unused")
    private String startup = "…";

    /// How the window's frames reach the screen, as the bar prints it:
    /// `GPU · vulkan`, or `CPU ·` and why. Set by the window when it changes,
    /// which is at its first frame and whenever it moves between the two.
    @Bind("app.presentation")
    @SuppressWarnings("unused")
    private String presentation = "…";

    /// Whether the frame-rate readout is up.
    ///
    /// On the model and not in the window, although the overlay is the window's,
    /// because the **menu** has to draw a tick beside it — and a menu row's
    /// `checked` is a constant resolved when the bar is built. A window that kept
    /// this to itself would give a bar that had to be told to rebuild by whoever
    /// toggled the HUD, which is exactly the arrangement bound values exist to
    /// replace.
    @Bind("app.hud")
    @SuppressWarnings("unused")
    private boolean hud;

    /// The screen showing: [HomeScreen#NAME] or a gallery screen's name.
    public String screen() {
        return screen;
    }

    /// How large the text is set.
    public ReadingSize readingSize() {
        return reading;
    }

    /// Whether the sidebar shows its labels rather than a rail of icons.
    public boolean isSidebarWide() {
        return sidebarWide;
    }

    /// Whether Back would go anywhere.
    public boolean canGoBack() {
        return history.canGoBack();
    }

    /// Whether Forward would go anywhere.
    public boolean canGoForward() {
        return history.canGoForward();
    }

    /// The gallery screens visited lately, most recent first, each once.
    public List<String> recentScreens() {
        return history.behind().stream()
                .filter(Gallery::has)
                .distinct()
                .limit(RECENT)
                .toList();
    }

    public boolean isHudShown() {
        return hud;
    }

    /// Everything that *happens* to the shell's values.
    ///
    /// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
    @dev.goldberry.bind.runtime.Actions
    public record Actions(ShellModel values) {

        /// Shows a screen, and remembers the one it replaced for Back. What the
        /// sidebar asks for and this decides.
        @Action("app.pick-screen")
        @SuppressWarnings("unused")
        public void pickScreen(String name) {
            if (name.equals(values.screen)) {
                return;
            }
            values.history.visit(values.screen);
            values.screen = name;
        }

        /// The screen before this one, if there was one.
        @Action("app.back")
        @SuppressWarnings("unused")
        public void back() {
            values.history.back(values.screen).ifPresent(name -> values.screen = name);
        }

        /// The screen Back left, if Back was the last move.
        @Action("app.forward")
        @SuppressWarnings("unused")
        public void forward() {
            values.history.forward(values.screen).ifPresent(name -> values.screen = name);
        }

        /// The next screen down the sidebar, wrapping at the end.
        @Action("app.next-screen")
        @SuppressWarnings("unused")
        public void nextScreen() {
            pickScreen(Gallery.step(values.screen, 1));
        }

        /// The screen above this one in the sidebar, wrapping at the top.
        @Action("app.previous-screen")
        @SuppressWarnings("unused")
        public void previousScreen() {
            pickScreen(Gallery.step(values.screen, -1));
        }

        /// The sidebar with its labels, or the rail of icons.
        @Action("app.toggle-sidebar")
        @SuppressWarnings("unused")
        public void toggleSidebar() {
            values.sidebarWide = !values.sidebarWide;
        }

        /// Sets the text to the size `name` spells: `small`, `medium` or `large`.
        @Action("app.pick-reading")
        @SuppressWarnings("unused")
        public void pickReading(String name) {
            values.reading = ReadingSize.named(name);
        }

        /// The text one step larger.
        @Action("app.larger-text")
        @SuppressWarnings("unused")
        public void largerText() {
            values.reading = values.reading.larger();
        }

        /// The text one step smaller.
        @Action("app.smaller-text")
        @SuppressWarnings("unused")
        public void smallerText() {
            values.reading = values.reading.smaller();
        }

        public void setStatus(String value) {
            values.status = value;
        }

        public void setStartup(String value) {
            values.startup = value;
        }

        public void setPresentation(String value) {
            values.presentation = value;
        }

        /// What the window reports after it has floated a HUD or taken one away.
        /// Not an `@Action`: no document names it, and the only caller is the
        /// application itself.
        public void setHud(boolean value) {
            values.hud = value;
        }
    }
}
