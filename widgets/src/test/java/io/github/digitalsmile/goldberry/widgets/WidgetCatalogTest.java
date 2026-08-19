package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.kdl.KdlSyntaxException;
import io.github.digitalsmile.goldberry.widgets.core.Primitives;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// That a widget module announces itself, and that an application gets it
/// without saying so.
///
/// This runs against the **real generated artifact**: `:widgets` applies
/// `goldberry.weave`, so by the time this test runs the build has collected every
/// `@Markup` class in the module into a `GoldberryCatalog` and declared it in the
/// module descriptor. Nothing here builds a catalog by hand — that would test a
/// fixture rather than the thing that ships
/// ([ADR-0131](../../../../../../book/src/adr/0131-a-widget-package-announces-itself.md)).
@DisplayName("a widget module announces itself")
class WidgetCatalogTest {

    @Nested
    @DisplayName("discovery")
    class Discovery {

        @Test
        @DisplayName("the build generated a catalog and the service loader finds it")
        void catalogIsFound() {
            var found = new ArrayList<WidgetCatalog>();
            Widgets.catalogs().forEach(found::add);

            assertFalse(found.isEmpty(),
                    "no WidgetCatalog on the path — the goldberry.weave step did not run,"
                            + " or the module descriptor was not patched");
            assertTrue(found.stream().anyMatch(c -> c.getClass().getSimpleName().equals("GoldberryCatalog")),
                    "found " + found);
            // Worth knowing which of the two declarations this exercised: the
            // test source set runs on the class path, so what is being read here
            // is the `META-INF/services` file. The `provides` patched into
            // `module-info` is the module-path half, and is checked structurally
            // by `CatalogWeaverTest` in `:weaver` -- and exercised for real when
            // the showcase runs, which it does modularly.
            assertFalse(found.getFirst().getClass().getModule().isNamed(),
                    "these tests run on the class path; if that changes, this test now"
                            + " covers the module-info patch and the note above is stale");
        }

        @Test
        @DisplayName("and an inflater gets every name in it without being told any")
        void everyNameIsRegistered() {
            var registered = Widgets.inflater().registered();

            // The two lists this module declares for the parity test, both of
            // which must be in the catalog the build produced.
            assertTrue(registered.containsAll(Primitives.builtInTypes()), registered.toString());
            assertTrue(registered.containsAll(Controls.controlTypes()), registered.toString());
            // And the shell widgets, which neither list names.
            assertTrue(registered.containsAll(
                    List.of("tabs", "tab", "menu", "item", "separator", "popover", "hud")),
                    registered.toString());
        }

        @Test
        @DisplayName("the names come out in a stable order")
        void stableOrder() {
            // Sorted by the build rather than left in file-system order: the list
            // is what an unknown-node error prints, and a class file that differed
            // between machines would break every reproducibility claim here.
            var registered = Widgets.inflater().registered();
            var sorted = new ArrayList<>(registered);
            sorted.sort(null);

            assertEquals(sorted, registered);
        }

        @Test
        @DisplayName("an unknown node names every registered one, with its position")
        void unknownNodeExplains() {
            var thrown = assertThrows(KdlSyntaxException.class,
                    () -> Widgets.inflater().inflate(KdlParser.parse("buton").getFirst()));

            assertTrue(thrown.getMessage().contains("buton"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("button"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("inflating through it")
    class Inflating {

        @Test
        @DisplayName("a document mixes structural and control nodes")
        void mixedDocument() {
            // The thing two separate inflaters made awkward: `column` came from
            // `Primitives` and `button` from `Controls`, and a caller wanting both
            // merged two registries.
            var widget = Widgets.inflater().inflate(
                    KdlParser.parse("column { text \"Hello\"; button \"Apply\" }").getFirst());

            assertNotNull(widget);
        }
    }

    @Nested
    @DisplayName("wiring from models")
    class FromModels {

        @Model
        static final class Left {
            @Bind("left.value") int value;

            @Action("left.act") void act() {
                value++;
            }
        }

        @Model
        static final class Right {
            @Bind("right.value") String value = "r";

            @Action("right.act") void act() {
                value = "moved";
            }
        }

        @Model
        static final class Clashing {
            @Bind("left.value") int value;

            @Action("clash.act") void act() {
                value++;
            }
        }

        @Test
        @DisplayName("two models make one registry, and neither had to be fetched")
        void merged() {
            var wiring = Wiring.of(new Left(), new Right());

            assertEquals(List.of("left.value", "right.value"),
                    List.copyOf(wiring.bindings().bound().keySet()));
            assertEquals(List.of("left.act", "right.act"),
                    List.copyOf(wiring.actions().bound().keySet()));
        }

        @Test
        @DisplayName("a valued action survives the merge as a valued one")
        void valuedSurvives() {
            // The merge has to tell the two halves of the action registry apart:
            // adapting a valued handler down to a Runnable would call it with a
            // value it was never given.
            var model = new Right();
            var wiring = Wiring.of(model);

            wiring.actions().resolve("right.act").run();

            assertEquals("moved", io.github.digitalsmile.goldberry.bind.Models
                    .observable(model, "right.value").get());
        }

        @Test
        @DisplayName("two models claiming one name is refused")
        void clash() {
            var thrown = assertThrows(IllegalStateException.class,
                    () -> Wiring.of(new Left(), new Clashing()));

            assertTrue(thrown.getMessage().contains("left.value"), thrown.getMessage());
        }

        @Test
        @DisplayName("an object that is not a model says so")
        void notAModel() {
            var thrown = assertThrows(IllegalStateException.class, () -> Wiring.of("a string"));

            assertTrue(thrown.getMessage().contains("neither @Model nor @Actions"), thrown.getMessage());
        }
    }
}
