package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.example.ui.Panes;
import io.github.digitalsmile.goldberry.markdown.model.Heading;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownView;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.form.textarea.TextArea;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;

/// That the five documents behind the window still say what the application
/// thinks they say.
///
/// None of this is a thing a golden image can show. A `bind=` that resolved to a
/// *copy* of a property draws exactly like one that reached the model; a `#name`
/// the stylesheet targets and no document builds is a rule that silently does
/// nothing; and a screen whose root stopped being a `masonry` would put its Java
/// cards in a second wall with no error at all (ADR-0222).
class ShowcaseDocumentsTest {

    private final Showcase showcase = new Showcase();

    private final ShowcaseModel model = modelOf(ShowcaseModel.class);
    private final ShowcaseModel.Actions actions = modelOf(ShowcaseModel.Actions.class);

    private <T> T modelOf(Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Showcase.models() has no " + type.getSimpleName()));
    }

    private io.github.digitalsmile.goldberry.kdl.KdlInflater<Widget> inflater() {
        return Widgets.inflater(
                model.named(), Icons.lenient(), showcase.models().toArray());
    }

    /// Every document whose root is a wall of cards, by the name the failure
    /// should print.
    private static final List<String> WALLS = List.of("basic", "panels", "overlays", "forms");

    private Masonry wall(String name) {
        return switch (name) {
            case "basic" -> Panes.basic(inflater());
            case "panels" -> Panes.panels(inflater());
            case "overlays" -> Panes.overlays(inflater());
            case "forms" -> Panes.forms(inflater());
            default -> throw new AssertionError("no document called " + name);
        };
    }

    private static List<String> typesIn(Widget widget) {
        var found = new ArrayList<String>();
        collect(new ElementTree(widget).root(), found);
        return found;
    }

    private static void collect(Element element, List<String> into) {
        if (element.type() != null) {
            into.add(element.type());
        }
        element.children().forEach(child -> collect(child, into));
    }

    /// Every node type in every wall, which is what "the gallery covers the
    /// catalog" is asked against.
    private List<String> everyType() {
        var types = new ArrayList<String>();
        WALLS.forEach(name -> types.addAll(typesIn(wall(name))));
        types.addAll(typesIn(Panes.bar(inflater())));
        return types;
    }

    @Test
    @DisplayName("the bar names a label, a bound count, two readings, a switch and a button")
    void bar() {
        // The row's own children, not `typesIn`'s whole subtree: a `toggle` is a
        // track and a thumb underneath, and asserting those here would make this
        // test fail when the *switch* was restyled rather than when the bar
        // changed.
        var types = new ArrayList<String>();
        new ElementTree(Panes.bar(inflater())).root().children().forEach(child -> types.add(child.type()));

        assertEquals(
                List.of("text", "badge", "text", "text", "spacer", "text", "toggle", "text", "button"),
                types,
                "the bar is startup on the left and the light on the right");
    }

    @Test
    @DisplayName("every wall has a masonry at its root, because a screen appends to it")
    void everyWallIsAWall() {
        for (var name : WALLS) {
            var wall = wall(name);
            assertFalse(wall.children().isEmpty(), () -> name + ".kdl built no cards");
            // Not a detail: `Wall.of` rebuilds the masonry with the Java cards
            // added, and a column count of zero would throw where a wrong one
            // would silently re-lay the whole screen.
            assertTrue(wall.columns() >= 1, () -> name + ".kdl asks for " + wall.columns() + " columns");
        }
    }

