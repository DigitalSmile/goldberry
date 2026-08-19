package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.bind.Models;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.example.ui.Panes;
import io.github.digitalsmile.goldberry.kdl.KdlSyntaxException;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// The showcase's two markup documents, inflated against the registries the
/// application really supplies.
///
/// A window that opens is not evidence that a document did anything: an empty
/// `sidebar.kdl` inflates to an empty column and paints a blank panel, and the
/// three-frame headless run would pass. So this asserts the **shape** — that
/// every control the document claims is in the tree, and that its bindings point
/// at the model's own properties rather than at copies of their values.
///
/// It is also a guard against the documents drifting: the registries are strict,
/// so a `bind=` naming a property the model stopped exposing fails here with the
/// line and column rather than in front of a user
/// ([ADR-0094](../../../../../../book/src/adr/0094-name-the-overload-not-the-allocation.md)).
class ShowcaseDocumentsTest {

    /// The real application, for the sake of its real model list.
    private final Showcase showcase = new Showcase();

    private final ShowcaseModel model = modelOf(ShowcaseModel.class);
    private final ShowcaseModel.Actions actions = modelOf(ShowcaseModel.Actions.class);

    private <T> T modelOf(Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance).map(type::cast).findFirst().orElseThrow(
                        () -> new AssertionError("Showcase.models() has no " + type.getSimpleName()));
    }

    /// The inflater, built from **the application's own** `models()` — minus the
    /// icons, which own native memory and are the one thing a test cannot have.
    ///
    /// From `models()` and not from a list assembled here, because a list
    /// assembled here is a list that can be right while the application's is
    /// wrong. That is not hypothetical: the showcase shipped for one commit with
    /// `Widgets.inflater(icons, model, this)` in `start` and `model, actions,
    /// new Showcase()` in this file, so every test passed and the window threw
    /// `no action named "app.toggle-theme" is bound` on the first frame.
    private io.github.digitalsmile.goldberry.kdl.KdlInflater<Widget> inflater() {
        return Widgets.inflater(Icons.lenient(), showcase.models().toArray());
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
    @DisplayName("the title bar names a label, a bound count and the theme button")
    void titleBar() {
        var types = typesIn(Panes.titleBar(inflater()));

        assertEquals(List.of("row", "text", "badge", "spacer", "button"), types);
    }

    /// Every control §3 has shipped so far appears across the gallery's
    /// documents, which is what makes them the showcase's coverage as well as its
    /// demo — `core-widgets.md` asks for exactly this: "a widget isn't done until
    /// it's in the gallery".
    ///
    /// Across the three rather than in one: a screen is a file now, and which file
    /// a control lives in is the gallery's business rather than this test's
    /// (ADR-0110).
    @Test
    @DisplayName("the gallery's documents hold every control the catalog has")
    void galleryCoversTheCatalog() {
        var types = new ArrayList<String>();
        types.addAll(typesIn(Panes.controls(inflater())));
        types.addAll(typesIn(Panes.values(inflater())));
        types.addAll(typesIn(Panes.overlays(inflater())));

        for (var control : List.of("radio-group", "radio", "segmented", "option", "badge",
                "text", "checkbox", "toggle", "slider", "knob", "spinner", "progress",
                "button", "panel")) {
            assertTrue(types.contains(control),
                    () -> "no document builds a " + control + " any more: " + types);
        }
    }

    /// A screen is a file, and every one of them parses and inflates — which is
    /// the assertion that fails when a document is added to the gallery and its
    /// names were never resolved against the registries.
    @Test
    @DisplayName("every screen document inflates against the real registries")
    void everyScreenInflates() {
        assertFalse(typesIn(Panes.controls(inflater())).isEmpty());
        assertFalse(typesIn(Panes.values(inflater())).isEmpty());
        assertFalse(typesIn(Panes.overlays(inflater())).isEmpty());
    }

    /// The half that a shape assertion would miss: a `bind=` that resolved to
    /// nothing would still inflate to a control, and the control would render
    /// perfectly and never move.
    @Test
    @DisplayName("a bound control holds the model's own property, not a copy")
    void bindingsReachTheModel() {
        var bound = new ArrayList<Widget>();
        collectBound(new ElementTree(Panes.controls(inflater())).root(), bound);
        collectBound(new ElementTree(Panes.values(inflater())).root(), bound);

        assertFalse(bound.isEmpty(), "nothing in the gallery's documents is bound");
        assertTrue(bound.stream().anyMatch(w -> w.binding() == Models.observable(model, "app.gain")),
                "no control follows app.gain");
        assertTrue(bound.stream().anyMatch(w -> w.binding() == Models.observable(model, "app.theme")),
                "the theme picker does not follow app.theme");
        assertTrue(bound.stream().anyMatch(w -> w.binding() == Models.observable(model, "app.status")),
                "the status line does not follow app.status");
    }

    private static void collectBound(Element element, List<Widget> into) {
        if (element.widget().binding() != null) {
            into.add(element.widget());
        }
        element.children().forEach(child -> collectBound(child, into));
    }

    /// Three controls on one number is the showcase's clearest demonstration of
    /// ADR-0063, and it is a property of the *document* — so it is asserted here
    /// rather than left to whoever reads the file.
    @Test
    @DisplayName("the slider, the knob and the bar are on the same property")
    void oneValueManyReaders() {
        var onGain = new ArrayList<String>();
        collectOn(new ElementTree(Panes.values(inflater())).root(), Models.observable(model, "app.gain"), onGain);

        assertEquals(List.of("slider", "knob", "slider", "progress"), onGain,
                "the slider, the knob, the fader and the bar — four readers of one number");
    }

    private static void collectOn(Element element, Object property, List<String> into) {
        if (element.widget().binding() == property && element.type() != null) {
            into.add(element.type());
        }
        element.children().forEach(child -> collectOn(child, property, into));
    }

    /// The reason the registries are strict. A typo in a document is a mistake
    /// that otherwise renders as a control that quietly never moves.
    @Test
    @DisplayName("a path the model does not expose fails at inflation")
    void strictRegistriesRefuseATypo() {
        var thrown = assertThrows(RuntimeException.class, () -> inflater()
                .inflate(io.github.digitalsmile.goldberry.kdl.KdlParser
                        .parse("slider bind=\"app.gian\"").getFirst()));

        assertTrue(thrown.getMessage().contains("app.gian"),
                () -> "the failure does not name the path: " + thrown.getMessage());
    }

    @Test
    @DisplayName("every document is on the module path and parses")
    void documentsExist() {
        assertNotNull(Panes.titleBar(inflater()));
        assertNotNull(Panes.controls(inflater()));
        assertNotNull(Panes.values(inflater()));
        assertNotNull(Panes.overlays(inflater()));
    }

    /// The stylesheet is a resource too, and an unstyled window is a window that
    /// renders — badly, and with no error at all.
    @Test
    @DisplayName("the showcase stylesheet loads and is not empty")
    void stylesheetLoads() {
        var sheet = io.github.digitalsmile.goldberry.css.Stylesheet.resource(
                io.github.digitalsmile.goldberry.css.CascadeLayer.APPLICATION,
                Showcase.class, "showcase.css");

        assertFalse(sheet.rules().isEmpty());
    }

    @Test
    @DisplayName("every id the stylesheet targets exists in the tree")
    void stylesheetAndDocumentsAgree() {
        var ids = new ArrayList<String>();
        collectIds(new ElementTree(Panes.titleBar(inflater())).root(), ids);
        collectIds(new ElementTree(Panes.controls(inflater())).root(), ids);
        collectIds(new ElementTree(Panes.values(inflater())).root(), ids);
        collectIds(new ElementTree(Panes.overlays(inflater())).root(), ids);

        // The ids the documents own. `#root`, `#gallery` and the two Java
        // screens' are deliberately absent here.
        for (var id : List.of("bar", "title", "clicks", "theme", "themes",
                "badges", "status", "options", "gain", "knobs", "task", "busy",
                "screen-controls", "screen-values", "screen-overlays",
                "faders", "overlays", "context-target")) {
            assertTrue(ids.contains(id),
                    () -> "showcase.css styles #" + id + " and no document builds it: " + ids);
        }
    }

    private static void collectIds(Element element, List<String> into) {
        if (element.widget() instanceof Styled styled && styled.id() != null) {
            into.add(styled.id());
        }
        element.children().forEach(child -> collectIds(child, into));
    }

    /// A document is inflated once and reused, so the same widget value must come
    /// back — otherwise every rebuild would re-parse a file.
    @Test
    @DisplayName("two inflations of one document are equal values")
    void inflationIsDeterministic() {
        var shared = inflater();

        assertEquals(Panes.titleBar(shared), Panes.titleBar(shared));
    }

    /// The registries are **generated** from `@Bind` and `@Action`, so this is
    /// really asserting that the processor read the model correctly — including
    /// the parse it writes for a valued action, which is the boilerplate the
    /// annotations exist to remove ([ADR-0096]).
    @Test
    @DisplayName("the generated registry exposes what the documents name")
    void registriesAreComplete() {
        var bindings = Models.bindings(model);
        var registry = Models.actions(actions);

        assertSame(Models.observable(model, "app.gain"), bindings.resolve("app.gain"));
        assertSame(Models.observable(model, "app.status"), bindings.resolve("app.status"));
        assertNotNull(registry.resolve("app.toggle-theme"));
        assertNotNull(registry.resolveValued("app.set-gain"));
    }

    /// Every member the registry wires is **private** now, so this is also the
    /// assertion that the handles ADR-0098 generates reach a real field and a
    /// real method in a real application — the processor's own tests prove the
    /// mechanism, and this proves the showcase uses it.
    @Test
    @DisplayName("the registry reaches the model's private members")
    void privateMembersAreReachable() {
        var bindings = Models.bindings(model);
        var registry = Models.actions(actions);

        // The field is package-private and the value is reached by path, which
        // is the only route markup has (ADR-0129).
        assertSame(Models.observable(model, "app.theme"), bindings.resolve("app.theme"));

        // Called through the woven call site, with the value parsed on the way in.
        registry.resolveValued("app.pick-theme").accept("light");
        assertEquals("light", Models.observable(model, "app.theme").get());
    }

    /// A valued action crosses as a `String` and the generated lambda parses it —
    /// so this is the one assertion that the *generated* arithmetic is right and
    /// not merely present.
    @Test
    @DisplayName("a generated valued action parses the value it is handed")
    void generatedValuedActionParses() {
        Models.actions(actions).resolveValued("app.set-gain").accept("62.5");

        assertEquals(62.5, Models.observable(model, "app.gain", Number.class).get().doubleValue(), 1e-9);
    }
}
