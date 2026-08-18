package io.github.digitalsmile.goldberry.example;

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

    private final ShowcaseModel model = new ShowcaseModel();

    /// The registry trio, exactly as `Showcase.start` builds it — minus the
    /// icons, which own native memory and are the one thing a test cannot have.
    private io.github.digitalsmile.goldberry.kdl.KdlInflater<Widget> inflater() {
        // Through `Showcase.actions` rather than the generated registry alone:
        // the documents name two handlers that belong to the window rather than
        // to the model, and a test with its own list of them would pass while
        // the application refused to start.
        return Controls.inflater(
                Showcase.actions(model, () -> { }, () -> { }),
                Icons.lenient(),
                ShowcaseModelRegistry.bindings(model));
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

    /// Every control §3 has shipped so far appears in this one document, which is
    /// what makes the sidebar the showcase's coverage as well as its demo.
    @Test
    @DisplayName("the sidebar holds every control the catalog has")
    void sidebarCoversTheCatalog() {
        var types = typesIn(Panes.sidebar(inflater()));

        for (var control : List.of("radio-group", "radio", "segmented", "option", "badge",
                "text", "checkbox", "toggle", "slider", "knob", "spinner", "progress")) {
            assertTrue(types.contains(control),
                    () -> "sidebar.kdl no longer builds a " + control + ": " + types);
        }
    }

    /// The half that a shape assertion would miss: a `bind=` that resolved to
    /// nothing would still inflate to a control, and the control would render
    /// perfectly and never move.
    @Test
    @DisplayName("a bound control holds the model's own property, not a copy")
    void bindingsReachTheModel() {
        var bound = new ArrayList<Widget>();
        collectBound(new ElementTree(Panes.sidebar(inflater())).root(), bound);

        assertFalse(bound.isEmpty(), "nothing in sidebar.kdl is bound");
        assertTrue(bound.stream().anyMatch(w -> w.binding() == model.gain()),
                "no control follows app.gain");
        assertTrue(bound.stream().anyMatch(w -> w.binding() == model.themeName()),
                "the theme picker does not follow app.theme");
        assertTrue(bound.stream().anyMatch(w -> w.binding() == model.status()),
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
        collectOn(new ElementTree(Panes.sidebar(inflater())).root(), model.gain(), onGain);

        assertEquals(List.of("slider", "knob", "progress"), onGain);
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
    @DisplayName("both documents are on the module path and parse")
    void documentsExist() {
        assertNotNull(Panes.titleBar(inflater()));
        assertNotNull(Panes.sidebar(inflater()));
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
        collectIds(new ElementTree(Panes.sidebar(inflater())).root(), ids);

        // The ids the documents own. `#root`, `#body` and `#content` are the
        // Java panes' and are deliberately absent here.
        for (var id : List.of("bar", "title", "clicks", "theme", "sidebar", "themes",
                "badges", "status", "options", "gain", "knobs", "task", "busy")) {
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
        var bindings = ShowcaseModelRegistry.bindings(model);
        var actions = ShowcaseModelRegistry.actions(model);

        assertSame(model.gain(), bindings.resolve("app.gain"));
        assertSame(model.status(), bindings.resolve("app.status"));
        assertNotNull(actions.resolve("app.toggle-theme"));
        assertNotNull(actions.resolveValued("app.set-gain"));
    }

    /// Every member the registry wires is **private** now, so this is also the
    /// assertion that the handles ADR-0098 generates reach a real field and a
    /// real method in a real application — the processor's own tests prove the
    /// mechanism, and this proves the showcase uses it.
    @Test
    @DisplayName("the registry reaches the model's private members")
    void privateMembersAreReachable() {
        var bindings = ShowcaseModelRegistry.bindings(model);
        var actions = ShowcaseModelRegistry.actions(model);

        // Read through a VarHandle: the field is private and this is the same
        // `Property` the accessor hands out.
        assertSame(model.themeName(), bindings.resolve("app.theme"));

        // Invoked through a MethodHandle, with the value parsed on the way in.
        actions.resolveValued("app.pick-theme").accept("light");
        assertEquals("light", model.themeName().get());
    }

    /// A valued action crosses as a `String` and the generated lambda parses it —
    /// so this is the one assertion that the *generated* arithmetic is right and
    /// not merely present.
    @Test
    @DisplayName("a generated valued action parses the value it is handed")
    void generatedValuedActionParses() {
        ShowcaseModelRegistry.actions(model).resolveValued("app.set-gain").accept("62.5");

        assertEquals(62.5, model.gain().get().doubleValue(), 1e-9);
    }
}
