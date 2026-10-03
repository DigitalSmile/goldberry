package dev.goldberry.example.ui.windows;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Subscription;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.text.Text;

/// What the desktop says about light and dark, and about motion, read from the
/// host and followed while the card is on screen.
///
/// Read more: [The desktop's theme](https://goldberry.dev/docs/guide/windows.html#the-desktops-theme).
public record SystemThemeCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-theme";

    @Override
    public State<?> createState() {
        return new ThemeState();
    }

    /// The answer in words, keeping "says nothing" apart from "light".
    static String describe(Optional<SystemTheme> theme) {
        return theme.map(value -> "The desktop says " + value.name().toLowerCase(Locale.ROOT) + ".")
                .orElse("The desktop says nothing, which is not the same as light.");
    }

    static final class ThemeState extends State<SystemThemeCard> {

        /// The listener on the host, or null before the first build with one.
        private @Nullable Subscription following;

        private boolean asked;

        private Optional<SystemTheme> theme = Optional.empty();

        private Optional<Boolean> reducedMotion = Optional.empty();

        private int changes;

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            if (!asked) {
                asked = true;
                host.ifPresent(window -> {
                    theme = window.systemTheme();
                    reducedMotion = window.reducedMotion();
                    following = window.onSystemThemeChanged(now -> setState(() -> {
                        theme = Optional.of(now);
                        changes++;
                    }));
                });
            }
            var motion = reducedMotion
                    .map(less -> less ? "It asks for less movement." : "It does not ask for less movement.")
                    .orElse("It says nothing about movement.");
            var followed = host.isEmpty()
                    ? "No host here, so nothing is followed."
                    : "Followed: " + changes + (changes == 1 ? " change" : " changes") + " since this card opened.";
            return new ShowcaseCard(
                            ID,
                            "The desktop's theme",
                            "host.systemTheme() is light, dark, or empty where the desktop has no such setting, and"
                                    + " onSystemThemeChanged follows it. Switch your desktop's style and watch the"
                                    + " count.",
                            DocLink.to("guide/windows", "the-desktops-theme"))
                    .of(
                            new Text(
                                    describe(theme),
                                    Attributes.NONE.id("theme-answer").classes("readout")),
                            new Text(motion, Attributes.NONE.id("theme-motion").classes("readout")),
                            new Text(
                                    followed,
                                    Attributes.NONE.id("theme-changes").classes("readout")));
        }

        @Override
        protected void dispose() {
            var subscription = following;
            if (subscription != null) {
                subscription.close();
                following = null;
            }
        }
    }
}
