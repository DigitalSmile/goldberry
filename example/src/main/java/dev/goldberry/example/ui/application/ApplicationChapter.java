package dev.goldberry.example.ui.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The **Application** screen: how an application is put together, and the
/// markup its views are written in.
///
/// One card per section of two chapters, in the book's order. The cards markup
/// can write are in `application.kdl`; the ones that need a loop, widget state or
/// a document typed at run time are built here, and the two are interleaved by
/// id so the wall reads in the guide's order.
///
/// Read more: [Building an application](https://goldberry.dev/docs/applications.html).
///
/// @param context what the screen is built from
public record ApplicationChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("applications");

    private static final String APPLICATIONS = "applications";

    private static final String MARKUP = "guide/markup";

    /// The direction everything flows in, drawn in text.
    private static final String FLOW = """
            values ── read ──▶ views
            views ── report ──▶ actions
            actions ── assign ──▶ values""";

    /// The layout the chapter recommends.
    private static final String LAYOUT = """
            com.example.app
            ├── Hello.java          Application
            ├── Settings.java       @Model + @Actions
            ├── WindowActions.java  @Actions
            ├── window.kdl          structure
            ├── app.css             appearance
            └── ui/
                ├── Panel.java      Widget records
                └── Gauge.java      @Markup""";

    /// What the first preview starts with.
    static final String INFLATE_SOURCE = """
            column {
              text "Typed at run time"
              row {
                button press="app.click" "March a league"
                badge bind="app.clicks" "0"
              }
            }""";

    /// What the syntax preview starts with.
    static final String SYNTAX_SOURCE = """
            // a node: name, arguments, properties, children
            row {
              /- text "a slashdash takes this out"
              checkbox checked=#true "Keywords are #true"
              text "two lines" \\
                   "as one node"
            }""";

    /// What the strict preview starts with: a typo.
    static final String STRICT_SOURCE = "button press=\"app.clik\" \"March a league\"";

    /// What the path preview starts with: an expression.
    static final String PATH_SOURCE = "text bind=\"!app.light\" \"Lights out\"";

    @Override
    public Widget build(BuildContext buildContext) {
        var byId = new LinkedHashMap<String, Widget>();
        for (var card : context.documents().wall("application.kdl").children()) {
            var id = card instanceof Card built ? built.attributes().id() : null;
            if (id != null) {
                byId.put(id, card);
            }
        }
        var cards = new ArrayList<Widget>();
        cards.add(fourKinds());
        cards.add(take(byId, "application-values"));
        cards.add(take(byId, "application-actions"));
        cards.add(views());
        cards.add(new ShowcaseCard(
                        "application-view-may-not-write",
                        "What a view may not do",
                        "Write. A bound widget is handed a value with no set on it, so a control reports what"
                                + " the user did as an action and the model decides. Who changed this always has"
                                + " an answer.",
                        DocLink.to(APPLICATIONS, "what-a-view-may-not-do"))
                .reference());
        cards.add(new ShowcaseCard(
                        "application-widget-state",
                        "Widget state, application state",
                        "A caret, a scroll offset or a hover is the widget's and dies with it. A value a second"
                                + " screen would need to agree on is the application's. Count with both buttons.",
                        DocLink.to(APPLICATIONS, "widget-state-versus-application-state"))
                .of(new TwoCounters(context)));
        cards.add(new ShowcaseCard(
                        "application-the-application",
                        "The application",
                        "One class implements Application and is the only one that knows a window exists."
                                + " Its models() list is the whole of the wiring: what markup resolves against,"
                                + " and what repaints and restyles.",
                        DocLink.to(APPLICATIONS, "the-application"))
                .reference());
        cards.add(take(byId, "application-more-models"));
        cards.add(new ShowcaseCard(
                        "application-inflate",
                        "Inflating a document",
                        "Widgets.inflater(icons, models) turns parsed nodes into widgets resolved against your"
                                + " models. Edit the document: it is parsed and inflated again on every keystroke.",
                        DocLink.to(APPLICATIONS, "inflating-a-document"))
                .of(new LivePreview("inflate", INFLATE_SOURCE, context)));
        cards.add(new ShowcaseCard(
                        "application-shipping-a-widget",
                        "Shipping a widget",
                        "Annotate a widget record with @Markup and give it a static inflate method. The build"
                                + " collects the module's widgets into a catalogue, so any application with the"
                                + " module on its path can name the node.",
                        DocLink.to(APPLICATIONS, "shipping-a-widget"))
                .reference());
        cards.add(new ShowcaseCard(
                        "application-where-does-it-go",
                        "Where does it go?",
                        "A value a widget shows is a @Bind field; what a button does is an @Action; a caret is"
                                + " widget State; an icon is opened in start and closed in stop; a menu belongs to"
                                + " the Application, which has the Host.",
                        DocLink.to(APPLICATIONS, "where-does-it-go"))
                .reference());
        cards.add(new ShowcaseCard(
                        "application-package-layout",
                        "The package layout",
                        "Views in a package of their own, so they can be replaced without touching the model."
                                + " Everything else is flat.",
                        DocLink.to(APPLICATIONS, "the-package-layout-that-follows"))
                .of(new Text(LAYOUT, Attributes.NONE.classes("mono", "layout-tree"))));
        cards.add(take(byId, "application-starting-fast"));
        cards.add(new ShowcaseCard(
                        "application-weaving",
                        "What the build does",
                        "Your model is plain Java. One Gradle plugin or Maven execution rewrites assignments to"
                                + " its fields so they notify, and forgetting it is a loud error naming the step.",
                        DocLink.to(APPLICATIONS, "what-the-build-does-to-all-this"))
                .reference());
        cards.add(new ShowcaseCard(
                        "markup-syntax",
                        "The syntax",
                        "KDL 2.0: a node is a name, arguments, properties and a child block. Keywords are"
                                + " #true and #false, /- comments out a node. Try a bare true and see it refused.",
                        DocLink.to(MARKUP, "the-syntax-as-goldberry-reads-it"))
                .of(new LivePreview("syntax", SYNTAX_SOURCE, context)));
        cards.add(new ShowcaseCard(
                        "markup-parsing",
                        "Parsing and inflating",
                        "KdlParser turns text into nodes that carry their line and column, and KdlInflater turns"
                                + " nodes into widgets. Widget names need no registration: every widget module"
                                + " on the path announces its own.",
                        DocLink.to(MARKUP, "parsing-and-inflating"))
                .reference());
        cards.add(take(byId, "markup-four-registries"));
        cards.add(new ShowcaseCard(
                        "markup-strict",
                        "Strict by default",
                        "A name nothing registered fails at inflation, with the bound names listed, because a"
                                + " button that silently does nothing is the hardest bug to notice. Fix the typo"
                                + " to app.click.",
                        DocLink.to(MARKUP, "strict-by-default"))
                .of(new LivePreview("strict", STRICT_SOURCE, context)));
        cards.add(new ShowcaseCard(
                        "markup-bind-path",
                        "bind= is a path",
                        "A binding is a name or names joined by dots, never an expression. Negation, formatting"
                                + " and arithmetic stay in Java. Take the ! out and the text follows app.light.",
                        DocLink.to(MARKUP, "bind-is-a-path-and-nothing-else"))
                .of(new LivePreview("path", PATH_SOURCE, context)));
        cards.add(take(byId, "markup-every-node"));
        cards.add(new ShowcaseCard(
                        "markup-hot-reload",
                        "Hot reload",
                        "ReloadableSource keeps the last version of a file that parsed and HotReload watches it."
                                + " A broken save leaves the last good document in force and logs the failure;"
                                + " the next save gets another go.",
                        DocLink.to(MARKUP, "hot-reload"))
                .reference());
        cards.add(new ShowcaseCard(
                        "markup-cannot-say",
                        "What markup cannot say",
                        "A document has no loop and no conditional. One mark per league walked is a row whose"
                                + " length follows a value, so it is built in Java.",
                        DocLink.to(MARKUP, "what-markup-cannot-say"))
                .of(new LeagueMarks(context)));
        cards.add(composed());
        cards.addAll(byId.values());
        return Wall.of(
                "application",
                "Application",
                "How an application is put together: values, actions, views and the one class that owns the"
                        + " window, and the markup the views are written in.",
                CHAPTER,
                cards);
    }

    /// The four kinds of class, and the one direction between them.
    private static Widget fourKinds() {
        return new ShowcaseCard(
                        "application-four-kinds",
                        "The four kinds of class",
                        "Values are a @Model class of fields, actions an @Actions record, views are widgets or"
                                + " a document, and one Application owns the window. Data flows down, events flow"
                                + " up.",
                        DocLink.to(APPLICATIONS, "the-four-kinds-of-class"))
                .of(new Text(FLOW, Attributes.NONE.classes("mono", "flow")));
    }

    /// One value, read once by a node of markup and once by a Java record.
    private Widget views() {
        return new ShowcaseCard(
                        "application-views",
                        "Views",
                        "A view is a widget record or a document, and both name a value the same way:"
                                + " bind=\"app.clicks\" and Models.observable(model, \"app.clicks\") are one lookup.",
                        DocLink.to(APPLICATIONS, "views"))
                .of(
                        new Row(
                                List.of(
                                        new Text("In markup", Attributes.NONE.classes("caption")),
                                        new MarkupSnippet(
                                                "badge id=\"views-markup\" bind=\"app.clicks\" \"0\"", context),
                                        new Text("In Java", Attributes.NONE.classes("caption")),
                                        Badge.of("0", Models.observable(context.model(), "app.clicks"))
                                                .id("views-java")),
                                Attributes.NONE.classes("toolbar")),
                        new Button("March a league", context.actions()::click).id("views-click"));
    }

    /// A button from a document beside a button from Java, both pressing one
    /// action.
    private Widget composed() {
        return new ShowcaseCard(
                        "markup-compose",
                        "Java and markup together",
                        "A document inflates to an ordinary widget, so it goes wherever a widget goes. The first"
                                + " button here is markup, the second is Java, and both press app.click.",
                        DocLink.to(MARKUP, "how-java-and-markup-compose"))
                .of(new Row(
                        List.of(
                                new MarkupSnippet(
                                        "button id=\"compose-markup\" press=\"app.click\" \"From markup\"", context),
                                new Button("From Java", context.actions()::click).id("compose-java"),
                                Badge.of("0", Models.observable(context.model(), "app.clicks"))
                                        .id("compose-count")),
                        Attributes.NONE.classes("toolbar")));
    }

    /// The document's card called `id`, removed from `cards` so that whatever is
    /// left over can be appended at the end.
    private static Widget take(Map<String, Widget> cards, String id) {
        var card = cards.remove(id);
        if (card == null) {
            throw new IllegalStateException("application.kdl has no card with id " + id);
        }
        return card;
    }
}
