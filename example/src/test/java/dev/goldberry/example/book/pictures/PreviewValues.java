package dev.goldberry.example.book.pictures;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.goldberry.bind.Observable;
import dev.goldberry.bind.Property;
import dev.goldberry.bind.registry.ActionRegistry;
import dev.goldberry.bind.registry.BindingRegistry;
import dev.goldberry.icon.Icon;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.markup.Wiring;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.panel.list.ListView;
import dev.goldberry.widgets.panel.table.Column;
import dev.goldberry.widgets.panel.table.Table;
import dev.goldberry.widgets.panel.tree.Checkable;
import dev.goldberry.widgets.panel.tree.Tree;
import dev.goldberry.widgets.panel.tree.TreeNode;
import dev.goldberry.widgets.text.Text;

/// What the guide's samples are shown holding.
///
/// A sample binds a widget to a path an application would publish —
/// `bind="audio.gain"`, `bind="app.road"` — and a picture of a slider at
/// nothing, or of a list with no rows, shows the frame and not the widget. So
/// every path the catalogue's samples name is given one value here, of the type
/// the widget reads: a number for a slider, a `LocalDate` for the date picker,
/// the `ListView` itself for `list`, because a row factory is code a document
/// cannot write.
///
/// The wiring is **lenient** in every other respect, as `BookMarkupTest`'s is:
/// an action resolves to nothing and a path not listed here to no value, so a
/// sample that names something new still inflates and the picture shows what a
/// preview would. The names are the samples' own, which is why they read like
/// the showcase's — several are the showcase's.
///
/// Confined to the thread that opened it: the icons it binds are faces.
public final class PreviewValues implements AutoCloseable {

    /// The icons the samples name, and the bundled icon each is drawn as. A
    /// sample names an icon the way an application would, and an application's
    /// `home` is Lucide's `house`.
    private static final Map<String, String> ICONS = Map.of(
            "footprints", "footprints",
            "grid", "layout-grid",
            "home", "house",
            "map", "map",
            "palette", "palette",
            "plus", "plus",
            "tag", "tag");

    /// One row of the table the Collections chapter builds.
    private record Walker(String id, String name, String realm, int leagues) {}

    private static final List<Walker> COMPANY = List.of(
            new Walker("frodo", "Frodo Baggins", "The Shire", 1795),
            new Walker("sam", "Samwise Gamgee", "The Shire", 1795),
            new Walker("gandalf", "Gandalf", "Valinor", 2410),
            new Walker("gimli", "Gimli", "Erebor", 1240));

    private final List<Icon> icons = new ArrayList<>();
    private final Wiring wiring;

    public PreviewValues() {
        var bindings = BindingRegistry.lenient();
        values().forEach(bindings::bind);
        var named = Icons.lenient();
        ICONS.forEach((name, bundled) -> {
            var icon = Icon.bundled(bundled, Icons.SLOT);
            icons.add(icon);
            named.bind(name, icon);
        });
        wiring = new Wiring(ActionRegistry.lenient(), named, bindings);
    }

    /// The wiring a sample inflates against.
    public Wiring wiring() {
        return wiring;
    }

    /// Every path, with the value its widget shows. Package-private so a test
    /// can hold it to the paths the guide writes.
    static Map<String, Observable<?>> values() {
        return Map.ofEntries(
                Map.entry(
                        "app.bio",
                        Property.of("We came down out of the pass at dusk and found the road still under snow.")),
                Map.entry("app.code", Property.of("123")),
                Map.entry("app.company", Property.of(company())),
                Map.entry("app.detail", Property.of(detail())),
                Map.entry("app.lands", Property.of(lands())),
                Map.entry("app.name", Property.of("Peregrin Took")),
                Map.entry("app.notes", Property.of("# Fellowship\n\nNine set out from Rivendell.\n\n- Frodo\n- Sam\n")),
                Map.entry("app.port", Property.of("8080")),
                Map.entry("app.road", Property.of(road())),
                Map.entry("app.status", Property.of("all checks passed")),
                Map.entry("audio.gain", Property.of(62.0)),
                Map.entry("audio.pan", Property.of(0.25)),
                Map.entry("build.state", Property.of("passing")),
                Map.entry(
                        "doc.source",
                        Property.of(
                                "<h1>The Red Book</h1><p>Marked in a hand that was <em>not</em> steady, checking the"
                                        + " <a href=\"https://goldberry.dev\">road</a> as it went.</p>")),
                Map.entry("download.received", Property.of(62.0)),
                Map.entry("filter.unread", Property.of(true)),
                Map.entry("form.error", Property.of("The port is already in use. Choose another.")),
                Map.entry(
                        "note.source",
                        Property.of("# The road\n\nWhere the road goes, *nobody* knows.\n\n- [x] Pack\n- [ ] Leave\n")),
                Map.entry("paint.colour", Property.of(0xFF88C0D0)),
                Map.entry("places.chosen", Property.of("Rivendell")),
                Map.entry("prefs.frost", Property.of(true)),
                Map.entry("prefs.theme", Property.of("dark")),
                Map.entry("prefs.tongues", Property.of(Set.of("sindarin", "westron"))),
                Map.entry("signup.name", Property.of("Meriadoc Brandybuck")),
                Map.entry("signup.port", Property.of("8080")),
                Map.entry("signup.step", Property.of(1)),
                Map.entry("trip.date", Property.of(LocalDate.of(2026, 9, 14))),
                Map.entry("trip.time", Property.of(LocalTime.of(9, 30))),
                Map.entry("view.mode", Property.of("grid")));
    }

    /// The widget the Collections chapter's `slot` is shown holding: a detail
    /// pane for one selection.
    private static Widget detail() {
        return new Card(new Text("Samwise Gamgee"), new Text("The Shire, 1795 leagues").styled("caption"));
    }

    /// The Collections chapter's list, as its Java sample builds it.
    private static ListView<String> road() {
        return ListView.of(List.of("Hobbiton", "Bree", "Weathertop", "Rivendell", "Moria", "Lothlórien"))
                .selected(Set.of("Rivendell"), _ -> {});
    }

    /// The chapter's table, sortable and resizable as the sample says.
    private static Table<Walker> company() {
        return new Table<>(
                COMPANY,
                Walker::id,
                List.of(
                        Column.<Walker>of("name", "Name", Walker::name)
                                .sortable(true)
                                .resizable(true),
                        Column.<Walker>of("realm", "Realm", Walker::realm)
                                .sortable(true)
                                .weight(2),
                        Column.<Walker>of("leagues", "Leagues", walker -> String.valueOf(walker.leagues()))
                                .sortable(true)
                                .fixed(96)));
    }

    /// The chapter's tree, with cascading checkboxes and one branch open.
    private static Tree lands() {
        var roots = List.of(
                TreeNode.of("eriador", "Eriador", TreeNode.leaf("shire", "The Shire"), TreeNode.leaf("bree", "Bree")),
                TreeNode.lazy("erebor", "Erebor", () -> List.of(TreeNode.leaf("dale", "Dale"))));
        return new Tree(roots, "shire", _ -> {}).checkable(Checkable.CASCADE);
    }

    @Override
    public void close() {
        icons.forEach(Icon::close);
    }
}
