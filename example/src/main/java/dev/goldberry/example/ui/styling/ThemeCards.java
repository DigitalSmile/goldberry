package dev.goldberry.example.ui.styling;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.bind.Subscription;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.platform.Capability;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.Scrollbars;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.text.Text;

/// The Styling screen's cards about what changes every style at once: a theme,
/// a density, the scroll bars and the desktop's own light or dark.
///
/// The buttons ask the application's actions, the same ones `Ctrl+T`, the bar's
/// switch and the File menu ask, so a theme changed here moves every other
/// control that shows it.
///
/// Read more: [Themes, density and
/// scrollbars](https://goldberry.dev/docs/guide/styling.html#themes-density-and-scrollbars).
final class ThemeCards {

    private static final String CHAPTER = "guide/styling";

    private ThemeCards() {}

    /// What the window is drawn in now, as a line a reader can check.
    static String describe(ShowcaseModel model) {
        var theme = model.theme() == Theme.NORD_LIGHT ? "Nord light" : "Nord dark";
        var density = model.density() == Density.COMPACT ? "compact" : "regular";
        var bars = model.scrollbars() == Scrollbars.ALWAYS ? "scroll bars always shown" : "overlay scroll bars";
        return theme + ", " + density + ", " + bars + ".";
    }

    static Widget restyle(ShowcaseModel model, ShowcaseModel.Actions actions) {
        return new ShowcaseCard(
                        "styling-restyle",
                        "Restyle versus repaint",
                        "A repaint redraws what changed; a restyle throws every resolved style away and reads the"
                                + " sheets again. A theme is a restyle, and Ctrl+T asks for one anywhere in the"
                                + " window. A count is only a repaint.",
                        DocLink.to(CHAPTER, "restyle-versus-repaint"))
                .of(
                        Demo.row(
                                "restyle-actions",
                                new Button("Restyle: switch the light", actions::toggleTheme).id("restyle-theme"),
                                new Live.Counter("restyle-count", "Repaint: count", List.of())),
                        new Text(
                                describe(model),
                                Attributes.NONE.id("restyle-now").classes("caption")));
    }

    static Widget themes(ShowcaseModel model, ShowcaseModel.Actions actions) {
        return new ShowcaseCard(
                        "styling-themes",
                        "Themes, density and scrollbars",
                        "A theme is a layer of custom properties; compact density is a handful of tokens that make"
                                + " every control 28 tall instead of 32; always-shown scroll bars reserve a 12 px"
                                + " gutter. Each is a stylesheet swapped at run time.",
                        DocLink.to(CHAPTER, "themes-density-and-scrollbars"))
                .of(
                        Demo.row(
                                "theme-actions",
                                new Button("Switch the light", actions::toggleTheme).id("theme-switch"),
                                new Button("Switch the density", actions::toggleDensity).id("density-switch"),
                                new Button("Switch the scroll bars", actions::toggleScrollbars)
                                        .id("scrollbars-switch")),
                        new Text(
                                describe(model), Attributes.NONE.id("theme-now").classes("caption")));
    }

    static Widget textScale() {
        return new ShowcaseCard(
                        "styling-text-scale",
                        "Text scale",
                        "The renderer scales text between 90% and 150% without scaling the boxes, and every"
                                + " control is built to survive it. It is a switch on a renderer an application"
                                + " builds itself; the launcher does not offer it yet.",
                        DocLink.to(CHAPTER, "text-scale"))
                .reference();
    }

    /// What the desktop says about light and dark, live, and a button that
    /// follows it.
    ///
    /// @param actions      where following the desktop is asked for
    /// @param capabilities what this build can do, which says whether it can ask
    record Desktop(ShowcaseModel.Actions actions, Set<Capability> capabilities) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new DesktopState();
        }
    }

    static final class DesktopState extends State<Desktop> {

        private @Nullable Host host;

        private @Nullable Subscription listening;

        private Optional<SystemTheme> said = Optional.empty();

        @Override
        public Widget build(BuildContext context) {
            if (host == null) {
                host = context.host().orElse(null);
                if (host != null) {
                    said = host.systemTheme();
                    listening = host.onSystemThemeChanged(theme -> setState(() -> said = Optional.of(theme)));
                }
            }
            var canAsk = widget().capabilities().contains(Capability.SYSTEM_THEME);
            var answer = !canAsk
                    ? "This build cannot ask the desktop."
                    : said.map(theme -> "The desktop says " + theme.name().toLowerCase(Locale.ROOT) + ".")
                            .orElse("The desktop does not say, which is not the same as light.");
            return new ShowcaseCard(
                            "styling-desktop",
                            "Following the desktop",
                            "host.systemTheme() answers light, dark, or nothing where the desktop has no such"
                                    + " setting, and a listener hears it change at dusk. The toolkit chooses"
                                    + " nothing with the answer: following it is the application's call.",
                            DocLink.to(CHAPTER, "following-the-desktop"))
                    .of(
                            new Text(answer, Attributes.NONE.id("desktop-says")),
                            Demo.row(
                                    "desktop-actions",
                                    new Button("Follow the desktop", this::follow)
                                            .disabled(said.isEmpty())
                                            .id("desktop-follow")));
        }

        private void follow() {
            said.ifPresent(theme -> widget().actions().pickTheme(theme == SystemTheme.DARK ? "dark" : "light"));
        }

        @Override
        protected void dispose() {
            if (listening != null) {
                listening.close();
                listening = null;
            }
            super.dispose();
        }
    }
}
