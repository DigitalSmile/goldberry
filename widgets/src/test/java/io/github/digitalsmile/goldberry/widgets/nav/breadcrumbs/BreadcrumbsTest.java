package io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.registry.ActionRegistry;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// §6's `breadcrumbs` — the path to here, and the first widget of the `nav`
/// package ([ADR-0306]).
///
/// The trail is stateful, so most of what is asserted here is read off the
/// **built** tree rather than off the value: a `Breadcrumbs` is a list of crumbs
/// and the thing under test is what its state makes of them — which is current,
/// what is hidden, and where the separators go.
class BreadcrumbsTest {

    private final List<String> log = new ArrayList<>();

    /// The crumbs and separators the trail actually builds, in order.
    ///
    /// Through an [ElementTree] rather than by calling `build` directly, because
    /// the state is created and mounted by the tree and reaching past it would be
    /// testing a method rather than a widget.
    private static List<Widget> row(Breadcrumbs trail) {
        return trailNode(trail).children().stream().map(Element::widget).toList();
    }

    /// The [CrumbTrail] element under the stateful node.
    ///
    /// One level down, and that is the arrangement under test rather than an
    /// inconvenience: `Breadcrumbs` is a composition node that styles nothing,
    /// and what a stylesheet and a hit test see is the child it builds.
    private static Element trailNode(Breadcrumbs trail) {
        return new ElementTree(trail).root().children().getFirst();
    }

    private static Breadcrumbs trail(int count) {
        var crumbs = new ArrayList<Widget>(count);
        for (var index = 0; index < count; index++) {
            crumbs.add(new Crumb("step-" + index, () -> {}));
        }
        return new Breadcrumbs(crumbs.toArray(Widget[]::new));
    }

    private static List<String> labels(List<Widget> row) {
        return row.stream()
                .map(widget -> switch (widget) {
                    case Crumb crumb -> crumb.label();
                    case CrumbSeparator _ -> ">";
                    case CrumbOverflow _ -> "…";
                    default -> widget.getClass().getSimpleName();
                })
                .toList();
    }

    @Nested
    @DisplayName("the row")
    class Row {

        @Test
        @DisplayName("a chevron goes between every pair and nowhere else")
        void separatorsAreInterleaved() {
            assertEquals(List.of("step-0", ">", "step-1", ">", "step-2"), labels(row(trail(3))));
        }

        @Test
        @DisplayName("one crumb is a row with no separator at all")
        void aSingleCrumbStandsAlone() {
            assertEquals(List.of("step-0"), labels(row(trail(1))));
        }

        @Test
        @DisplayName("an empty trail builds an empty row rather than failing")
        void nothingIsAValidPath() {
            // A path is commonly built from a loop, and a loop over nothing is a
            // window that is still loading rather than a bug.
            assertEquals(List.of(), labels(row(new Breadcrumbs())));
        }
    }

    @Nested
    @DisplayName("which crumb is current")
    class Current {

        @Test
        @DisplayName("the last one is, and the trail is what says so")
        void theLastCrumbIsCurrent() {
            var row = row(trail(3));

            assertFalse(((Crumb) row.get(0)).current());
            assertFalse(((Crumb) row.get(2)).current());
            assertTrue(((Crumb) row.get(4)).current());
        }

        @Test
        @DisplayName("a document cannot mark one itself")
        void itIsNotAnAttribute() {
            var written = (Crumb) Widgets.inflater()
                    .inflateAll(KdlParser.parse("crumb \"Home\""))
                    .getFirst();

            // Nothing in markup sets it, which is what keeps the invariant the
            // trail exists to hold.
            assertFalse(written.current());
        }

