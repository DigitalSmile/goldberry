package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.Shortcut;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `menubar` — §8's in-window bar, and the accelerator registration that was
/// waiting on it ([ADR-0163]).
///
/// Two claims are worth more than the rest and both are about what happens when
/// **no menu is on screen**: an accelerator fires, and it stops firing when the
/// bar goes away. Everything ADR-0106 deferred was deferred because a popup does
/// not live long enough, so a test that opened a menu first would be testing the
/// thing that already worked.
class MenuBarTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Attributes id(String value) {
        return Attributes.NONE.id(value);
    }

    /// Every widget in the described tree, depth first.
    private static List<Widget> described(Element element) {
        var found = new ArrayList<Widget>();
        collect(element, found);
        return found;
    }

    private static void collect(Element element, List<Widget> into) {
        into.add(element.widget());
        for (var child : element.children()) {
            collect(child, into);
        }
    }

    private static MenuBarRow row(ElementTree tree) {
        return described(tree.root()).stream()
                .filter(MenuBarRow.class::isInstance)
                .map(MenuBarRow.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the bar described no menubar node"));
    }

    private static List<MenuTitle> titles(ElementTree tree) {
        return described(tree.root()).stream()
                .filter(MenuTitle.class::isInstance)
                .map(MenuTitle.class::cast)
                .toList();
    }

    @Nested
    @DisplayName("what it describes")
    class Description {

        @Test
        @DisplayName("a bar of items describes a menubar of headings")
        void headings() {
            var tree = new ElementTree(new MenuBar(
                    new Item("File").submenu(new Item("Open…", () -> { })),
                    new Item("Edit").submenu(new Item("Undo", () -> { }))));

            assertEquals(List.of("File", "Edit"),
                    titles(tree).stream().map(MenuTitle::label).toList());
        }

        /// The node a stylesheet sees is the row, not the stateful widget — the
        /// split `select` and `tabs` already use.
        @Test
        @DisplayName("the id and classes the document wrote land on the menubar node")
        void attributesLandOnTheRow() {
            var tree = new ElementTree(new MenuBar(
                    List.of(new Item("File").submenu(new Item("Open…", () -> { }))),
                    id("main-bar").classes("compact")));

            assertEquals("main-bar", row(tree).id());
            assertTrue(row(tree).classes().contains("compact"));
        }

        /// A bar is the horizontal scope a menu is not, and that is the whole of
        /// why `Left` and `Right` walk it (ADR-0078).
        @Test
        @DisplayName("a bar is a horizontal focus scope where a menu is a vertical one")
        void horizontal() {
            var tree = new ElementTree(new MenuBar(
                    new Item("File").submenu(new Item("Open…", () -> { }))));

            assertEquals(FocusScope.HORIZONTAL, row(tree).focusScope());
            assertEquals(FocusScope.VERTICAL, new Menu().focusScope());
        }

        /// Every heading needs one, because a menu is anchored to the heading
        /// that opened it and an anchor is looked up by id.
        @Test
        @DisplayName("a heading with no id of its own is given one")
        void generatedIds() {
            var tree = new ElementTree(new MenuBar(
                    new Item("File").submenu(new Item("Open…", () -> { })),
                    new Item("Edit").submenu(new Item("Undo", () -> { }))));

            var ids = titles(tree).stream().map(MenuTitle::id).toList();
            assertEquals(2, ids.size());
            assertEquals(2, Set.copyOf(ids).size(), "two headings, two distinct ids: " + ids);
        }

        @Test
        @DisplayName("a heading the document named keeps its own id")
        void authorsIdWins() {
            var tree = new ElementTree(new MenuBar(
                    new Item("File").submenu(new Item("Open…", () -> { }))
                            .withAttributes(id("file"))));

            assertEquals("file", titles(tree).getFirst().id());
        }

        /// A heading has none of the three things that make a menu row a menu
        /// row, and asserting it is how a copy-paste from [Item] gets caught.
        @Test
        @DisplayName("a heading has no tick column, accelerator or chevron")
        void headingsAreNotRows() {
            var tree = new ElementTree(new MenuBar(
                    new Item("File").submenu(new Item("Open…", () -> { }))));

            assertTrue(described(tree.root()).stream().noneMatch(ItemLead.class::isInstance));
            assertTrue(described(tree.root()).stream().noneMatch(ItemChevron.class::isInstance));
        }
    }

    @Nested
    @DisplayName("opening a menu, which is what needs a window")
    class Opening {

        private final TestHost host = new TestHost()
                .anchoring("menubar-title-0", 0, 0, 40, 28)
                .anchoring("menubar-title-1", 40, 0, 40, 28);

        private MenuBar bar() {
            return new MenuBar(
                    new Item("File").submenu(new Item("Open…", () -> { })),
                    new Item("Edit").submenu(new Item("Undo", () -> { })));
        }

        private ElementTree tree() {
            return new ElementTree(bar(), host);
        }

        private void click(MenuTitle title) {
            title.onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                    PointerEvent.Button.PRIMARY, 1, null));
        }

        private void hover(MenuTitle title) {
            title.onPointer(new PointerEvent(PointerEvent.Kind.ENTERED, 0, 0,
                    PointerEvent.Button.PRIMARY, 0, null));
        }

        @Test
        @DisplayName("a click asks the window for a menu under its heading")
        void clickOpens() {
            var tree = tree();
            click(titles(tree).getFirst());

            assertEquals(1, host.opened.size());
            var opened = host.opened.getFirst();
            assertEquals(0, opened.anchor().left(), "under the first heading");
            assertEquals(28, opened.anchor().top() + opened.anchor().size().height(),
                    "hanging from the bottom of the bar");
        }

        /// The one placement no menu bar anywhere uses is a menu centred under
        /// its heading, so the alignment is asserted rather than left to a
        /// default that might change.
        @Test
        @DisplayName("the menu hangs from the heading's left edge, touching the bar")
        void placement() {
            var tree = tree();
            click(titles(tree).getFirst());

            var placement = host.opened.getFirst().placement();
            assertEquals(io.github.digitalsmile.goldberry.Placement.Side.BOTTOM,
                    placement.side());
            assertEquals(io.github.digitalsmile.goldberry.Placement.Align.START,
                    placement.align());
            assertEquals(0, placement.gap(), "a bar's menu touches the bar");
        }

        /// With nothing showing, crossing the bar on the way somewhere else must
        /// not drop a menu on the screen.
        @Test
        @DisplayName("hovering a heading with nothing open does nothing")
        void hoverWithNothingOpenDoesNothing() {
            var tree = tree();
            hover(titles(tree).getFirst());

            assertTrue(host.opened.isEmpty());
        }

        /// The other half of the same rule, and the one that makes a bar feel
        /// like a bar. Against a host that opens nothing this cannot be
        /// asserted — `switchTo` needs something to *be* open — so it is stated
        /// here and left to [MenusTest]'s real window.
        @Test
        @DisplayName("a heading with no submenu opens nothing")
        void headingWithNoMenu() {
            var tree = new ElementTree(new MenuBar(new Item("Help")), host);
            click(titles(tree).getFirst());

            assertTrue(host.opened.isEmpty(),
                    "there is nothing to show, so nothing is asked for");
        }

        @Test
        @DisplayName("a disabled heading opens nothing")
        void disabledHeading() {
            var tree = new ElementTree(new MenuBar(
                    new Item("File").submenu(new Item("Open…", () -> { })).disabled(true)),
                    host);
            click(titles(tree).getFirst());

            assertTrue(host.opened.isEmpty());
            assertTrue(titles(tree).getFirst().isDisabled());
        }

        /// `Down` is the arrow a heading spends on opening, where an [Item]
        /// spends `Right` on its submenu. It is free precisely because the bar's
        /// scope is horizontal.
        @Test
        @DisplayName("Down opens the heading's menu, and Right is left to the scope")
        void downOpens() {
            var tree = tree();
            var title = titles(tree).getFirst();

            title.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE,
                    false, null));
            assertTrue(host.opened.isEmpty(), "Right is traversal here, not activation");

            title.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.DOWN, Modifiers.NONE,
                    false, null));
            assertEquals(1, host.opened.size());
        }
    }

    @Nested
    @DisplayName("the accelerators, which is what ADR-0106 deferred")
    class Registration {

        private final TestHost host = new TestHost();

        /// **The claim.** No menu has been opened, nothing is on screen, and
        /// `Ctrl+O` runs the command a menu three levels down names.
        @Test
        @DisplayName("an accelerator fires with the menu shut, which is the whole point")
        void firesWithNothingOnScreen() {
            var opened = new AtomicInteger();
            new ElementTree(new MenuBar(
                    new Item("File").submenu(
                            new Item("Open…", opened::incrementAndGet).accelerator("Ctrl+O"))),
                    host);

            assertTrue(host.press("Ctrl+O"), "Ctrl+O should be bound");
            assertEquals(1, opened.get());
        }

        /// A submenu is a description like any other, so its commands are as
        /// registrable as the top level's — which is the difference between
        /// walking a value and walking a window.
        @Test
        @DisplayName("an accelerator inside a submenu is registered too")
        void nested() {
            var chosen = new AtomicInteger();
            new ElementTree(new MenuBar(
                    new Item("File").submenu(
                            new Item("Recent").submenu(
                                    new Item("notes.txt", chosen::incrementAndGet)
                                            .accelerator("Ctrl+Shift+R")))),
                    host);

            assertTrue(host.press("Ctrl+Shift+R"));
            assertEquals(1, chosen.get());
        }

        /// The other half, and the leak if it is missing: an accelerator is an
        /// entry in a map that outlives the tree.
        @Test
        @DisplayName("unmounting the bar gives the accelerators back")
        void unmountUnbinds() {
            var tree = new ElementTree(new MenuBar(
                    new Item("File").submenu(
                            new Item("Open…", () -> { }).accelerator("Ctrl+O"))),
                    host);

            assertTrue(host.shortcuts().containsKey(Shortcut.of("Ctrl+O")));
            tree.unmount();
            assertFalse(host.shortcuts().containsKey(Shortcut.of("Ctrl+O")),
                    "a bar that has gone away must not still own Ctrl+O");
        }

        /// A greyed row that still fires on its key is worse than no accelerator
        /// at all: the command is unavailable and the keyboard says otherwise.
        @Test
        @DisplayName("a disabled item's accelerator is displayed and not bound")
        void disabledIsNotBound() {
            var ran = new AtomicInteger();
            new ElementTree(new MenuBar(
                    new Item("File").submenu(
                            new Item("Open…", ran::incrementAndGet)
                                    .accelerator("Ctrl+O").disabled(true))),
                    host);

            assertFalse(host.press("Ctrl+O"));
            assertEquals(0, ran.get());
        }

        /// A row with a submenu leads somewhere rather than doing something, so
        /// there is nothing for a key to run.
        @Test
        @DisplayName("a heading's own accelerator is not bound, because it has no command")
        void headingsAreNotBound() {
            new ElementTree(new MenuBar(
                    new Item("File").accelerator("Ctrl+F")
                            .submenu(new Item("Open…", () -> { }))),
                    host);

            assertFalse(host.press("Ctrl+F"));
        }

        /// A typo in a stylesheet does not take the window down, and a typo in an
        /// accelerator must not either — it is already drawn beside the row where
        /// somebody can see it.
        @Test
        @DisplayName("an accelerator that does not parse is skipped, not thrown")
        void unparseableIsSkipped() {
            var tree = new ElementTree(new MenuBar(
                    new Item("File").submenu(
                            new Item("Open…", () -> { }).accelerator("Ctrl+Zork"),
                            new Item("Save", () -> { }).accelerator("Ctrl+S"))),
                    host);

            assertTrue(host.shortcuts().containsKey(Shortcut.of("Ctrl+S")),
                    "the good one beside it is still bound");
            assertEquals(2, titles(tree).size() + 1, "and the bar still built");
        }

        /// §8's "`Alt`-style keyboard activation", as far as a `Shortcut` on a
        /// non-modifier key can express it.
        @Test
        @DisplayName("F10 opens the first heading")
        void f10() {
            var host = new TestHost().anchoring("menubar-title-0", 0, 0, 40, 28);
            new ElementTree(new MenuBar(
                    new Item("File").submenu(new Item("Open…", () -> { }))), host);

            assertTrue(host.press("F10"));
            assertEquals(1, host.opened.size());
        }
    }

    @Nested
    @DisplayName("the walk on its own")
    class Walking {

        @Test
        @DisplayName("every command with an accelerator is found, in document order")
        void order() {
            var bindings = Accelerators.in(List.of(
                    new Item("File").submenu(
                            new Item("Open…", () -> { }).accelerator("Ctrl+O"),
                            new Separator(),
                            new Item("Recent").submenu(
                                    new Item("notes.txt", () -> { }).accelerator("Ctrl+1"))),
                    new Item("Edit").submenu(
                            new Item("Undo", () -> { }).accelerator("Ctrl+Z"))));

            assertEquals(List.of("Open…", "notes.txt", "Undo"),
                    bindings.stream().map(b -> b.label()).toList());
            assertEquals(Shortcut.of("Ctrl+O"), bindings.getFirst().shortcut());
        }

        /// Neither a separator nor anything else that is not an item has an
        /// accelerator, and walking one must not be a class cast.
        @Test
        @DisplayName("anything that is not an item is walked past")
        void nonItems() {
            assertTrue(Accelerators.in(List.of(new Separator(), new Menu())).isEmpty());
        }
    }

    @Nested
    @DisplayName("from markup")
    class Markup {

        /// There is no new syntax: a nested `item` is a submenu, and a `menubar`
        /// of them is a bar. That is the whole of what ADR-0163 buys an author.
        @Test
        @DisplayName("a menubar of nested items inflates into headings")
        void inflates() {
            var document = KdlParser.parse("""
                    menubar id="bar" {
                        item "File" {
                            item accelerator="Ctrl+O" "Open…"
                        }
                        item "Edit" {
                            item accelerator="Ctrl+Z" "Undo"
                        }
                    }
                    """);
            var widget = Widgets.inflater().inflate(document.getFirst());
            var bar = assertInstanceOf(MenuBar.class, widget);

            assertEquals("bar", bar.attributes().id());
            var tree = new ElementTree(bar);
            assertEquals(List.of("File", "Edit"),
                    titles(tree).stream().map(MenuTitle::label).toList());
        }
    }
}
