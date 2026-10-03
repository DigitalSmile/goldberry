package dev.goldberry.example.ui.diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The **Diagnostics** screen: what this run logged, measured and found.
///
/// One card per section of the logging chapter. The readings the window already
/// publishes are bound in `diagnostics.kdl`; the loggers, the build's
/// capabilities and the run's properties are asked for here.
///
/// Read more: [Logging and diagnostics](https://goldberry.dev/docs/guide/logging.html).
///
/// @param context what the screen is built from
public record DiagnosticsChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("guide/logging");

    private static final String LOGGING = "guide/logging";

    /// A few of the messages the chapter explains, as they appear in a log.
    private static final String FAILURES = """
            no action named "app.sav" is bound
            "!prefs.frost" is not a binding path
            unknown node "buton"; registered: …
            libgoldberry not found at …""";

    @Override
    public Widget build(BuildContext buildContext) {
        var byId = new LinkedHashMap<String, Widget>();
        for (var card : context.documents().wall("diagnostics.kdl").children()) {
            var id = card instanceof Card built ? built.attributes().id() : null;
            if (id != null) {
                byId.put(id, card);
            }
        }
        var cards = new ArrayList<Widget>();
        cards.add(new ShowcaseCard(
                        "diagnostics-logback",
                        "A logback configuration",
                        "Goldberry logs through SLF4J and binds no provider. Add one and level the toolkit under"
                                + " dev.goldberry: DEBUG is the window's lifecycle, TRACE adds the start-up timeline"
                                + " and a line per frame. Here is what this run lets through.",
                        DocLink.to(LOGGING, "a-logback-configuration"))
                .of(new LoggerLevels("logback-levels", List.of("dev.goldberry", "dev.goldberry.example"))));
        cards.add(take(byId, "diagnostics-startup"));
        cards.add(take(byId, "diagnostics-frames"));
        cards.add(new ShowcaseCard(
                        "diagnostics-native",
                        "The platform's own libraries",
                        "GLib and SDL log as ordinary SLF4J events on native.<library>.<subsystem>, so each is"
                                + " levelled or switched off by name. -Dgoldberry.log.native=false gives them stderr"
                                + " back.",
                        DocLink.to(LOGGING, "the-platforms-own-libraries"))
                .of(new LoggerLevels(
                        "native-levels",
                        List.of("native", "native.sdl.video", "native.glib.libayatana-appindicator"))));
        cards.add(take(byId, "diagnostics-presentation"));
        cards.add(new ShowcaseCard(
                        "diagnostics-capabilities",
                        "What this build can do",
                        "The desktop integrations are compiled in only where the build machine had the headers."
                                + " Goldberry.capabilities() answers from the library's own record, before any"
                                + " window opens. This is the answer for this build.",
                        DocLink.to(LOGGING, "what-this-build-can-do"))
                .of(new BuildCapabilities(context.capabilities())));
        cards.add(new ShowcaseCard(
                        "diagnostics-properties",
                        "Properties an application can set",
                        "Each is read from -Dname=value on the command line, or set before the toolkit starts."
                                + " The value beside each is what this run was given.",
                        DocLink.to(LOGGING, "properties-an-application-can-set"))
                .of(RunProperties.ofThisRun()));
        cards.add(new ShowcaseCard(
                        "diagnostics-failures",
                        "Failure messages",
                        "The messages that come up most, what each means and where to read more: a misspelt"
                                + " action or binding, an expression in bind=, an unknown node, a missing native"
                                + " library.",
                        DocLink.to(LOGGING, "failure-messages-and-what-they-mean"))
                .of(new Text(FAILURES, Attributes.NONE.classes("mono", "failure-lines"))));
        cards.addAll(byId.values());
        return Wall.of(
                "diagnostics",
                "Diagnostics",
                "Where a slow start or a slow frame went, what the platform's libraries said, which way this"
                        + " window presents and what this build can do.",
                CHAPTER,
                cards);
    }

    /// The document's card called `id`, removed so that whatever is left over
    /// can be appended at the end.
    private static Widget take(Map<String, Widget> cards, String id) {
        var card = cards.remove(id);
        if (card == null) {
            throw new IllegalStateException("diagnostics.kdl has no card with id " + id);
        }
        return card;
    }
}
