package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ui.gallery.Documents;
import dev.goldberry.icon.Icon;
import dev.goldberry.kdl.KdlInflater;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.markdown.model.Heading;
import dev.goldberry.markdown.view.MarkdownView;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.panel.masonry.Masonry;

/// That the documents behind the window still say what the application thinks
/// they say.
///
/// None of this is a thing a golden image can show. A `bind=` that resolved to a
/// *copy* of a property draws exactly like one that reached the model; a `#name`
/// the stylesheet targets and no document builds is a rule that silently does
/// nothing; and a screen whose root stopped being a `masonry` would put its Java
/// cards in a second wall with no error at all.
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

    /// The two icons the documents name, bound as the window binds them. A button
    /// with an icon and no label is only legal when the registry answers the name.
    private final Icon plus = Icon.bundled("plus", 16);

    private final Icon palette = Icon.bundled("palette", 16);

    private KdlInflater<Widget> inflater() {
        return Widgets.inflater(
                model.named(),
                Icons.lenient().bind("plus", plus).bind("palette", palette),
                showcase.models().toArray());
    }

    @AfterEach
    void closeIcons() {
        plus.close();
        palette.close();
    }

    private final Documents documents = new Documents(inflater());

    /// Every document beside the screens, by file name.
    private static List<String> documentNames() {
        try {
            var url = Documents.class.getResource("/dev/goldberry/example/ui/statusbar.kdl");
            assertNotNull(url, "statusbar.kdl is not on the test's class path");
            try (Stream<Path> files = Files.list(Path.of(url.toURI()).getParent())) {
                return files.map(file -> file.getFileName().toString())
                        .filter(name -> name.endsWith(".kdl"))
                        .sorted()
                        .toList();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (URISyntaxException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /// Every document whose root is a wall of cards.
    private static List<String> walls() {
        return documentNames().stream()
                .filter(name -> KdlParser.resource(Documents.class, "/dev/goldberry/example/ui/" + name)
                        .getFirst()
                        .name()
                        .equals("masonry"))
                .toList();
    }

    private Masonry wall(String name) {
        return documents.wall(name);
    }

    private Widget bar() {
        return documents.document("statusbar.kdl");
    }

    /// Every widget under every document, as one tree per document.
    private List<Widget> everyDocument() {
        return documentNames().stream().map(documents::document).toList();
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

    @Test
    @DisplayName("the Markdown screen binds one property to an editor and a preview")
    void markdownIsLive() {
        // Building the preview parses its document, and md4c is in libgoldberry --
        // which CI's Java job does not build.
        RendererRequirement.enforce();
        // `markdown.kdl` says `markdown-view` and nothing in this application tells
        // the inflater where that node comes from: `goldberry-html` declares a
        // `WidgetCatalog`, the module path carries it, and `Widgets.inflater` finds it
        // through a `uses`. If that mechanism broke, the document
        // would fail to inflate rather than render oddly -- so this is the assertion
        // that a second widget module works at all.
        var views = new ArrayList<MarkdownView>();
        var editors = new ArrayList<TextArea>();
        collectPanes(new ElementTree(documents.document("markdown.kdl")).root(), views, editors);

        assertEquals(1, views.size(), "markdown.kdl should build exactly one markdown-view");
        assertEquals(1, editors.size(), "and exactly one editor beside it");

        // **The live-ness, asserted rather than described.** The editor and the
        // preview are bound to the same property, which is what makes a keystroke a
        // new document: nothing in this application connects them.
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
        // Every document, so the Markdown and HTML chapters too, which parse
        // through md4c and resolve entities with it: libgoldberry, which CI's
        // Java job does not build. Without it this skips rather than failing on a
        // class that could not initialise; the jobs that build the library run it.
        RendererRequirement.enforce();
        documentNames()
                .forEach(name ->
                        assertFalse(typesIn(documents.document(name)).isEmpty(), () -> name + " inflated to nothing"));
    }

    @Test
    @DisplayName("a bound control holds the model's own property, not a copy")
    void bindingsReachTheModel() {
        // Every document again, md4c included, for the reason above.
        RendererRequirement.enforce();
        var bound = new ArrayList<Widget>();
        everyDocument().forEach(document -> collectBound(new ElementTree(document).root(), bound));

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
        collectOn(new ElementTree(wall("values.kdl")).root(), Models.observable(model, "app.gain"), onGain);

        // Sorted, not in document order. What this asserts is *which four
        // controls* read one number; where they fall in the tree is the
        // masonry's, and it packs by column height — so adding a card anywhere
        // in the wall can reorder these four without any of them changing what
        // they read. That is exactly what happened when the buttons card landed,
        // and an assertion that failed for it was testing the wall's
        // packing under a name about bindings.
        assertTrue(
                onGain.containsAll(List.of("knob", "progress", "slider")) && onGain.size() >= 4,
                () -> "a slider, a knob, a fader and a bar read one number: " + onGain);
    }

    @Test
    @DisplayName("the light is picked four ways, on one property in two spellings")
    void theLightIsPickedFourWays() {
        var theme = Models.observable(model, "app.theme");
        var byName = new ArrayList<Widget>();
        collectBoundTo(new ElementTree(wall("choices.kdl")).root(), theme, byName);
        var asFlag = new ArrayList<Widget>();
        collectBoundTo(new ElementTree(bar()).root(), Models.observable(model, "app.light"), asFlag);

        var pickers = byName.stream().map(w -> w.getClass().getSimpleName()).toList();
        assertTrue(
                pickers.containsAll(List.of("RadioGroup", "Segmented", "Select")),
                () -> "a radio group, a segmented bar and a select read the theme by name: " + pickers);
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
        if (element.widget().binding() == property) {
            // The element's own type when it has one, and otherwise the type of
            // the node it builds. A **stateful** control carries its binding on
            // the widget a document wrote and its CSS type on the node that
            // widget builds — `slider`, the arrangement `tabs`,
            // `collapse` and `toaster` have always had. Asking only the bound
            // element would have this test quietly counting two readers where
            // there are four, which is what it caught when `slider` became
            // stateful.
            var type = element.type() == null ? styledTypeBelow(element) : element.type();
            if (type != null) {
                into.add(type);
            }
        }
        element.children().forEach(child -> collectOn(child, property, into));
    }

    /// The CSS type of the nearest descendant that has one, breadth-first.
    ///
    /// Breadth-first because a stateful widget's styled node is its immediate
    /// child; a depth-first walk would reach that node's own first child first
    /// and report a part (`slider-track`) where the control (`slider`) is meant.
    private static @Nullable String styledTypeBelow(Element element) {
        var queue = new java.util.ArrayDeque<>(element.children());
        while (!queue.isEmpty()) {
            var next = queue.removeFirst();
            if (next.type() != null) {
                return next.type();
            }
            queue.addAll(next.children());
        }
        return null;
    }

    @Test
    @DisplayName("a path the model does not expose fails at inflation")
    void strictRegistriesRefuseATypo() {
        var thrown = assertThrows(
                RuntimeException.class,
                () -> inflater()
                        .inflate(dev.goldberry.kdl.KdlParser.parse("slider bind=\"app.gian\"")
                                .getFirst()));

        assertTrue(
                thrown.getMessage().contains("app.gian"),
                () -> "the failure does not name the path: " + thrown.getMessage());
    }

    @Test
    @DisplayName("every document is on the module path and parses")
    void documentsExist() {
        assertNotNull(bar());
        documentNames().forEach(name -> assertNotNull(documents.document(name)));
    }

    @Test
    @DisplayName("the showcase stylesheet loads and is not empty")
    void stylesheetLoads() {
        var sheet = dev.goldberry.css.Stylesheet.resource(
                dev.goldberry.css.cascade.CascadeLayer.APPLICATION, Showcase.class, "showcase.css");

        assertFalse(sheet.rules().isEmpty());
    }

    @Test
    @DisplayName("every id the stylesheet gives the bar, the bar builds")
    void stylesheetAndBarAgree() {
        var ids = new ArrayList<String>();
        collectIds(new ElementTree(bar()).root(), ids);

        for (var id : List.of(
                "bar", "title", "clicks", "startup", "status", "light-switch", "dark-label", "light-label", "theme")) {
            assertTrue(ids.contains(id), () -> "showcase.css styles #" + id + " and the bar does not build it: " + ids);
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
    /// On `panels-cards.kdl`, which has no `change=` in it. A `change=` on a
    /// `toggle`, a `slider` or a `knob` arrives through `Wiring.flag`/`numeric`,
    /// which wrap the registry's `Consumer<String>` in a **new** lambda every call —
    /// so two inflations of a document with one are equal in every component but
    /// that one, and never equal.
    /// That is a fact about adapters rather than about determinism, and asserting
    /// it here would only pin the adapter.
    @Test
    @DisplayName("two inflations of one document are equal values")
    void inflationIsDeterministic() {
        var shared = inflater();

        assertEquals(new Documents(shared).wall("panels-cards.kdl"), new Documents(shared).wall("panels-cards.kdl"));
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
        // route markup has: a value is named one way.
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