    @Test
    @DisplayName("a document whose root is not a masonry is refused by name")
    void aRootThatIsNotAWallIsRefused() {
        // The failure this guards: a `column` wrapped round the masonry during an
        // edit. Nothing throws at inflation -- it is a perfectly good document --
        // and the screen quietly grows a second wall under the first.
        var thrown = assertThrows(IllegalStateException.class, () -> Panes.wallOf(inflater(), "statusbar.kdl"));

        assertTrue(thrown.getMessage().contains("statusbar.kdl"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("masonry"), thrown.getMessage());
    }

    @Test
    @DisplayName("the gallery's documents hold every control the catalog has")
    void galleryCoversTheCatalog() {
        var types = everyType();

        for (var control : List.of(
                // §3 -- the controls. No `select`: it is `Stateful`, so the node
                // that carries a css type is the `select-field` its state builds,
                // and the widget itself reports none. It is asserted by class in
                // `theLightIsPickedFourWays` instead.
                "radio-group",
                "radio",
                "segmented",
                "option",
                "badge",
                "checkbox",
                "toggle",
                "slider",
                "knob",
                "spinner",
                "progress",
                "button",
                // §5 -- the containers
                "panel",
                "card",
                "group-box",
                "masonry",
                "statistic",
                "skeleton",
                "split-pane",
                "carousel",
                "collapse",
                // §4 -- the fields
                "text-input",
                "text-area",
                "form",
                "field",
                // §7 and §8. No `item` and no `separator`: a menu bar draws a row
                // of titles and builds its rows only when one is *opened*, so the
                // items `overlays.kdl` writes are not in a closed bar's tree at
                // all -- which is the whole point of a menu being a popup.
                "menubar",
                "text",
                "row",
                "column",
                "spacer")) {
            assertTrue(types.contains(control), () -> "no document builds a " + control + " any more: " + types);
        }
    }

    @Test
    @DisplayName("the Markdown screen binds one property to an editor and a preview")
    void markdownIsLive() {
        // Building the preview parses its document, and md4c is in libgoldberry --
        // which CI's Java job does not build. The one test here that needs it.
        RendererRequirement.enforce();
        // `markdown.kdl` says `markdown-view` and nothing in this application tells
        // the inflater where that node comes from: `goldberry-html` declares a
        // `WidgetCatalog`, the module path carries it, and `Widgets.inflater` finds it
        // through a `uses` (ADR-0131, ADR-0294). If that mechanism broke, the document
        // would fail to inflate rather than render oddly -- so this is the assertion
        // that a second widget module works at all.
        var views = new ArrayList<MarkdownView>();
        var editors = new ArrayList<TextArea>();
        collectPanes(new ElementTree(Panes.markdown(inflater())).root(), views, editors);

        assertEquals(1, views.size(), "markdown.kdl should build exactly one markdown-view");
        assertEquals(1, editors.size(), "and exactly one editor beside it");

        // **The live-ness, asserted rather than described.** The editor and the
        // preview are bound to the same property, which is what makes a keystroke a
        // new document: nothing in this application connects them (ADR-0296).
        var preview = views.getFirst();
        var editor = editors.getFirst();
        assertNotNull(preview.binding(), "the preview follows a property or it is not live");
        assertSame(
                Models.observable(model, "md.source"),
                preview.binding(),
                "the preview should follow md.source, which is what the editor writes");
        assertSame(editor.binding(), preview.binding(), "one property, read twice");

        // And that the sample actually parses into a document rather than into one
        // paragraph of literal Markdown.
        var document = preview.resolved();
        assertTrue(
                document.blocks().getFirst() instanceof Heading heading && heading.level() == 1,
                () -> "the sample starts with " + document.blocks().getFirst());
        assertTrue(document.blocks().size() > 20, "the sample covers every construct, so it is not three blocks");
    }

    /// Every `markdown-view` and every `text-area` under `element`, at any depth — a
    /// split pane wraps each child, so neither is a child of the root.
    private static void collectPanes(Element element, List<MarkdownView> views, List<TextArea> editors) {
        if (element.widget() instanceof MarkdownView view) {
            views.add(view);
        }
        if (element.widget() instanceof TextArea editor) {
            editors.add(editor);
        }
        element.children().forEach(child -> collectPanes(child, views, editors));
    }

    @Test
    @DisplayName("every screen document inflates against the real registries")
    void everyScreenInflates() {
        WALLS.forEach(name -> assertFalse(typesIn(wall(name)).isEmpty(), () -> name + ".kdl inflated to nothing"));
        assertFalse(typesIn(Panes.bar(inflater())).isEmpty());
    }

    @Test
    @DisplayName("a bound control holds the model's own property, not a copy")
    void bindingsReachTheModel() {
        var bound = new ArrayList<Widget>();
        collectBound(new ElementTree(wall("basic")).root(), bound);
        collectBound(new ElementTree(Panes.bar(inflater())).root(), bound);

        assertFalse(bound.isEmpty(), "nothing in the gallery's documents is bound");
        for (var path :
                List.of("app.gain", "app.theme", "app.light", "app.status", "app.startup", "app.clicks", "app.prose")) {
            var property = Models.observable(model, path);
            assertTrue(bound.stream().anyMatch(w -> w.binding() == property), () -> "no control follows " + path);
        }
    }

    private static void collectBound(Element element, List<Widget> into) {
        if (element.widget().binding() != null) {
            into.add(element.widget());
        }
        element.children().forEach(child -> collectBound(child, into));
    }

    @Test
    @DisplayName("the slider, the knob, the fader and the bar are on one property")
    void oneValueManyReaders() {
        var onGain = new ArrayList<String>();
        collectOn(new ElementTree(wall("basic")).root(), Models.observable(model, "app.gain"), onGain);

        // Sorted, not in document order. What this asserts is *which four
        // controls* read one number; where they fall in the tree is the
        // masonry's, and it packs by column height — so adding a card anywhere
        // in the wall can reorder these four without any of them changing what
        // they read. That is exactly what happened when the buttons card landed
        // (ADR-0293), and an assertion that failed for it was testing the wall's
        // packing under a name about bindings.
        assertEquals(
                List.of("knob", "progress", "slider", "slider"),
                onGain.stream().sorted().toList(),
                "the slider, the knob, the fader and the bar — four readers of one number");
    }

    @Test
    @DisplayName("the light is picked four ways, on one property in two spellings")
    void theLightIsPickedFourWays() {
        var theme = Models.observable(model, "app.theme");
        var byName = new ArrayList<Widget>();
        collectBoundTo(new ElementTree(wall("basic")).root(), theme, byName);
        var asFlag = new ArrayList<Widget>();
        collectBoundTo(new ElementTree(Panes.bar(inflater())).root(), Models.observable(model, "app.light"), asFlag);

        assertEquals(
                List.of("RadioGroup", "Segmented", "Select"),
                byName.stream().map(w -> w.getClass().getSimpleName()).toList(),
                "three pickers read the theme by name");
        assertEquals(
                List.of("Toggle"),
                asFlag.stream().map(w -> w.getClass().getSimpleName()).toList(),
                "and the bar's switch reads the same fact as a boolean, because a switch"
                        + " falls back to its own flag for anything that is not one");
    }

    private static void collectBoundTo(Element element, Object property, List<Widget> into) {
        if (element.widget().binding() == property) {
            into.add(element.widget());
        }
        element.children().forEach(child -> collectBoundTo(child, property, into));
    }

    private static void collectOn(Element element, Object property, List<String> into) {
        if (element.widget().binding() == property && element.type() != null) {
            into.add(element.type());
        }
        element.children().forEach(child -> collectOn(child, property, into));
    }

    @Test
    @DisplayName("a path the model does not expose fails at inflation")
    void strictRegistriesRefuseATypo() {
        var thrown = assertThrows(
                RuntimeException.class,
                () -> inflater()
                        .inflate(io.github.digitalsmile.goldberry.kdl.KdlParser.parse("slider bind=\"app.gian\"")
                                .getFirst()));

        assertTrue(
                thrown.getMessage().contains("app.gian"),
                () -> "the failure does not name the path: " + thrown.getMessage());
    }

    @Test
    @DisplayName("every document is on the module path and parses")
    void documentsExist() {
        assertNotNull(Panes.bar(inflater()));
        WALLS.forEach(name -> assertNotNull(wall(name)));
    }

    @Test
    @DisplayName("the showcase stylesheet loads and is not empty")
    void stylesheetLoads() {
        var sheet = io.github.digitalsmile.goldberry.css.Stylesheet.resource(
                io.github.digitalsmile.goldberry.css.cascade.CascadeLayer.APPLICATION, Showcase.class, "showcase.css");

        assertFalse(sheet.rules().isEmpty());
    }

    @Test
    @DisplayName("every id the stylesheet targets exists in the tree")
    void stylesheetAndDocumentsAgree() {
        var ids = new ArrayList<String>();
        collectIds(new ElementTree(Panes.bar(inflater())).root(), ids);
        WALLS.forEach(name -> collectIds(new ElementTree(wall(name)).root(), ids));

        // The ids the documents own. `#root`, `#gallery`, `#app-menu` and the ids
        // the Java cards build are deliberately absent here.
        for (var id : List.of(
                // the bar
                "bar",
                "title",
                "clicks",
                "startup",
                "status",
                "light-switch",
                "dark-label",
                "light-label",
                "theme",
                // Basic
                "themes",
                "theme-bar",
                "theme-select",
                "badges",
                "gain",
                "knobs",
                "faders",
                "busy",
                // Panels
                "surfaces",
                "numbers",
                "demo-split",
                "demo-carousel",
                "demo-accordion",
                // Overlays
                "overlays",
                "context-target",
                // Forms
                "signup",
                "named-echo")) {
            assertTrue(ids.contains(id), () -> "showcase.css styles #" + id + " and no document builds it: " + ids);
        }
    }

    private static void collectIds(Element element, List<String> into) {
        if (element.widget() instanceof Styled styled && styled.id() != null) {
            into.add(styled.id());
        }
        element.children().forEach(child -> collectIds(child, into));
    }

    /// Two inflations of one document are the same **value**.
    ///
    /// On `panels.kdl`, and it has to be: it is the one document in the gallery
    /// with no `change=` in it. A `change=` on a `toggle`, a `slider` or a `knob`
    /// arrives through `Wiring.flag`/`numeric`, which wrap the registry's
    /// `Consumer<String>` in a **new** lambda every call — so two inflations of
    /// `basic.kdl` are equal in every component but that one, and never equal.
    /// That is a fact about adapters rather than about determinism, and asserting
    /// it here would only pin the adapter.
    @Test
    @DisplayName("two inflations of one document are equal values")
    void inflationIsDeterministic() {
        var shared = inflater();

        assertEquals(Panes.panels(shared), Panes.panels(shared));
    }

    @Test
    @DisplayName("the generated registry exposes what the documents name")
    void registriesAreComplete() {
        var bindings = Models.bindings(model);
        var registry = Models.actions(actions);

        assertSame(Models.observable(model, "app.gain"), bindings.resolve("app.gain"));
        assertSame(Models.observable(model, "app.startup"), bindings.resolve("app.startup"));
        assertNotNull(registry.resolve("app.toggle-theme"));
        assertNotNull(registry.resolveValued("app.set-gain"));
        assertNotNull(registry.resolve("app.submit-signup"));
    }

    @Test
    @DisplayName("the registry reaches the model's private members")
    void privateMembersAreReachable() {
        var bindings = Models.bindings(model);
        var registry = Models.actions(actions);

        // The field is private and the value is reached by path, which is the only
        // route markup has (ADR-0129).
        assertSame(Models.observable(model, "app.theme"), bindings.resolve("app.theme"));

        // Called through the woven call site, with the value parsed on the way in.
        registry.resolveValued("app.pick-theme").accept("light");
        assertEquals("light", Models.observable(model, "app.theme").get());
    }

    /// The two spellings of the light, which is the one place in the model where
    /// a value is written down twice.
    ///
    /// Asserted because the failure is silent and asymmetric: a `pickTheme` that
    /// forgot the flag would leave the bar's switch stuck while every other
    /// control moved, and only in one direction.
    @Test
    @DisplayName("every route to the theme moves both spellings of it")
    void theTwoSpellingsAgree() {
        var name = Models.observable(model, "app.theme");
        var flag = Models.observable(model, "app.light");

        List<Runnable> routes = List.of(
                actions::toggleTheme,
                () -> actions.pickTheme("light"),
                () -> actions.pickTheme("dark"),
                () -> actions.setLight(true),
                () -> actions.setLight(false));

        Function<Object, Boolean> asFlag = value -> "light".equals(value);
        for (var route : routes) {
            route.run();
            assertEquals(
                    asFlag.apply(name.get()),
                    flag.get(),
                    () -> "app.theme says " + name.get() + " and app.light says " + flag.get());
        }
    }

    @Test
    @DisplayName("a generated valued action parses the value it is handed")
    void generatedValuedActionParses() {
        Models.actions(actions).resolveValued("app.set-gain").accept("62.5");

        assertEquals(
                62.5, Models.observable(model, "app.gain", Number.class).get().doubleValue(), 1e-9);
    }
}