        @Test
        @DisplayName("the current crumb is not a link, whatever `press` it was written with")
        void theCurrentCrumbIsInert() {
            var trail =
                    new Breadcrumbs(new Crumb("Home", () -> log.add("home")), new Crumb("Here", () -> log.add("here")));
            var here = (Crumb) row(trail).getLast();

            here.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, new ElementTree(here).root()));
            here.onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, new ElementTree(here).root()));

            assertEquals(List.of(), log, "§6: the last crumb is the current page and is not a link");
            assertFalse(here.isFocusable(), "and it is not a Tab stop either");
        }

        @Test
        @DisplayName("an earlier crumb still goes where it says")
        void anEarlierCrumbFollows() {
            var trail = new Breadcrumbs(new Crumb("Home", () -> log.add("home")), new Crumb("Here"));
            var home = (Crumb) row(trail).getFirst();

            home.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, new ElementTree(home).root()));

            assertEquals(List.of("home"), log);
            assertTrue(home.isFocusable());
        }

        @Test
        @DisplayName("a crumb with nowhere to go is not a Tab stop")
        void aWordIsNotAControl() {
            assertFalse(new Crumb("Library").isFocusable());
        }

        @Test
        @DisplayName("the last *crumb* is current, not the last child")
        void trailingSceneryIsNotThePath() {
            // A trail may hold a badge or a spacer after its path, and "where you
            // are" is the last step of the path rather than whatever was written
            // last.
            var trail = new Breadcrumbs(
                    new Crumb("Home", () -> {}),
                    new Crumb("Here", () -> {}),
                    new io.github.digitalsmile.goldberry.widgets.controls.badge.Badge("2"));

            var crumbs = row(trail).stream()
                    .filter(Crumb.class::isInstance)
                    .map(Crumb.class::cast)
                    .toList();

            assertFalse(crumbs.get(0).current());
            assertTrue(crumbs.get(1).current());
        }
    }

    @Nested
    @DisplayName("overflow")
    class Overflow {

        @Test
        @DisplayName("a trail at the limit shows every crumb")
        void fourFits() {
            assertEquals(List.of("step-0", ">", "step-1", ">", "step-2", ">", "step-3"), labels(row(trail(4))));
        }

        @Test
        @DisplayName("past it, the middle becomes a single `…`")
        void fiveCollapses() {
            // First, `…`, then the last two: four things on the row, counting the
            // `…` as one, which is what `collapseAfter` means.
            assertEquals(List.of("step-0", ">", "…", ">", "step-3", ">", "step-4"), labels(row(trail(5))));
        }

        @Test
        @DisplayName("a much deeper path still shows exactly four things")
        void theRowDoesNotGrowWithThePath() {
            assertEquals(List.of("step-0", ">", "…", ">", "step-18", ">", "step-19"), labels(row(trail(20))));
        }

        @Test
        @DisplayName("nothing is elided inside a label")
        void labelsAreNeverTruncated() {
            // §6: "a truncated folder name is worse than a hidden one, because it
            // looks like a name".
            for (var label : labels(row(trail(20)))) {
                assertFalse(
                        label.startsWith("step-") && label.endsWith("…"),
                        () -> "a crumb's own label was shortened: " + label);
            }
        }

        @Test
        @DisplayName("`collapseAfter` of zero or less never collapses")
        void collapsingIsOptional() {
            var trail = trail(6).collapseAfter(0);

            assertEquals(6, row(trail).stream().filter(Crumb.class::isInstance).count());
        }

        @Test
        @DisplayName("a collapseAfter below three is raised rather than refused")
        void tooSmallIsRaised() {
            // A row that cannot contain where you are is not an answer, and the
            // number is a hint about width rather than a contract.
            var trail = trail(6).collapseAfter(1);

            assertEquals(List.of("step-0", ">", "…", ">", "step-5"), labels(row(trail)));
        }

        @Test
        @DisplayName("the arithmetic, asked directly")
        void theHiddenRange() {
            assertArrayRange(0, 0, BreadcrumbsState.hiddenRange(4, 4));
            assertArrayRange(1, 3, BreadcrumbsState.hiddenRange(5, 4));
            assertArrayRange(1, 18, BreadcrumbsState.hiddenRange(20, 4));
            assertArrayRange(0, 0, BreadcrumbsState.hiddenRange(20, 0));
        }

        private static void assertArrayRange(int from, int to, int[] actual) {
            assertEquals(List.of(from, to), List.of(actual[0], actual[1]));
        }

        @Test
        @DisplayName("the `…` is the one part in the catalog that takes the focus")
        void theOverflowIsReachable() {
            // The crumbs behind it are not in the tree, so there is no node for
            // the keyboard to reach and no key that could stand for one.
            var overflow = (CrumbOverflow) row(trail(20)).get(2);

            assertTrue(overflow.isFocusable());
            assertEquals(Role.MENU_BUTTON, overflow.role());
            assertFalse(overflow.accessibleName().equals("…"), "a name has to be something you can act on");
        }

        @Test
        @DisplayName("every other part stays out of the Tab order")
        void thePartsAreNotFocusable() {
            for (var part : row(trail(20))) {
                if (part instanceof CrumbOverflow) {
                    continue;
                }
                var focusable = part instanceof Handles handles && handles.isFocusable();
                assertEquals(
                        part instanceof Crumb crumb && !crumb.current(),
                        focusable,
                        () -> part.getClass().getSimpleName() + " disagrees about being a Tab stop");
            }
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a document writes what §6 spells")
        void inflates() {
            var actions = ActionRegistry.strict().bind("go-home", () -> log.add("home"));

            var trail = (Breadcrumbs)
                    Widgets.inflater(actions).inflateAll(KdlParser.parse("""
                            breadcrumbs id="path" {
                                crumb press="go-home" "Home"
                                crumb "The Red Book"
                            }
                            """)).getFirst();

            assertEquals("path", trail.attributes().id());
            assertEquals(2, trail.rawCrumbs().size());
            assertEquals(List.of("Home", ">", "The Red Book"), labels(row(trail)));
        }

        @Test
        @DisplayName("`collapse-after` is a document's to set")
        void collapseAfterInflates() {
            var trail = (Breadcrumbs) Widgets.inflater()
                    .inflateAll(KdlParser.parse("breadcrumbs collapse-after=3 { crumb \"a\"; crumb \"b\" }"))
                    .getFirst();

            assertEquals(3, trail.collapseAfter());
        }

        @Test
        @DisplayName("a crumb needs a word in it")
        void anEmptyCrumbIsRefused() {
            assertThrows(IllegalArgumentException.class, () -> new Crumb(""));
        }

        @Test
        @DisplayName("the catalog registers the trail and its crumb, and not the parts")
        void theRegistryListsTheWidgetsAlone() {
            var registered = Widgets.inflater().registered();

            assertTrue(registered.contains("breadcrumbs"));
            assertTrue(registered.contains("crumb"));
            assertFalse(registered.contains("crumb-separator"));
            assertFalse(registered.contains("crumb-overflow"));
        }

        @Test
        @DisplayName("the trail's id lands on the node that is painted")
        void theIdReachesTheStyledNode() {
            // An id on a node nothing paints would be an anchor `Host.anchor`
            // never finds.
            assertEquals(
                    "path",
                    trailNode(new Breadcrumbs(new Crumb("Home")).id("path")).id());
        }
    }
}
