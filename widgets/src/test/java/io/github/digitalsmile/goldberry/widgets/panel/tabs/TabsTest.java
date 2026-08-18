package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `tabs` — the strip, the lazy panel, and the three things a tab can be given
/// that a stylesheet cannot supply: a label, an icon and a colour.
class TabsTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static WidgetRenderer renderer() {
        return new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, "")),
                TestFont.get());
    }

    private static Box render(Widget widget) {
        return renderer().render(new ElementTree(widget));
    }

    private static List<String> textIn(Box box) {
        var found = new ArrayList<String>();
        collect(box, found);
        return found;
    }

    private static void collect(Box box, List<String> into) {
        if (box.text() != null) {
            into.add(box.text().paragraph().text());
        }
        box.children().forEach(child -> collect(child, into));
    }

    private static Tabs strip(String selected) {
        return new Tabs(selected,
                new Tab("a", "First", new Text("content of A")),
                new Tab("b", "Second", new Text("content of B")));
    }

    /// §5's "lazy content instantiation", and it is lazy by omission: an
    /// unselected tab's content is never put in the tree at all.
    @Test
    @DisplayName("only the selected tab's content is built")
    void lazyContent() {
        assertEquals(List.of("First", "Second", "content of A"), textIn(render(strip("a"))));
        assertEquals(List.of("First", "Second", "content of B"), textIn(render(strip("b"))));
    }

    @Test
    @DisplayName("a strip is a list and a panel, in that order")
    void anatomy() {
        var children = strip("a").children();

        assertEquals(2, children.size());
        assertEquals("tab-list", ((Styled) children.get(0)).cssType());
        assertEquals("tab-panel", ((Styled) children.get(1)).cssType());
    }

    /// The selected tab is marked with `:checked`, like every other exactly-one
    /// set in this catalog.
    @Test
    @DisplayName("the selected tab is the checked one, and only it")
    void selection() {
        var headers = ((TabList) strip("b").children().getFirst()).headers();

        assertFalse(((Styled) headers.get(0)).isChecked());
        assertTrue(((Styled) headers.get(1)).isChecked());
    }

    /// Read through `bind`, like every other value: the strip shows what the
    /// property holds now.
    @Test
    @DisplayName("a bound strip follows its property")
    void bound() {
        var property = Property.of("a");
        var tabs = strip("a").bound(property);

        assertEquals("a", tabs.selected());
        property.set("b");
        assertEquals("b", tabs.selected());
    }

    /// It reports and does not decide: a strip whose handler does nothing does
    /// not move, which is the visible form of "the model did not change".
    @Test
    @DisplayName("picking a tab asks, and changes nothing by itself")
    void picking() {
        var asked = new ArrayList<String>();
        var tabs = strip("a").onChange(asked::add);
        var headers = ((TabList) tabs.children().getFirst()).headers();

        ((Tab) headers.get(1)).onSelect().run();

        assertEquals(List.of("b"), asked);
        assertEquals("a", tabs.selected(), "the strip did not select it — the application does");
    }

    /// Removing a tab is the same shape: the × asks, the application shortens its
    /// own list.
    @Test
    @DisplayName("closing a tab asks, and removes nothing by itself")
    void closing() {
        var asked = new ArrayList<String>();
        var tabs = new Tabs("a",
                new Tab("a", "First").closable(true),
                new Tab("b", "Second"))
                .onClose(asked::add);
        var headers = ((TabList) tabs.children().getFirst()).headers();

        // Two parts: the underline every tab has, and the × only a closable one
        // does.
        var closable = (Tab) headers.get(0);
        assertEquals(2, closable.children().size(), "a closable tab has a × as well as a rule");
        assertEquals(1, ((Tab) headers.get(1)).children().size(), "and one that is not, has not");
        assertInstanceOf(TabClose.class, closable.children().get(1));

        closable.onClose().run();
        assertEquals(List.of("a"), asked);
        assertEquals(2, tabs.children().size());
    }

    /// And adding: the `+` is a part that only appears when somebody is listening
    /// for it.
    @Test
    @DisplayName("the add affordance appears only when a strip can gain tabs")
    void adding() {
        var added = new int[1];
        var without = ((TabList) strip("a").children().getFirst()).headers();
        var with = ((TabList) strip("a").onNew(() -> added[0]++).children().getFirst()).headers();

        assertEquals(2, without.size());
        assertEquals(3, with.size());
        assertEquals("tab-new", ((Styled) with.get(2)).cssType());

        ((TabNew) with.get(2)).onNew().run();
        assertEquals(1, added[0]);
    }

    /// A colour a stylesheet cannot know: application data, written through
    /// `restyle` so the stylesheet still decides what it *means*.
    @Test
    @DisplayName("a tab's own colour reaches its style, and no colour leaves the theme's")
    void colour() {
        var red = 0xFFBF616A;
        var coloured = new Tab("a", "First").colour(red);
        var plain = new Tab("b", "Second");

        var box = render(new Tabs("a", coloured, plain));
        // Child 0 of the list is the rule, so the headers start at 1.
        var headers = box.children().getFirst().children();

        assertEquals(red, labelColour(headers.get(1)),
                "the label is drawn in the tab's own colour");
        // The second tab is muted by `controls.css` and is emphatically not red.
        assertFalse(labelColour(headers.get(2)) == red);
    }

    /// The colour of the first text under `box` — a tab's label is a child box,
    /// because a box with text is a measured leaf and could hold no icon beside
    /// it.
    private static int labelColour(Box box) {
        if (box.text() != null) {
            return box.text().argb();
        }
        for (var child : box.children()) {
            var found = labelColour(child);
            if (found != 0) {
                return found;
            }
        }
        return 0;
    }

    @Test
    @DisplayName("a strip is one Tab stop with a horizontal roving selection")
    void keyboard() {
        assertEquals(FocusScope.HORIZONTAL, strip("a").focusScope());
        assertTrue(new Tab("a", "First").isFocusable());
        assertFalse(new TabClose(() -> { }).isFocusable(),
                "a closable tab would otherwise be two stops, and nine tabs nineteen");
        assertTrue(new TabNew(() -> { }).isFocusable());
    }

    @Test
    @DisplayName("a document writes a strip, its tabs, their icons and their colours")
    void fromKdl() {
        var widget = Controls.inflater().inflate(KdlParser.parse("""
                tabs id="views" value="editor" {
                    tab value="editor" "Editor" {
                        text "the editor"
                    }
                    tab value="log" colour="#bf616a" closable=#true "Log"
                }
                """).getFirst());

        var tabs = assertInstanceOf(Tabs.class, widget);
        assertEquals("views", tabs.id());
        assertEquals("editor", tabs.selected());

        var log = (Tab) tabs.rawTabs().get(1);
        assertEquals(0xFFBF616A, log.colour(), "written the way a stylesheet writes a colour");
        assertTrue(log.closable());
        assertEquals(List.of("the editor"), textIn(render(tabs)).subList(2, 3));
    }

    @Test
    @DisplayName("a tab needs a value, for a radio's reason")
    void refusesATabWithNoValue() {
        var refused = assertThrows(IllegalArgumentException.class,
                () -> Controls.inflater().inflate(KdlParser.parse("tabs { tab \"Log\" }")
                        .getFirst()));

        assertTrue(refused.getMessage().contains("value"), refused.getMessage());
    }
}
